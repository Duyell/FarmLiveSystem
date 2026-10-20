package com.farmlive.web;

import com.farmlive.common.ApiResponse;
import com.farmlive.dto.Dtos;
import com.farmlive.service.BookingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 可用资源查询：按县域（可选按时段）返回可用场地及其可用设备。
 */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {
    private final BookingService bookingService;

    public ResourceController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/available")
    public ApiResponse<List<Dtos.AvailableVenueView>> available(
            @RequestParam(required = false) String countyCode,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return ApiResponse.ok(bookingService.availableResources(countyCode, date, startTime, endTime));
    }
}
