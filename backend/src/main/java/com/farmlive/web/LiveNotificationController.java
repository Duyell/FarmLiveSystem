package com.farmlive.web;

import com.farmlive.common.ApiResponse;
import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import com.farmlive.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 直播管理员通知接口：发送记录、手动重试、模板增删改查。
 */
@RestController
public class LiveNotificationController {
    private final NotificationService notificationService;

    public LiveNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/api/live/notifications")
    public ApiResponse<PageResult<Dtos.NotificationView>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        page = Math.max(1, page);
        size = Math.min(50, Math.max(1, size));
        return ApiResponse.ok(notificationService.listAll(type, channel, status, page, size));
    }

    @PostMapping("/api/live/notifications/{id}/retry")
    public ApiResponse<Void> retry(@PathVariable Long id) {
        notificationService.retryManual(id);
        return ApiResponse.ok();
    }

    @GetMapping("/api/live/notification-templates")
    public ApiResponse<List<Dtos.TemplateView>> templates(@RequestParam(required = false) String type) {
        return ApiResponse.ok(notificationService.listTemplates(type));
    }

    @PostMapping("/api/live/notification-templates")
    public ApiResponse<Void> createTemplate(@Valid @RequestBody Dtos.TemplateRequest request) {
        notificationService.createTemplate(request);
        return ApiResponse.ok();
    }

    @PutMapping("/api/live/notification-templates/{id}")
    public ApiResponse<Void> updateTemplate(@PathVariable Long id,
                                            @Valid @RequestBody Dtos.TemplateRequest request) {
        notificationService.updateTemplate(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/api/live/notification-templates/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        notificationService.deleteTemplate(id);
        return ApiResponse.ok();
    }
}
