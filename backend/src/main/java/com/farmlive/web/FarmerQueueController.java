package com.farmlive.web;

import com.farmlive.auth.AuthInterceptor;
import com.farmlive.auth.CurrentUser;
import com.farmlive.common.ApiResponse;
import com.farmlive.dto.Dtos;
import com.farmlive.service.QueueService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 农户端排队接口：加入排队、我的排队、候补确认、放弃。
 */
@RestController
@RequestMapping("/api/farmer/queues")
public class FarmerQueueController {
    private final QueueService queueService;

    public FarmerQueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping
    public ApiResponse<Dtos.QueueView> join(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @Valid @RequestBody Dtos.QueueCreateRequest request) {
        return ApiResponse.ok(queueService.join(user, request));
    }

    @GetMapping("/slot-status")
    public ApiResponse<Dtos.SlotStatusView> slotStatus(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @RequestParam Long venueId,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        return ApiResponse.ok(queueService.slotStatus(user, venueId, startTime, endTime));
    }

    @GetMapping
    public ApiResponse<List<Dtos.QueueView>> mine(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user) {
        return ApiResponse.ok(queueService.listMine(user));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<Dtos.QueueView> confirm(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @PathVariable Long id) {
        return ApiResponse.ok(queueService.confirm(user, id));
    }

    @PostMapping("/{id}/abandon")
    public ApiResponse<Dtos.QueueView> abandon(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @PathVariable Long id) {
        return ApiResponse.ok(queueService.abandon(user, id));
    }
}
