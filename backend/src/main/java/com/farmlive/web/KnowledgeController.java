package com.farmlive.web;

import com.farmlive.auth.AuthInterceptor;
import com.farmlive.auth.CurrentUser;
import com.farmlive.common.ApiResponse;
import com.farmlive.dto.Dtos;
import com.farmlive.service.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库管理接口（仅系统管理员）：上传、启停、重解析、删除。
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @GetMapping("/docs")
    public ApiResponse<List<Dtos.KnowledgeDocView>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping("/docs")
    public ApiResponse<Dtos.KnowledgeDocView> upload(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) {
        return ApiResponse.ok(service.upload(user, file, title));
    }

    @PostMapping("/docs/{id}/reparse")
    public ApiResponse<Dtos.KnowledgeDocView> reparse(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @PathVariable Long id) {
        return ApiResponse.ok(service.reparse(user, id));
    }

    @PutMapping("/docs/{id}/toggle")
    public ApiResponse<Dtos.KnowledgeDocView> toggle(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @PathVariable Long id,
            @Valid @RequestBody Dtos.KnowledgeToggleRequest request) {
        return ApiResponse.ok(service.toggle(user, id, request.enabled()));
    }

    @DeleteMapping("/docs/{id}")
    public ApiResponse<Void> delete(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @PathVariable Long id) {
        service.delete(user, id);
        return ApiResponse.ok();
    }
}
