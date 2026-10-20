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
 * 农户端预约接口：提交、我的预约、详情、取消。
 */
@RestController
@RequestMapping("/api/farmer/bookings")
public class FarmerBookingController {
    private final BookingService bookingService;

    public FarmerBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ApiResponse<Dtos.BookingView> submit(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @Valid @RequestBody Dtos.BookingCreateRequest request) {
        return ApiResponse.ok(bookingService.submit(user, request));
    }

    @GetMapping
    public ApiResponse<PageResult<Dtos.BookingView>> list(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        page = Math.max(1, page);
        size = Math.min(50, Math.max(1, size));
        return ApiResponse.ok(bookingService.myBookings(user, status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<Dtos.BookingView> detail(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @PathVariable Long id) {
        return ApiResponse.ok(bookingService.myBooking(user, id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Dtos.BookingView> cancel(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @PathVariable Long id,
            @RequestBody(required = false) Dtos.BookingCancelRequest request) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.ok(bookingService.cancel(user, id, reason));
    }
}
