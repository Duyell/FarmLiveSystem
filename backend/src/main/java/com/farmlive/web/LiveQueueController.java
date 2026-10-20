package com.farmlive.web;

import com.farmlive.common.ApiResponse;
import com.farmlive.dto.Dtos;
import com.farmlive.service.QueueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 直播管理员排队接口：场地队列查看、关闭队列。
 */
@RestController
@RequestMapping("/api/live/queues")
public class LiveQueueController {
    private final QueueService queueService;

    public LiveQueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping
    public ApiResponse<List<Dtos.QueueView>> list(
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String date) {
        return ApiResponse.ok(queueService.listForLive(venueId, date));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<Dtos.QueueView> close(@PathVariable Long id) {
        return ApiResponse.ok(queueService.close(id));
    }
}
