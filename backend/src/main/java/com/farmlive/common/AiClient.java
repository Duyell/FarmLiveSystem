package com.farmlive.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容接口客户端：对话 /embeddings 与 /chat/completions。
 * Key 通过环境变量或本地 application.yml 注入，不入仓库。
 */
@Component
public class AiClient {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper mapper;

    @Value("${app.ai.chat-base-url}")
    private String chatBaseUrl;
    @Value("${app.ai.chat-api-key:}")
    private String chatApiKey;
    @Value("${app.ai.chat-model}")
    private String chatModel;
    @Value("${app.ai.embed-base-url}")
    private String embedBaseUrl;
    @Value("${app.ai.embed-api-key:}")
    private String embedApiKey;
    @Value("${app.ai.embed-model}")
    private String embedModel;

    public AiClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public boolean chatConfigured() {
        return chatApiKey != null && !chatApiKey.isBlank();
    }

    public boolean embedConfigured() {
        return embedApiKey != null && !embedApiKey.isBlank();
    }

    /**
     * 批量向量化：POST {embed-base}/v1/embeddings，返回与输入等长的向量列表。
     */
    public List<double[]> embed(List<String> texts) {
        if (!embedConfigured()) {
            throw BusinessException.badRequest("AI_EMBED_NOT_CONFIGURED", "向量接口未配置，请设置 AI_EMBED_API_KEY");
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", embedModel);
            ArrayNode input = body.putArray("input");
            texts.forEach(input::add);
            JsonNode data = post(embedBaseUrl + "/v1/embeddings", embedApiKey, body);
            List<double[]> result = new ArrayList<>(texts.size());
            for (JsonNode item : data.path("data")) {
                JsonNode vector = item.path("embedding");
                double[] values = new double[vector.size()];
                for (int i = 0; i < vector.size(); i++) {
                    values[i] = vector.get(i).asDouble();
                }
                result.add(values);
            }
            if (result.size() != texts.size()) {
                throw BusinessException.badRequest("向量接口返回数量与输入不一致");
            }
            return result;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest("AI_EMBED_FAILED", "向量化失败：" + ex.getMessage());
        }
    }

    /**
     * 对话生成：POST {chat-base}/v1/chat/completions。
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (!chatConfigured()) {
            throw BusinessException.badRequest("AI_CHAT_NOT_CONFIGURED", "对话接口未配置，请设置 AI_CHAT_API_KEY");
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", chatModel);
            body.put("temperature", 0.3);
            ArrayNode messages = body.putArray("messages");
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", systemPrompt);
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", userPrompt);
            JsonNode data = post(chatBaseUrl + "/v1/chat/completions", chatApiKey, body);
            return data.path("choices").path(0).path("message").path("content").asText("");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest("AI_CHAT_FAILED", "AI 回答失败：" + ex.getMessage());
        }
    }

    /**
     * 流式对话：逐行解析 SSE data 块的 delta.content，回调片段并返回完整文本。
     */
    public String chatStream(String systemPrompt, String userPrompt, java.util.function.Consumer<String> onDelta) {
        if (!chatConfigured()) {
            throw BusinessException.badRequest("AI_CHAT_NOT_CONFIGURED", "对话接口未配置，请设置 AI_CHAT_API_KEY");
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", chatModel);
            body.put("temperature", 0.3);
            body.put("stream", true);
            ArrayNode messages = body.putArray("messages");
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", systemPrompt);
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", userPrompt);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chatBaseUrl + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + chatApiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            StringBuilder full = new StringBuilder();
            LineSubscriber subscriber = new LineSubscriber(full, onDelta);
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.fromLineSubscriber(subscriber));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("接口返回 " + response.statusCode() + "：" + subscriber.errorText());
            }
            if (subscriber.failed()) {
                throw new IllegalStateException("流式解析失败：" + subscriber.errorText());
            }
            return full.toString();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest("AI_CHAT_FAILED", "AI 流式回答失败：" + ex.getMessage());
        }
    }

    private final class LineSubscriber implements java.util.concurrent.Flow.Subscriber<String> {
        private final StringBuilder full;
        private final java.util.function.Consumer<String> onDelta;
        private volatile boolean failed;
        private volatile String error = "";

        private LineSubscriber(StringBuilder full, java.util.function.Consumer<String> onDelta) {
            this.full = full;
            this.onDelta = onDelta;
        }

        private boolean failed() { return failed; }
        private String errorText() { return error.isEmpty() ? "未知错误" : error; }

        @Override public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override public void onNext(String line) {
            if (line == null || !line.startsWith("data:")) return;
            String data = line.substring(5).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) return;
            try {
                JsonNode node = mapper.readTree(data);
                String delta = node.path("choices").path(0).path("delta").path("content").asText("");
                if (!delta.isEmpty()) {
                    full.append(delta);
                    onDelta.accept(delta);
                }
            } catch (Exception ex) {
                failed = true;
                error = ex.getMessage();
            }
        }

        @Override public void onError(Throwable throwable) {
            failed = true;
            error = throwable.getMessage();
        }

        @Override public void onComplete() { }
    }

    private JsonNode post(String url, String apiKey, ObjectNode body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("接口返回 " + response.statusCode() + "：" + excerpt(response.body()));
        }
        return mapper.readTree(response.body());
    }

    private String excerpt(String body) {
        if (body == null) return "";
        String message = body.replaceAll("\\s+", " ").trim();
        return message.length() > 200 ? message.substring(0, 200) + "…" : message;
    }
}
