package com.farmlive.web;

import com.farmlive.auth.AuthInterceptor;
import com.farmlive.auth.CurrentUser;
import com.farmlive.common.ApiResponse;
import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import com.farmlive.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知接口：本人通知列表、标记已读。全部登录用户。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<PageResult<Dtos.NotificationView>> list(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        page = Math.max(1, page);
        size = Math.min(50, Math.max(1, size));
        return ApiResponse.ok(notificationService.listMine(user.id(), type, page, size));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @PathVariable Long id) {
        notificationService.markRead(user.id(), id);
        return ApiResponse.ok();
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user) {
        return ApiResponse.ok(notificationService.unreadCount(user.id()));
    }
}
