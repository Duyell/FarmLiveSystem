package com.farmlive.web;

import com.farmlive.auth.AuthInterceptor;
import com.farmlive.auth.CurrentUser;
import com.farmlive.common.ApiResponse;
import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import com.farmlive.service.SalesService;
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

/**
 * 直播管理员销售录入接口。
 */
@RestController
@RequestMapping("/api/live/sales")
public class SalesController {
    private final SalesService service;

    public SalesController(SalesService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<Dtos.SalesView>> list(
            @RequestParam(required = false) Long bookingId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        page = Math.max(1, page);
        size = Math.min(50, Math.max(1, size));
        return ApiResponse.ok(service.list(bookingId, keyword, page, size));
    }

    @PostMapping
    public ApiResponse<Dtos.SalesView> create(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @Valid @RequestBody Dtos.SalesRequest request) {
        return ApiResponse.ok(service.create(user, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Dtos.SalesView> update(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @PathVariable Long id,
            @Valid @RequestBody Dtos.SalesRequest request) {
        return ApiResponse.ok(service.update(user, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @PathVariable Long id) {
        service.delete(user, id);
        return ApiResponse.ok(null);
    }
}
