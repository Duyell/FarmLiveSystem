package com.farmlive.web;

import com.farmlive.auth.AuthInterceptor;
import com.farmlive.auth.CurrentUser;
import com.farmlive.common.ApiResponse;
import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import com.farmlive.service.BookingService;
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
 * 直播管理员预约接口：预约列表、审核。
 */
@RestController
@RequestMapping("/api/live/bookings")
public class LiveBookingController {
    private final BookingService bookingService;

    public LiveBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public ApiResponse<PageResult<Dtos.BookingView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        page = Math.max(1, page);
        size = Math.min(50, Math.max(1, size));
        return ApiResponse.ok(bookingService.listForReview(status, keyword, page, size));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<Dtos.BookingView> review(@PathVariable Long id,
                                                @Valid @RequestBody Dtos.BookingReviewRequest request) {
        return ApiResponse.ok(bookingService.review(id, request));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<Dtos.BookingView> complete(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @PathVariable Long id,
            @Valid @RequestBody Dtos.BookingCompleteRequest request) {
        return ApiResponse.ok(bookingService.complete(user, id, request.status()));
    }
}
