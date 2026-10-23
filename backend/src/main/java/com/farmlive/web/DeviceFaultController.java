package com.farmlive.web;

import com.farmlive.auth.AuthInterceptor;
import com.farmlive.auth.CurrentUser;
import com.farmlive.common.ApiResponse;
import com.farmlive.dto.Dtos;
import com.farmlive.service.DeviceFaultService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/live/devices")
public class DeviceFaultController {
    private final DeviceFaultService service;

    public DeviceFaultController(DeviceFaultService service) {
        this.service = service;
    }

    @GetMapping("/{deviceId}/faults")
    public ApiResponse<List<Dtos.DeviceFaultView>> list(@PathVariable Long deviceId) {
        return ApiResponse.ok(service.list(deviceId));
    }

    @PostMapping("/{deviceId}/faults")
    public ApiResponse<Dtos.DeviceFaultView> report(
            @PathVariable Long deviceId,
            @Valid @RequestBody Dtos.DeviceFaultCreateRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user) {
        return ApiResponse.ok(service.report(deviceId, request, user));
    }

    @PostMapping("/{deviceId}/faults/{faultId}/resolve")
    public ApiResponse<Dtos.DeviceFaultView> resolve(
            @PathVariable Long deviceId,
            @PathVariable Long faultId,
            @RequestBody(required = false) Dtos.DeviceFaultResolveRequest request,
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user) {
        String resolution = request == null ? null : request.resolution();
        return ApiResponse.ok(service.resolve(deviceId, faultId, resolution, user));
    }
}
