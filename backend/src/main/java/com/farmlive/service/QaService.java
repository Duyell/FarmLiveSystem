package com.farmlive.service;

import com.farmlive.auth.CurrentUser;
import com.farmlive.common.AiClient;
import com.farmlive.common.BusinessException;
import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import com.farmlive.repo.KnowledgeRepository;
import com.farmlive.repo.KnowledgeRepository.ChunkRow;
import com.farmlive.repo.QaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 问答业务：问题向量化 → 余弦相似度检索 → 低于阈值拒答 →
 * 带引用上下文生成回答 → 记录问答。
 * AI 只解答咨询，不直接提交或修改直播预约。
 */
@Service
public class QaService {
    private static final Logger log = LoggerFactory.getLogger(QaService.class);

    private static final String SYSTEM_PROMPT = """
            你是助农直播管理系统的问答助手，服务对象是农户和管理员。
            只依据下方提供的知识库内容回答问题；知识库没有的内容要如实说明不知道，禁止编造。
            不得代替用户提交、修改或取消直播预约，遇到操作类请求请引导用户到对应页面操作。
            回答使用简体中文，简洁分点，末尾单独一行列出引用来源编号，格式：引用来源：[1][2]。
            """;

    /**
     * 能力/问候类问题（如“你能做什么”“你是谁”“你好”）不依赖知识库检索，
     * 直接由 LLM 基于本提示回答，避免被检索 0 命中误判为拒答。
     */
    private static final String CAPABILITY_PROMPT = """
            你是助农直播管理系统的问答助手，服务对象是农户和管理员。
            你的能力范围（只做咨询解答，不代替用户操作）：
            - 解答直播预约规则：预约时段（整点、1~4 小时、不跨天）、提前 2~30 天、农户身份才能提交、同场地同时段冲突校验。
            - 解答预约审核与调整：直播管理员审核通过/驳回（驳回注明原因与推荐时间）、开播前 12 小时可申请调整需审批、取消需填原因。
            - 解答排队候补：资源被占自动排队（Q-农户ID-日期-序号）、按申请时间排序、前序取消顺位前移、候补确认后 3 天内不能再排队。
            - 解答通知：审核结果/顺位变化/候补确认/开播前 1 小时提醒/设备故障预警，系统消息+短信双渠道，失败自动重试。
            - 解答农产品溯源与质检资料、设备使用与校准周期、数据统计指标（占用率/场次/销量/成功率）。
            - 解答权限划分：系统管理员/直播管理员/农户主播/数据统计员各自职责。
            你不能做的事：不能代用户提交/修改/取消预约、不能查看农户完整手机号、不能导出数据。遇到操作类请求请引导用户到对应页面操作。
            回答使用简体中文，简洁分点，能力类问题末尾无需引用来源。
            """;

    private static final java.util.Set<String> CAPABILITY_TRIGGERS = java.util.Set.of(
            "你能做什么", "你可以做什么", "你会做什么", "你是什么", "你是谁", "你是干什么的",
            "你叫什么", "你叫什么名字", "介绍你自己", "自我介绍", "你的功能", "你有哪些功能",
            "你能帮我什么", "你能回答什么", "帮助", "help", "怎么用", "使用说明", "你能干什么",
            "你好", "您好", "在吗", "在不在", "hi", "hello", "谢谢", "感谢",
            "你能解答什么", "可以问什么", "能问什么问题"
    );

    /**
     * 能力/问候类问题判定：命中触发词，或问题不含业务实体词（预约/排队/通知/设备/场地/农户/溯源/统计等）。
     */
    private boolean isCapabilityQuestion(String question) {
        String normalized = question.toLowerCase().trim();
        if (normalized.isEmpty()) return false;
        for (String trigger : CAPABILITY_TRIGGERS) {
            if (normalized.contains(trigger)) return true;
        }
        // 短问题且不含任何业务关键词，视为闲聊/能力类
        if (normalized.length() <= 12) {
            String[] businessHints = {"预约", "排队", "候补", "通知", "设备", "场地", "农户", "溯源",
                    "统计", "审核", "调整", "取消", "校准", "资质", "密码", "登录", "农产品", "直播"};
            for (String hint : businessHints) {
                if (normalized.contains(hint)) return false;
            }
            return true;
        }
        return false;
    }

    private final QaRepository qaRepository;
    private final KnowledgeRepository knowledgeRepository;
    private final AiClient aiClient;

    @Value("${app.ai.top-k:4}")
    private int topK;
    @Value("${app.ai.score-threshold:0.35}")
    private double scoreThreshold;

    public QaService(QaRepository qaRepository, KnowledgeRepository knowledgeRepository, AiClient aiClient) {
        this.qaRepository = qaRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.aiClient = aiClient;
    }

    public Dtos.QaView ask(CurrentUser user, String question) {
        String trimmed = normalize(question);
        Prepared prepared = prepareAnswer(trimmed);
        if (prepared.refusal() != null) {
            Long id = qaRepository.insert(user.id(), trimmed, prepared.refusal(), List.of());
            return viewOf(id);
        }
        String answer = aiClient.chat(prepared.systemPrompt(), prepared.userPrompt());
        if (answer == null || answer.isBlank()) {
            answer = "AI 未返回有效回答，请稍后重试。";
        }
        Long id = qaRepository.insert(user.id(), trimmed, answer, prepared.citations());
        log.info("AI 问答完成（recordId={}，命中 {} 条引用，模式={}）",
                id, prepared.citations().size(), aiClient.embedConfigured() ? "向量" : "关键词");
        return viewOf(id);
    }

    /**
     * 流式问答：复用检索与提示词准备，逐片段回调，结束时写库并返回问答记录。
     */
    public Dtos.QaView streamAsk(CurrentUser user, String question, java.util.function.Consumer<String> onDelta) {
        String trimmed = normalize(question);
        Prepared prepared = prepareAnswer(trimmed);
        if (prepared.refusal() != null) {
            onDelta.accept(prepared.refusal());
            Long id = qaRepository.insert(user.id(), trimmed, prepared.refusal(), List.of());
            return viewOf(id);
        }
        String answer = aiClient.chatStream(prepared.systemPrompt(), prepared.userPrompt(), onDelta);
        if (answer == null || answer.isBlank()) {
            answer = "AI 未返回有效回答，请稍后重试。";
            onDelta.accept(answer);
        }
        Long id = qaRepository.insert(user.id(), trimmed, answer, prepared.citations());
        log.info("AI 流式问答完成（recordId={}，命中 {} 条引用，模式={}）",
                id, prepared.citations().size(), aiClient.embedConfigured() ? "向量" : "关键词");
        return viewOf(id);
    }

    private String normalize(String question) {
        String trimmed = question == null ? "" : question.strip();
        if (trimmed.length() < 2 || trimmed.length() > 500) {
            throw BusinessException.badRequest("问题长度需在 2 到 500 字之间");
        }
        return trimmed;
    }

    private record Prepared(String systemPrompt, String userPrompt, List<String> citations, String refusal) {
    }

    /**
     * 统一准备提示词：能力类直接答、无命中拒答、其余走知识库检索。
     */
    private Prepared prepareAnswer(String trimmed) {
        if (isCapabilityQuestion(trimmed)) {
            return new Prepared(CAPABILITY_PROMPT, "用户问题：" + trimmed, List.of(), null);
        }
        List<Hit> hits = aiClient.embedConfigured()
                ? retrieve(aiClient.embed(List.of(trimmed)).get(0))
                : keywordRetrieve(trimmed);
        if (hits.isEmpty()) {
            return new Prepared(null, null, List.of(),
                    "知识库中未收录与该问题相关的内容，暂无法给出可靠回答。"
                            + "建议换个问法，或联系管理员在知识库中补充相关资料。");
        }
        StringBuilder context = new StringBuilder();
        List<String> citations = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            Hit hit = hits.get(i);
            context.append("[").append(i + 1).append("]（来源：")
                    .append(hit.chunk.docTitle()).append("）\n")
                    .append(hit.chunk.content()).append("\n\n");
            citations.add(hit.chunk.docTitle());
        }
        return new Prepared(SYSTEM_PROMPT,
                "知识库内容：\n" + context + "\n用户问题：" + trimmed, citations, null);
    }

    public PageResult<Dtos.QaView> history(CurrentUser user, int page, int size) {
        return qaRepository.pageByAccount(user.id(), Math.max(1, page), Math.min(50, Math.max(1, size)));
    }

    // ---------------- 内部 ----------------

    private record Hit(ChunkRow chunk, double score) {
    }

    /**
     * 全量分块余弦相似度，取 top-K 且高于阈值（配置了向量接口时走此路径）。
     */
    private List<Hit> retrieve(double[] questionVector) {
        List<ChunkRow> chunks = knowledgeRepository.listEnabledChunks();
        List<Hit> hits = new ArrayList<>();
        for (ChunkRow chunk : chunks) {
            if (chunk.embedding() == null || chunk.embedding().length == 0) {
                continue;
            }
            double score = cosine(questionVector, chunk.embedding());
            if (score >= scoreThreshold) {
                hits.add(new Hit(chunk, score));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return hits.size() > topK ? hits.subList(0, topK) : hits;
    }

    /**
     * 关键词降级检索（未配置向量接口时）：问题切 2~4 字滑窗词元，
     * 按分块命中词元比例计分，高于阈值取 top-K。
     */
    private List<Hit> keywordRetrieve(String question) {
        List<String> grams = grams(question);
        if (grams.isEmpty()) {
            return List.of();
        }
        List<ChunkRow> chunks = knowledgeRepository.listEnabledChunks();
        List<Hit> hits = new ArrayList<>();
        for (ChunkRow chunk : chunks) {
            long matched = grams.stream().filter(chunk.content()::contains).count();
            double score = (double) matched / grams.size();
            if (score >= 0.12) {
                hits.add(new Hit(chunk, score));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return hits.size() > topK ? hits.subList(0, topK) : hits;
    }

    /**
     * 生成问题的 2~4 字滑窗词元（去重，最多 240 个）。
     */
    private List<String> grams(String question) {
        String normalized = question.replaceAll("[\\s，。！？；：,.!?;:\\-—（）()【】\\[\\]\"“”']", "");
        if (normalized.length() < 2) {
            return List.of();
        }
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        for (int len = 2; len <= 4; len++) {
            for (int i = 0; i + len <= normalized.length(); i++) {
                set.add(normalized.substring(i, i + len));
                if (set.size() >= 240) {
                    return new ArrayList<>(set);
                }
            }
        }
        return new ArrayList<>(set);
    }

    private double cosine(double[] a, double[] b) {
        if (a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private Dtos.QaView viewOf(Long id) {
        Dtos.QaView view = qaRepository.findById(id);
        if (view == null) {
            throw BusinessException.conflict("QA_RECORD_MISSING", "问答记录保存失败，请重试");
        }
        return view;
    }
}
