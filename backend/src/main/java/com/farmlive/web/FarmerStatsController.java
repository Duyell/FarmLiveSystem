package com.farmlive.web;

import com.farmlive.auth.AuthInterceptor;
import com.farmlive.auth.CurrentUser;
import com.farmlive.common.ApiResponse;
import com.farmlive.common.BusinessException;
import com.farmlive.dto.Dtos;
import com.farmlive.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 农户个人数据：只返回当前账号关联农户的数据。
 */
@RestController
@RequestMapping("/api/farmer/stats")
public class FarmerStatsController {
    private final StatsService statsService;

    public FarmerStatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public ApiResponse<Dtos.MyStatsView> overview(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user) {
        return ApiResponse.ok(statsService.myStats(farmerId(user)));
    }

    @GetMapping("/details")
    public ApiResponse<List<Dtos.StatsDetailView>> details(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user) {
        return ApiResponse.ok(statsService.myDetails(farmerId(user)));
    }

    private Long farmerId(CurrentUser user) {
        if (user.farmerId() == null) {
            throw BusinessException.badRequest("当前账号未关联农户");
        }
        return user.farmerId();
    }
}
