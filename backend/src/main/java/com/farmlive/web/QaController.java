package com.farmlive.web;

import com.farmlive.auth.AuthInterceptor;
import com.farmlive.auth.CurrentUser;
import com.farmlive.common.ApiResponse;
import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import com.farmlive.service.QaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI 问答接口（全部登录用户）：提问、流式提问与历史记录。
 */
@RestController
@RequestMapping("/api/qa")
public class QaController {
    private final QaService service;
    private final ObjectMapper mapper;

    public QaController(QaService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping("/ask")
    public ApiResponse<Dtos.QaView> ask(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @Valid @RequestBody Dtos.QaAskRequest request) {
        return ApiResponse.ok(service.ask(user, request.question()));
    }

    /**
     * 流式问答 SSE：先逐片段推送 {"delta":"..."}，结束时推送 {"done":true,...}。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @Valid @RequestBody Dtos.QaAskRequest request) {
        SseEmitter emitter = new SseEmitter(120000L);
        CompletableFuture.runAsync(() -> {
            try {
                Dtos.QaView view = service.streamAsk(user, request.question(), delta -> {
                    try {
                        emitter.send(SseEmitter.event().data(
                                mapper.writeValueAsString(Map.of("delta", delta))));
                    } catch (Exception ex) {
                        throw new IllegalStateException("推送流式片段失败", ex);
                    }
                });
                emitter.send(SseEmitter.event().data(mapper.writeValueAsString(Map.of(
                        "done", true,
                        "recordId", view.id(),
                        "citations", view.citations() == null ? List.of() : view.citations()))));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    @GetMapping("/records")
    public ApiResponse<PageResult<Dtos.QaView>> history(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(service.history(user, page, size));
    }
}
