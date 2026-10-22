package com.farmlive.web;

import com.farmlive.auth.AuthInterceptor;
import com.farmlive.auth.CurrentUser;
import com.farmlive.auth.PasswordService;
import com.farmlive.common.ApiResponse;
import com.farmlive.common.BusinessException;
import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import com.farmlive.repo.AdminRepository;
import com.farmlive.repo.RegionRepository;
import com.farmlive.repo.RegionRepository.RegionRow;
import com.farmlive.service.NotificationService;
import com.farmlive.service.AuditService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminRepository repository;
    private final PasswordService passwordService;
    private final RegionRepository regionRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public AdminController(AdminRepository repository, PasswordService passwordService,
                           RegionRepository regionRepository, NotificationService notificationService,
                           AuditService auditService) {
        this.repository = repository;
        this.passwordService = passwordService;
        this.regionRepository = regionRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Dtos.DashboardView> dashboard() {
        return ApiResponse.ok(repository.dashboard());
    }

    @GetMapping("/roles")
    public ApiResponse<List<Dtos.RoleView>> roles() {
        return ApiResponse.ok(repository.listRoles());
    }

    // ---------------- accounts ----------------

    @GetMapping("/accounts")
    public ApiResponse<PageResult<Dtos.AccountView>> accounts(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(repository.listAccounts(keyword, normalizePage(page), normalizeSize(size)));
    }

    @PostMapping("/accounts")
    public ApiResponse<Dtos.AccountView> createAccount(@Valid @RequestBody Dtos.AccountCreateRequest request) {
        String username = request.username().trim();
        if (!username.matches("^[A-Za-z0-9_]{3,30}$")) {
            throw BusinessException.badRequest("用户名只能是 3-30 位字母、数字或下划线");
        }
        if (repository.usernameExists(username)) {
            throw BusinessException.conflict("用户名已存在");
        }
        Dtos.RoleView role = repository.findRole(request.roleId())
                .orElseThrow(() -> BusinessException.badRequest("角色不存在"));
        String status = normalizeAccountStatus(request.status());
        validateFarmerLink(role, request.farmerId(), null);
        String hash = encodePassword(request.password());
        Long id = repository.createAccount(username, hash, request.displayName().trim(),
                role.id(), request.farmerId(), status);
        auditService.recordCurrent("ACCOUNT_CREATE", "创建账号 " + username + "，角色 " + role.code());
        return ApiResponse.ok(repository.findAccount(id)
                .orElseThrow(() -> BusinessException.notFound("账号创建失败")));
    }

    @PutMapping("/accounts/{id}")
    public ApiResponse<Dtos.AccountView> updateAccount(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser current,
            @Valid @RequestBody Dtos.AccountUpdateRequest request) {
        Dtos.AccountView existing = repository.findAccount(id)
                .orElseThrow(() -> BusinessException.notFound("账号不存在"));
        Dtos.RoleView role = repository.findRole(request.roleId())
                .orElseThrow(() -> BusinessException.badRequest("角色不存在"));
        String status = normalizeAccountStatus(request.status());

        if (current.id().equals(id) && !existing.roleId().equals(request.roleId())) {
            throw BusinessException.badRequest("不能修改自己的角色");
        }
        if (current.id().equals(id) && "disabled".equals(status)) {
            throw BusinessException.badRequest("不能停用自己的账号");
        }
        if ("system_admin".equals(existing.roleCode())
                && (!"system_admin".equals(role.code()) || "disabled".equals(status))
                && repository.countSystemAdmins() <= 1) {
            throw BusinessException.conflict("不能停用或降级最后一个系统管理员");
        }
        validateFarmerLink(role, request.farmerId(), id);
        repository.updateAccount(id, request.displayName().trim(), role.id(), request.farmerId(), status);
        auditService.recordCurrent("ACCOUNT_UPDATE",
                "修改账号 " + existing.username() + "，角色 " + existing.roleCode() + " -> " + role.code()
                        + "，状态 " + existing.status() + " -> " + status);
        return ApiResponse.ok(repository.findAccount(id)
                .orElseThrow(() -> BusinessException.notFound("账号不存在")));
    }

    @DeleteMapping("/accounts/{id}")
    public ApiResponse<Void> deleteAccount(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser current) {
        Dtos.AccountView existing = repository.findAccount(id)
                .orElseThrow(() -> BusinessException.notFound("账号不存在"));
        if (current.id().equals(id)) {
            throw BusinessException.badRequest("不能删除自己的账号");
        }
        if ("system_admin".equals(existing.roleCode()) && repository.countSystemAdmins() <= 1) {
            throw BusinessException.conflict("不能删除最后一个系统管理员");
        }
        repository.deleteAccount(id);
        auditService.recordCurrent("ACCOUNT_DELETE", "删除账号 " + existing.username());
        return ApiResponse.ok();
    }

    @PostMapping("/accounts/{id}/reset-password")
    public ApiResponse<Void> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody Dtos.ResetPasswordRequest request) {
        Dtos.AccountView account = repository.findAccount(id)
                .orElseThrow(() -> BusinessException.notFound("账号不存在"));
        String hash = encodePassword(request.newPassword());
        repository.updatePassword(id, hash, true);
        auditService.recordCurrent("ACCOUNT_RESET_PASSWORD", "重置账号 " + account.username() + " 的密码");
        return ApiResponse.ok();
    }

    private void validateFarmerLink(Dtos.RoleView role, Long farmerId, Long excludeAccountId) {
        if ("farmer".equals(role.code())) {
            if (farmerId == null) {
                throw BusinessException.badRequest("农户角色必须关联农户");
            }
            repository.findFarmer(farmerId)
                    .orElseThrow(() -> BusinessException.badRequest("关联的农户不存在"));
            if (repository.farmerHasAccount(farmerId, excludeAccountId)) {
                throw BusinessException.conflict("该农户已经有一个账号");
            }
        } else if (farmerId != null) {
            throw BusinessException.badRequest("非农户角色不能关联农户");
        }
    }

    private String encodePassword(String raw) {
        try {
            passwordService.validateStrength(raw);
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest(ex.getMessage());
        }
        return passwordService.encode(raw);
    }

    // ---------------- farmers ----------------

    @GetMapping("/farmers")
    public ApiResponse<PageResult<Dtos.FarmerView>> farmers(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String countyCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(repository.listFarmers(keyword, countyCode, normalizePage(page), normalizeSize(size)));
    }

    @PostMapping("/farmers")
    public ApiResponse<Dtos.FarmerView> createFarmer(@Valid @RequestBody Dtos.FarmerRequest request) {
        validatePhone(request.phone());
        String county = resolveCountyName(request.countyCode());
        String qualification = normalizeQualification(request.qualificationStatus());
        String status = normalizeAccountStatus(request.status());
        Long id = repository.createFarmer(request.farmerNo().trim(), request.name().trim(),
                county, request.countyCode().trim(), request.phone().trim(), qualification, status);
        auditService.recordCurrent("FARMER_CREATE",
                "创建农户 " + request.farmerNo().trim() + " / " + request.name().trim());
        return ApiResponse.ok(repository.findFarmer(id)
                .orElseThrow(() -> BusinessException.notFound("农户创建失败")));
    }

    @PutMapping("/farmers/{id}")
    public ApiResponse<Dtos.FarmerView> updateFarmer(
            @PathVariable Long id,
            @Valid @RequestBody Dtos.FarmerRequest request) {
        Dtos.FarmerView existing = repository.findFarmer(id)
                .orElseThrow(() -> BusinessException.notFound("农户不存在"));
        String phone = request.phone().trim();
        if (phone.contains("****")) {
            phone = repository.findFarmerPhone(id)
                    .orElseThrow(() -> BusinessException.notFound("农户不存在"));
        }
        validatePhone(phone);
        String county = resolveCountyName(request.countyCode());
        String qualification = normalizeQualification(request.qualificationStatus());
        String status = normalizeAccountStatus(request.status());
        repository.updateFarmer(id, request.name().trim(), county, request.countyCode().trim(),
                phone, qualification, status);
        auditService.recordCurrent("FARMER_UPDATE",
                "农户 " + existing.farmerNo() + "：" + existing.name() + " -> " + request.name().trim()
                        + "，区域 " + existing.countyCode() + " -> " + request.countyCode().trim()
                        + "，资质 " + existing.qualificationStatus() + " -> " + qualification
                        + "，状态 " + existing.status() + " -> " + status
                        + "，原因：" + safeReason(request.changeReason()));
        return ApiResponse.ok(repository.findFarmer(id)
                .orElseThrow(() -> BusinessException.notFound("农户不存在")));
    }

    @DeleteMapping("/farmers/{id}")
    public ApiResponse<Void> deleteFarmer(@PathVariable Long id) {
        Dtos.FarmerView existing = repository.findFarmer(id)
                .orElseThrow(() -> BusinessException.notFound("农户不存在"));
        if (repository.farmerHasAccount(id, null)) {
            throw BusinessException.conflict("该农户有账号，不能删除");
        }
        if (repository.farmerHasBooking(id)) {
            throw BusinessException.conflict("该农户有预约，不能删除");
        }
        repository.deleteFarmer(id);
        auditService.recordCurrent("FARMER_DELETE",
                "删除农户 " + existing.farmerNo() + " / " + existing.name());
        return ApiResponse.ok();
    }

    // ---------------- venues ----------------

    @GetMapping("/venues")
    public ApiResponse<PageResult<Dtos.VenueView>> venues(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String countyCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(repository.listVenues(keyword, countyCode, normalizePage(page), normalizeSize(size)));
    }

    @PostMapping("/venues")
    public ApiResponse<Dtos.VenueView> createVenue(@Valid @RequestBody Dtos.VenueRequest request) {
        LocalTime start = parseTime(request.openStart());
        LocalTime end = parseTime(request.openEnd());
        validateVenueTimes(start, end, request.capacity());
        resolveCountyName(request.countyCode());
        String status = normalizeResourceStatus(request.status());
        Long id = repository.createVenue(request.venueNo().trim(), request.name().trim(),
                request.location().trim(), request.capacity(), start, end, request.countyCode().trim(), status);
        auditService.recordCurrent("VENUE_CREATE",
                "创建场地 " + request.venueNo().trim() + " / " + request.name().trim());
        return ApiResponse.ok(repository.findVenue(id)
                .orElseThrow(() -> BusinessException.notFound("场地创建失败")));
    }

    @PutMapping("/venues/{id}")
    public ApiResponse<Dtos.VenueView> updateVenue(
            @PathVariable Long id,
            @Valid @RequestBody Dtos.VenueRequest request) {
        Dtos.VenueView existing = repository.findVenue(id)
                .orElseThrow(() -> BusinessException.notFound("场地不存在"));
        LocalTime start = parseTime(request.openStart());
        LocalTime end = parseTime(request.openEnd());
        validateVenueTimes(start, end, request.capacity());
        resolveCountyName(request.countyCode());
        String status = normalizeResourceStatus(request.status());
        repository.updateVenue(id, request.name().trim(), request.location().trim(),
                request.capacity(), start, end, request.countyCode().trim(), status);
        auditService.recordCurrent("VENUE_UPDATE",
                "场地 " + existing.venueNo() + "：" + existing.name() + " -> " + request.name().trim()
                        + "，状态 " + existing.status() + " -> " + status);
        return ApiResponse.ok(repository.findVenue(id)
                .orElseThrow(() -> BusinessException.notFound("场地不存在")));
    }

    @DeleteMapping("/venues/{id}")
    public ApiResponse<Void> deleteVenue(@PathVariable Long id) {
        Dtos.VenueView existing = repository.findVenue(id)
                .orElseThrow(() -> BusinessException.notFound("场地不存在"));
        if (repository.venueHasDevices(id)) {
            throw BusinessException.conflict("该场地下还有设备，不能删除");
        }
        if (repository.venueHasBooking(id)) {
            throw BusinessException.conflict("该场地有预约，不能删除");
        }
        repository.deleteVenue(id);
        auditService.recordCurrent("VENUE_DELETE",
                "删除场地 " + existing.venueNo() + " / " + existing.name());
        return ApiResponse.ok();
    }

    // ---------------- devices ----------------

    @GetMapping("/devices")
    public ApiResponse<PageResult<Dtos.DeviceView>> devices(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(repository.listDevices(keyword, normalizePage(page), normalizeSize(size)));
    }

    @PostMapping("/devices")
    public ApiResponse<Dtos.DeviceView> createDevice(@Valid @RequestBody Dtos.DeviceRequest request) {
        repository.findVenue(request.venueId())
                .orElseThrow(() -> BusinessException.badRequest("所属场地不存在"));
        String status = normalizeResourceStatus(request.status());
        LocalDate calibration = parseDate(request.calibrationDate());
        Long id = repository.createDevice(request.deviceNo().trim(), request.name().trim(),
                trimToNull(request.model()), trimToNull(request.config()),
                request.venueId(), status, calibration);
        auditService.recordCurrent("DEVICE_CREATE",
                "创建设备组合 " + request.deviceNo().trim() + " / " + request.name().trim());
        return ApiResponse.ok(repository.findDevice(id)
                .orElseThrow(() -> BusinessException.notFound("设备创建失败")));
    }

    @PutMapping("/devices/{id}")
    public ApiResponse<Dtos.DeviceView> updateDevice(
            @PathVariable Long id,
            @Valid @RequestBody Dtos.DeviceRequest request) {
        Dtos.DeviceView existing = repository.findDevice(id)
                .orElseThrow(() -> BusinessException.notFound("设备不存在"));
        repository.findVenue(request.venueId())
                .orElseThrow(() -> BusinessException.badRequest("所属场地不存在"));
        String status = normalizeResourceStatus(request.status());
        LocalDate calibration = parseDate(request.calibrationDate());
        repository.updateDevice(id, request.name().trim(), trimToNull(request.model()),
                trimToNull(request.config()), request.venueId(), status, calibration);
        if (!"maintenance".equals(existing.status()) && !"disabled".equals(existing.status())
                && ("maintenance".equals(status) || "disabled".equals(status))) {
            notificationService.notifyDeviceFault(id, existing.name());
        }
        auditService.recordCurrent("DEVICE_UPDATE",
                "设备组合 " + existing.deviceNo() + "：" + existing.name() + " -> " + request.name().trim()
                        + "，场地 " + existing.venueId() + " -> " + request.venueId()
                        + "，状态 " + existing.status() + " -> " + status
                        + "，校准 " + existing.calibrationDate() + " -> " + request.calibrationDate()
                        + "，原因：" + safeReason(request.changeReason()));
        return ApiResponse.ok(repository.findDevice(id)
                .orElseThrow(() -> BusinessException.notFound("设备不存在")));
    }

    @DeleteMapping("/devices/{id}")
    public ApiResponse<Void> deleteDevice(@PathVariable Long id) {
        Dtos.DeviceView existing = repository.findDevice(id)
                .orElseThrow(() -> BusinessException.notFound("设备不存在"));
        if (repository.deviceHasBooking(id)) {
            throw BusinessException.conflict("该设备有预约，不能删除");
        }
        repository.deleteDevice(id);
        auditService.recordCurrent("DEVICE_DELETE",
                "删除设备 " + existing.deviceNo() + " / " + existing.name());
        return ApiResponse.ok();
    }

    // ---------------- helpers ----------------

    private int normalizePage(int page) {
        return Math.max(1, page);
    }

    private int normalizeSize(int size) {
        return Math.min(100, Math.max(1, size));
    }

    private String normalizeAccountStatus(String status) {
        if (status == null || status.isBlank()) return "active";
        if (!List.of("active", "disabled").contains(status)) {
            throw BusinessException.badRequest("状态只能是 active 或 disabled");
        }
        return status;
    }

    private String normalizeQualification(String status) {
        if (status == null || status.isBlank()) return "pending";
        if (!List.of("pending", "verified", "rejected", "disabled").contains(status)) {
            throw BusinessException.badRequest("资质状态不正确");
        }
        return status;
    }

    private String normalizeResourceStatus(String status) {
        if (status == null || status.isBlank()) return "available";
        if (!List.of("available", "maintenance", "disabled").contains(status)) {
            throw BusinessException.badRequest("资源状态不正确");
        }
        return status;
    }

    private void validatePhone(String phone) {
        if (phone == null || !phone.matches("^1\\d{10}$")) {
            throw BusinessException.badRequest("手机号必须是 11 位数字");
        }
    }

    /**
     * 校验区域编码：必须存在、启用中且为县级（level=3），返回区域名称作为 county 快照。
     * county 列 NOT NULL 且只做展示，统一由后端按编码回写，防止前端伪造文本。
     */
    private String resolveCountyName(String countyCode) {
        if (countyCode == null || countyCode.isBlank()) {
            throw BusinessException.badRequest("所属区域不能为空");
        }
        RegionRow region = regionRepository.findRowByCode(countyCode)
                .orElseThrow(() -> BusinessException.badRequest("所属区域不存在"));
        if (!region.enabled()) {
            throw BusinessException.badRequest("所属区域已停用");
        }
        if (region.level() != 3) {
            throw BusinessException.badRequest("所属区域必须选择到县 / 区一级");
        }
        return region.name();
    }

    private void validateVenueTimes(LocalTime start, LocalTime end, Integer capacity) {
        if (capacity == null || capacity <= 0) {
            throw BusinessException.badRequest("容纳人数必须大于 0");
        }
        if (!start.isBefore(end)) {
            throw BusinessException.badRequest("开放开始时间必须早于结束时间");
        }
    }

    private LocalTime parseTime(String value) {
        try {
            return AdminRepository.parseTime(value);
        } catch (Exception ex) {
            throw BusinessException.badRequest("时间格式必须是 HH:mm");
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return AdminRepository.parseDate(value);
        } catch (Exception ex) {
            throw BusinessException.badRequest("日期格式必须是 yyyy-MM-dd");
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeReason(String value) {
        return value == null || value.isBlank() ? "未填写" : value.trim();
    }
}
