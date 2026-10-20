package com.farmlive.web;

import com.farmlive.common.ApiResponse;
import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 直播管理员基础数据入口：农户、场地、设备。
 * 复用系统管理员已有 CRUD 实现，路径按角色拆分，字段与 /api/admin 保持一致。
 */
@RestController
@RequestMapping("/api/live")
public class LiveBasicDataController {
    private final AdminController adminController;

    public LiveBasicDataController(AdminController adminController) {
        this.adminController = adminController;
    }

    @GetMapping("/farmers")
    public ApiResponse<PageResult<Dtos.FarmerView>> farmers(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String countyCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return adminController.farmers(keyword, countyCode, page, size);
    }

    @PostMapping("/farmers")
    public ApiResponse<Dtos.FarmerView> createFarmer(@Valid @RequestBody Dtos.FarmerRequest request) {
        return adminController.createFarmer(request);
    }

    @PutMapping("/farmers/{id}")
    public ApiResponse<Dtos.FarmerView> updateFarmer(
            @PathVariable Long id, @Valid @RequestBody Dtos.FarmerRequest request) {
        return adminController.updateFarmer(id, request);
    }

    @DeleteMapping("/farmers/{id}")
    public ApiResponse<Void> deleteFarmer(@PathVariable Long id) {
        return adminController.deleteFarmer(id);
    }

    @GetMapping("/venues")
    public ApiResponse<PageResult<Dtos.VenueView>> venues(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String countyCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return adminController.venues(keyword, countyCode, page, size);
    }

    @PostMapping("/venues")
    public ApiResponse<Dtos.VenueView> createVenue(@Valid @RequestBody Dtos.VenueRequest request) {
        return adminController.createVenue(request);
    }

    @PutMapping("/venues/{id}")
    public ApiResponse<Dtos.VenueView> updateVenue(
            @PathVariable Long id, @Valid @RequestBody Dtos.VenueRequest request) {
        return adminController.updateVenue(id, request);
    }

    @DeleteMapping("/venues/{id}")
    public ApiResponse<Void> deleteVenue(@PathVariable Long id) {
        return adminController.deleteVenue(id);
    }

    @GetMapping("/devices")
    public ApiResponse<PageResult<Dtos.DeviceView>> devices(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return adminController.devices(keyword, page, size);
    }

    @PostMapping("/devices")
    public ApiResponse<Dtos.DeviceView> createDevice(@Valid @RequestBody Dtos.DeviceRequest request) {
        return adminController.createDevice(request);
    }

    @PutMapping("/devices/{id}")
    public ApiResponse<Dtos.DeviceView> updateDevice(
            @PathVariable Long id, @Valid @RequestBody Dtos.DeviceRequest request) {
        return adminController.updateDevice(id, request);
    }

    @DeleteMapping("/devices/{id}")
    public ApiResponse<Void> deleteDevice(@PathVariable Long id) {
        return adminController.deleteDevice(id);
    }
}
