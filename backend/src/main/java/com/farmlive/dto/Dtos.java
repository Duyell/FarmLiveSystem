package com.farmlive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class Dtos {
    private Dtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "旧密码不能为空") String oldPassword,
            @NotBlank(message = "新密码不能为空") String newPassword
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "新密码不能为空") String newPassword
    ) {
    }

    public record UserView(
            Long id,
            String username,
            String displayName,
            String roleCode,
            String roleName,
            Long farmerId,
            String farmerName,
            boolean mustChangePassword
    ) {
    }

    public record LoginResponse(String token, UserView user) {
    }

    public record RoleView(Long id, String code, String name) {
    }

    public record AccountView(
            Long id,
            String username,
            String displayName,
            Long roleId,
            String roleCode,
            String roleName,
            Long farmerId,
            String farmerName,
            String status,
            boolean mustChangePassword,
            String lastLoginAt,
            String createdAt
    ) {
    }

    public record AccountCreateRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password,
            @NotBlank(message = "显示名不能为空") String displayName,
            @NotNull(message = "角色不能为空") Long roleId,
            Long farmerId,
            String status
    ) {
    }

    public record AccountUpdateRequest(
            @NotBlank(message = "显示名不能为空") String displayName,
            @NotNull(message = "角色不能为空") Long roleId,
            Long farmerId,
            String status
    ) {
    }

    public record FarmerView(
            Long id,
            String farmerNo,
            String name,
            String county,
            String phone,
            String qualificationStatus,
            String status,
            String createdAt
    ) {
    }

    public record FarmerRequest(
            @NotBlank(message = "农户编号不能为空") String farmerNo,
            @NotBlank(message = "姓名不能为空") String name,
            @NotBlank(message = "县域不能为空") String county,
            @NotBlank(message = "手机号不能为空") String phone,
            String qualificationStatus,
            String status
    ) {
    }

    public record VenueView(
            Long id,
            String venueNo,
            String name,
            String location,
            Integer capacity,
            String openStart,
            String openEnd,
            String status,
            String createdAt
    ) {
    }

    public record VenueRequest(
            @NotBlank(message = "场地编号不能为空") String venueNo,
            @NotBlank(message = "场地名称不能为空") String name,
            @NotBlank(message = "位置不能为空") String location,
            @NotNull(message = "容纳人数不能为空") Integer capacity,
            @NotBlank(message = "开放开始时间不能为空") String openStart,
            @NotBlank(message = "开放结束时间不能为空") String openEnd,
            String status
    ) {
    }

    public record DeviceView(
            Long id,
            String deviceNo,
            String name,
            String model,
            Long venueId,
            String venueName,
            String status,
            String calibrationDate,
            String createdAt
    ) {
    }

    public record DeviceRequest(
            @NotBlank(message = "设备编号不能为空") String deviceNo,
            @NotBlank(message = "设备名称不能为空") String name,
            String model,
            @NotNull(message = "所属场地不能为空") Long venueId,
            String status,
            String calibrationDate
    ) {
    }

    public record DashboardView(long accounts, long farmers, long venues, long devices, long bookings) {
    }

}
