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
 * 农户端预约调整接口。
 */
@RestController
@RequestMapping("/api/farmer")
public class FarmerAdjustmentController {
    private final AdjustmentService service;

    public FarmerAdjustmentController(AdjustmentService service) {
        this.service = service;
    }

    @PostMapping("/bookings/{bookingId}/adjustments")
    public ApiResponse<Dtos.AdjustmentView> submit(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @PathVariable Long bookingId,
            @Valid @RequestBody Dtos.AdjustmentCreateRequest request) {
        return ApiResponse.ok(service.submit(user, bookingId, request));
    }

    @GetMapping("/adjustments")
    public ApiResponse<PageResult<Dtos.AdjustmentView>> list(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        page = Math.max(1, page);
        size = Math.min(50, Math.max(1, size));
        return ApiResponse.ok(service.myList(user, status, page, size));
    }

    @PostMapping("/adjustments/{id}/withdraw")
    public ApiResponse<Dtos.AdjustmentView> withdraw(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @PathVariable Long id) {
        return ApiResponse.ok(service.withdraw(user, id));
    }
}
