package com.farmlive.web;

import com.farmlive.auth.AuthInterceptor;
import com.farmlive.auth.CurrentUser;
import com.farmlive.common.ApiResponse;
import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import com.farmlive.service.AdjustmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 直播管理员调整审批接口。
 */
@RestController
@RequestMapping("/api/live/adjustments")
public class LiveAdjustmentController {
    private final AdjustmentService service;

    public LiveAdjustmentController(AdjustmentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<Dtos.AdjustmentView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        page = Math.max(1, page);
        size = Math.min(50, Math.max(1, size));
        return ApiResponse.ok(service.liveList(status, keyword, page, size));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<Dtos.AdjustmentView> review(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @PathVariable Long id,
            @Valid @RequestBody Dtos.AdjustmentReviewRequest request) {
        return ApiResponse.ok(service.review(user, id, request));
    }
}
