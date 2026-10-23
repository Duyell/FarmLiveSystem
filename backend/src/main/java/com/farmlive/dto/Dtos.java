package com.farmlive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class Dtos {
    private Dtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password,
            String roleCode,
            String captchaId,
            String captchaAnswer
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

    public record RegionView(
            String code,
            String parentCode,
            int level,
            String name
    ) {
    }

    // ---------------- booking ----------------

    public record BookingProductItem(
            String productName,
            String spec,
            Double quantity,
            String unit
    ) {
    }

    public record BookingCreateRequest(
            @NotNull(message = "场地不能为空") Long venueId,
            @NotNull(message = "设备不能为空") Long deviceId,
            @NotBlank(message = "开始时间不能为空") String startTime,
            @NotBlank(message = "结束时间不能为空") String endTime,
            @NotBlank(message = "直播主题不能为空") String topic,
            List<BookingProductItem> products
    ) {
    }

    public record BookingReviewRequest(
            @NotBlank(message = "审核结果不能为空") String result,
            String comment,
            String recommendedTime
    ) {
    }

    public record BookingCancelRequest(String reason) {
    }

    public record BookingProductView(
            String productName,
            String spec,
            Double quantity,
            String unit
    ) {
    }

    public record BookingView(
            Long id,
            String bookingNo,
            Long farmerId,
            String farmerName,
            Long venueId,
            String venueName,
            Long deviceId,
            String deviceNo,
            String deviceName,
            String startTime,
            String endTime,
            String topic,
            String status,
            String reviewComment,
            String recommendedTime,
            String cancelReason,
            List<BookingProductView> products,
            String createdAt
    ) {
    }

    public record AvailableDeviceView(
            Long deviceId,
            String deviceNo,
            String name,
            String config
    ) {
    }

    public record AvailableVenueView(
            Long venueId,
            String venueNo,
            String venueName,
            String countyCode,
            String openStart,
            String openEnd,
            List<AvailableDeviceView> devices
    ) {
    }

    // ---------------- queue ----------------

    public record QueueCreateRequest(
            @NotNull(message = "场地不能为空") Long venueId,
            @NotBlank(message = "开始时间不能为空") String startTime,
            @NotBlank(message = "结束时间不能为空") String endTime
    ) {
    }

    public record SlotStatusView(
            boolean occupied,
            boolean selfOccupied,
            boolean canJoin,
            int queueCount,
            String reason
    ) {
    }

    // ---------------- notification ----------------

    public record NotificationView(
            Long id,
            String type,
            String channel,
            String title,
            String content,
            String status,
            String smsStatus,
            int retryCount,
            String sentAt,
            boolean read,
            String createdAt
    ) {
    }

    public record TemplateView(
            Long id,
            String code,
            String name,
            String type,
            String titleTemplate,
            String contentTemplate,
            boolean enabled,
            String updatedAt
    ) {
    }

    public record TemplateRequest(
            @NotBlank(message = "模板编码不能为空") String code,
            @NotBlank(message = "模板名称不能为空") String name,
            @NotBlank(message = "通知类型不能为空") String type,
            @NotBlank(message = "标题模板不能为空") String titleTemplate,
            @NotBlank(message = "内容模板不能为空") String contentTemplate,
            Boolean enabled
    ) {
    }

    public record QueueView(
            Long id,
            String queueNo,
            Long farmerId,
            String farmerName,
            Long venueId,
            String venueName,
            String slotStart,
            String slotEnd,
            int position,
            int totalInQueue,
            int estimatedWait,
            String status,
            String candidateDeadline,
            Long assignedDeviceId,
            String assignedDeviceName,
            Long confirmedBookingId,
            String createdAt
    ) {
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
            String countyCode,
            String phone,
            String qualificationStatus,
            String status,
            String createdAt
    ) {
    }

    public record FarmerRequest(
            @NotBlank(message = "农户编号不能为空") String farmerNo,
            @NotBlank(message = "姓名不能为空") String name,
            String county,
            @NotBlank(message = "所属区域不能为空") String countyCode,
            @NotBlank(message = "手机号不能为空") String phone,
            String qualificationStatus,
            String status,
            String changeReason
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
            String countyCode,
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
            @NotBlank(message = "所属区域不能为空") String countyCode,
            String status
    ) {
    }

    public record DeviceView(
            Long id,
            String deviceNo,
            String name,
            String model,
            String config,
            Long venueId,
            String venueName,
            String status,
            String calibrationDate,
            String createdAt
    ) {
    }

    public record DeviceRequest(
            @NotBlank(message = "组合编号不能为空") String deviceNo,
            @NotBlank(message = "组合名称不能为空") String name,
            String model,
            String config,
            @NotNull(message = "所属场地不能为空") Long venueId,
            String status,
            String calibrationDate,
            String changeReason
    ) {
    }

    public record DeviceFaultView(
            Long id,
            Long deviceId,
            String deviceName,
            String faultPart,
            String description,
            String status,
            String reportedBy,
            String reportedAt,
            String resolvedAt,
            String resolution
    ) {
    }

    public record DeviceFaultCreateRequest(
            @NotBlank(message = "故障部位不能为空") String faultPart,
            @NotBlank(message = "故障描述不能为空") String description
    ) {
    }

    public record DeviceFaultResolveRequest(String resolution) {
    }

    public record DashboardView(long accounts, long farmers, long venues, long devices, long bookings,
                                long pendingReview, long todayLive,
                                long availableVenues, long availableDevices) {
    }

    // ---------------- adjustment ----------------

    public record AdjustmentCreateRequest(
            @NotNull(message = "新场地不能为空") Long newVenueId,
            @NotNull(message = "新设备不能为空") Long newDeviceId,
            @NotBlank(message = "新开始时间不能为空") String newStartTime,
            @NotBlank(message = "新结束时间不能为空") String newEndTime,
            @NotBlank(message = "调整原因不能为空") String reason
    ) {
    }

    public record AdjustmentReviewRequest(
            @NotBlank(message = "审核结果不能为空") String result,
            String comment
    ) {
    }

    public record AdjustmentView(
            Long id,
            String requestNo,
            Long bookingId,
            String bookingNo,
            Long farmerId,
            String farmerName,
            Long oldVenueId,
            String oldVenueName,
            String oldStartTime,
            String oldEndTime,
            Long newVenueId,
            String newVenueName,
            Long newDeviceId,
            String newDeviceNo,
            String newDeviceName,
            String newStartTime,
            String newEndTime,
            String reason,
            String status,
            String reviewComment,
            String createdAt,
            String reviewedAt
    ) {
    }

    // ---------------- sales ----------------

    public record BookingCompleteRequest(
            @NotBlank(message = "完成状态不能为空") String status
    ) {
    }

    public record SalesRequest(
            @NotNull(message = "预约不能为空") Long bookingId,
            @NotBlank(message = "农产品名称不能为空") String productName,
            String spec,
            @NotNull(message = "数量不能为空") Double quantity,
            @NotBlank(message = "单位不能为空") String unit
    ) {
    }

    public record SalesView(
            Long id,
            Long bookingId,
            String bookingNo,
            String farmerName,
            String venueName,
            String startTime,
            String productName,
            String spec,
            Double quantity,
            String unit,
            String createdAt,
            String updatedAt
    ) {
    }

    // ---------------- stats ----------------

    public record StatsOverviewView(
            double occupancyRate,
            long liveCount,
            double soldQuantity,
            double successRate,
            long bookingTotal,
            long approvedCount,
            long completedCount
    ) {
    }

    public record StatsDetailView(
            Long bookingId,
            String bookingNo,
            String farmerName,
            String venueName,
            String countyCode,
            String startTime,
            String topic,
            double soldQuantity,
            long salesCount
    ) {
    }

    public record StatsTrendView(String period, long liveCount, double soldQuantity) {
    }

    public record MyStatsView(
            long bookingTotal,
            long approvedCount,
            long completedCount,
            double soldQuantity
    ) {
    }

    // ---------------- audit ----------------

    public record AuditLogView(
            Long id,
            Long operatorId,
            String operatorName,
            String roleCode,
            String action,
            String detail,
            String ip,
            String createdAt
    ) {
    }

    // ---------------- qualification ----------------

    public record QualificationView(
            Long id,
            Long farmerId,
            String originalName,
            String contentType,
            long fileSize,
            String status,
            String reviewComment,
            String uploaderName,
            String createdAt,
            String reviewedAt
    ) {
    }

    public record QualificationReviewRequest(
            @NotBlank(message = "审核结果不能为空") String result,
            String comment
    ) {
    }

}
