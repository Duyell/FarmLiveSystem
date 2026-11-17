package com.farmlive.service;

import com.farmlive.auth.CurrentUser;
import com.farmlive.common.BusinessException;
import com.farmlive.common.AiClient;
import com.farmlive.dto.Dtos;
import com.farmlive.repo.KnowledgeRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 知识库管理：文档上传 → 解析 → 分块 → 向量化入库；
 * 支持启停、删除、重解析；启动时预置常见问题种子文档。
 */
@Service
public class KnowledgeService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
    private static final Set<String> ALLOWED_TYPES = Set.of("txt", "md", "pdf", "docx");
    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 80;
    private static final int EMBED_BATCH = 16;

    private final KnowledgeRepository repository;
    private final AiClient aiClient;
    private final AuditService auditService;

    public KnowledgeService(KnowledgeRepository repository, AiClient aiClient, AuditService auditService) {
        this.repository = repository;
        this.aiClient = aiClient;
        this.auditService = auditService;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedKnowledge();
    }

    /**
     * 首次启动预置《助农直播常见问题》，让问答开箱可用；无 Key 时标记 failed 可重解析。
     */
    private void seedKnowledge() {
        Long id = null;
        try {
            if (repository.docExistsByTitle("助农直播常见问题")) {
                return;
            }
            id = repository.insertDoc("助农直播常见问题", "助农直播常见问题.txt", "txt",
                    SEED_TEXT.getBytes(StandardCharsets.UTF_8).length, repository.findSystemAccountId());
            storeChunks(id, SEED_TEXT);
            log.info("知识库种子文档已入库（docId={}）", id);
        } catch (Exception ex) {
            if (id != null) {
                repository.updateParseStatus(id, "failed");
            }
            log.warn("种子知识入库失败（不影响启动，配置 AI Key 后可重新解析）: {}", ex.getMessage());
        }
    }

    public List<Dtos.KnowledgeDocView> list() {
        return repository.listDocs();
    }

    /**
     * 上传不做整体事务：文档元信息先落库，向量化失败时保留 failed 状态可重试。
     */
    public Dtos.KnowledgeDocView upload(CurrentUser user, MultipartFile file, String title) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请选择要上传的文档");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        String type = extension(originalName);
        if (!ALLOWED_TYPES.contains(type)) {
            throw BusinessException.badRequest("仅支持 txt / md / pdf / docx 文档");
        }
        String name = (title == null || title.isBlank()) ? baseName(originalName) : title.trim();
        if (name.length() > 120) {
            throw BusinessException.badRequest("文档标题过长");
        }
        String text;
        try {
            text = extractText(file.getBytes(), type);
        } catch (Exception ex) {
            throw BusinessException.badRequest("文档解析失败：" + ex.getMessage());
        }
        if (text == null || text.strip().length() < 20) {
            throw BusinessException.badRequest("文档内容过少，解析后不足 20 字");
        }
        Long id = repository.insertDoc(name, originalName, type, file.getSize(), user == null ? null : user.id());
        try {
            storeChunks(id, text);
        } catch (Exception ex) {
            repository.updateParseStatus(id, "failed");
            auditService.record(user, "KNOWLEDGE_UPLOAD", "上传文档「" + name + "」解析失败");
            throw BusinessException.badRequest("AI_DOC_PARSE_FAILED", "文档已保存但向量化失败：" + ex.getMessage()
                    + "，可在列表中重新解析");
        }
        auditService.record(user, "KNOWLEDGE_UPLOAD", "上传知识库文档「" + name + "」");
        return viewOf(id);
    }

    public Dtos.KnowledgeDocView reparse(CurrentUser user, Long id) {
        KnowledgeRepository.DocRow doc = repository.findDocRow(id)
                .orElseThrow(() -> BusinessException.notFound("文档不存在"));
        // 种子文档没有原始文件，重解析时按种子内容重建。
        String text = "助农直播常见问题".equals(doc.title()) ? SEED_TEXT : null;
        if (text == null) {
            throw BusinessException.badRequest("该文档没有保留原始内容，请删除后重新上传");
        }
        repository.deleteChunksByDocId(id);
        storeChunks(id, text);
        auditService.record(user, "KNOWLEDGE_REPARSE", "重新解析文档「" + doc.title() + "」");
        return viewOf(id);
    }

    @org.springframework.transaction.annotation.Transactional
    public Dtos.KnowledgeDocView toggle(CurrentUser user, Long id, boolean enabled) {
        repository.findDocRow(id).orElseThrow(() -> BusinessException.notFound("文档不存在"));
        repository.setEnabled(id, enabled);
        auditService.record(user, "KNOWLEDGE_TOGGLE",
                (enabled ? "启用" : "停用") + "知识库文档（id=" + id + "）");
        return viewOf(id);
    }

    @org.springframework.transaction.annotation.Transactional
    public void delete(CurrentUser user, Long id) {
        KnowledgeRepository.DocRow doc = repository.findDocRow(id)
                .orElseThrow(() -> BusinessException.notFound("文档不存在"));
        repository.deleteDoc(id);
        auditService.record(user, "KNOWLEDGE_DELETE", "删除知识库文档「" + doc.title() + "」");
    }

    // ---------------- 内部 ----------------

    private Dtos.KnowledgeDocView viewOf(Long id) {
        return repository.listDocs().stream()
                .filter(doc -> doc.id().equals(id))
                .findFirst()
                .orElseThrow(() -> BusinessException.notFound("文档不存在"));
    }

    private void storeChunks(Long docId, String text) {
        List<String> chunks = splitChunks(text);
        boolean vectorizable = aiClient.embedConfigured();
        for (int start = 0; start < chunks.size(); start += EMBED_BATCH) {
            List<String> batch = chunks.subList(start, Math.min(start + EMBED_BATCH, chunks.size()));
            List<double[]> vectors = vectorizable
                    ? aiClient.embed(batch)
                    : batch.stream().map(chunk -> (double[]) null).toList();
            for (int i = 0; i < batch.size(); i++) {
                repository.insertChunk(docId, start + i, batch.get(i), batch.get(i).length(), vectors.get(i));
            }
        }
        repository.updateParseStatus(docId, "parsed");
    }

    /**
     * 定长分块，块间重叠 80 字，尽量在标点处断开。
     */
    private List<String> splitChunks(String text) {
        String normalized = text.strip().replaceAll("[\\t\\r\\n]+", "\n");
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + CHUNK_SIZE, normalized.length());
            if (end < normalized.length()) {
                int breakAt = lastPunctuation(normalized, start + CHUNK_OVERLAP, end);
                if (breakAt > start + 100) {
                    end = breakAt;
                }
            }
            chunks.add(normalized.substring(start, end).strip());
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
        return chunks.stream().filter(chunk -> !chunk.isBlank()).toList();
    }

    private int lastPunctuation(String text, int from, int to) {
        for (int i = to - 1; i >= from; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n' || c == '.' || c == '!' || c == '?') {
                return i + 1;
            }
        }
        return -1;
    }

    private String extractText(byte[] bytes, String type) throws Exception {
        return switch (type) {
            case "txt", "md" -> new String(bytes, StandardCharsets.UTF_8);
            case "pdf" -> {
                try (PDDocument document = PDDocument.load(bytes)) {
                    yield new PDFTextStripper().getText(document);
                }
            }
            case "docx" -> {
                try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
                     XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                    yield extractor.getText();
                }
            }
            default -> throw new IllegalArgumentException("不支持的类型");
        };
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String baseName(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static final String SEED_TEXT = """
            助农直播常见问题（系统预置）

            一、直播预约规则
            1. 谁可以提交直播预约？只有农户身份的账号可以提交直播预约，直播管理员和系统管理员不能代提。
            2. 预约时段有什么限制？直播时段按整点划分，单场时长 1 到 4 小时，不能跨天，最早提前 2 小时提交，最多提前 30 天预约。
            3. 同一场地同一时段可以预约几场直播？只允许一场，提交时系统自动校验冲突，审核通过后还会按小时锁资源兜底。
            4. 预约被驳回怎么办？审核驳回时会附上驳回原因和推荐预约时间，可以按推荐时间重新提交。
            5. 取消预约有什么限制？直播开始前 24 小时内不能再取消，取消时需要填写原因，例如农产品临时缺货。

            二、排队与候补规则
            1. 什么时候需要排队？目标时段已被其他农户预约占用时，可以加入该场地该时段的排队队列；时段空闲时直接预约即可，不需要排队。
            2. 排队号是什么格式？排队号格式为 Q-农户编号-日期-序号，例如 Q-NF2026001-20261101-001。
            3. 排队顺位怎么计算？严格按加入时间排序，前序农户取消、放弃或超时后，后面的人顺位自动前移。
            4. 候补确认有什么规则？队首会在场地释放时升级为候补，候补需在开播前 1 小时截止时间前确认，超时自动顺延给下一位。
            5. 候补确认后又取消会怎样？确认候补生成预约后又取消的农户，72 小时内不能再加入该场地的排队队列。
            6. 排队队列会被关闭吗？直播管理员可以关闭整个场地时段的队列，关闭后队列内所有有效排队终止。

            三、通知规则
            1. 系统会发哪些通知？预约审核结果、排队顺位变化、候补确认提醒、直播开始前 1 小时提醒、设备故障预警。
            2. 通知渠道有哪些？系统内消息和短信两个渠道，设备故障等重要事件优先短信提醒。
            3. 短信发送失败怎么办？系统会按 1、5、15 分钟的间隔自动重试三次，最终失败时站内消息仍然保留，管理员也可以手动重发。

            四、设备与场地
            1. 设备编号是什么格式？设备编号格式为 ZB-场地编号-设备序号，例如 ZB-C001-01。
            2. 设备故障对预约有什么影响？设备报修后，系统会自动取消受影响的后续已通过预约并通知相关农户，管理员会尽快协调替代资源。
            3. 场地什么时候可以预约？场地状态为可用且在开放时段内才可以预约，可以按县域筛选查询空闲场地和设备。
            """;
}
