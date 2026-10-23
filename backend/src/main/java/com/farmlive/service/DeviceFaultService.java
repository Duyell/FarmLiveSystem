package com.farmlive.service;

import com.farmlive.auth.CurrentUser;
import com.farmlive.common.BusinessException;
import com.farmlive.dto.Dtos;
import com.farmlive.repo.AdminRepository;
import com.farmlive.repo.DeviceFaultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DeviceFaultService {
    private final DeviceFaultRepository faultRepository;
    private final AdminRepository adminRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public DeviceFaultService(DeviceFaultRepository faultRepository, AdminRepository adminRepository,
                              NotificationService notificationService, AuditService auditService) {
        this.faultRepository = faultRepository;
        this.adminRepository = adminRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    public List<Dtos.DeviceFaultView> list(Long deviceId) {
        adminRepository.findDevice(deviceId)
                .orElseThrow(() -> BusinessException.notFound("设备组合不存在"));
        return faultRepository.listByDevice(deviceId);
    }

    @Transactional
    public Dtos.DeviceFaultView report(Long deviceId, Dtos.DeviceFaultCreateRequest request,
                                       CurrentUser user) {
        Dtos.DeviceView device = adminRepository.findDevice(deviceId)
                .orElseThrow(() -> BusinessException.notFound("设备组合不存在"));
        if ("disabled".equals(device.status())) {
            throw BusinessException.conflict("DEVICE_DISABLED", "设备组合已停用，不能报修");
        }
        if (faultRepository.hasActive(deviceId)) {
            throw BusinessException.conflict("FAULT_ACTIVE", "该设备组合已有未完成的故障记录");
        }
        Long id = faultRepository.create(deviceId, request.faultPart().trim(),
                request.description().trim(), user.id());
        adminRepository.updateDeviceStatus(deviceId, "maintenance");
        auditService.record(user, "DEVICE_FAULT_REPORT",
                "设备组合 " + device.deviceNo() + " 报修：" + request.faultPart().trim()
                        + "，" + request.description().trim());
        notificationService.notifyDeviceFault(deviceId, device.name());
        return faultRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("故障记录创建失败"));
    }

    @Transactional
    public Dtos.DeviceFaultView resolve(Long deviceId, Long faultId, String resolution,
                                        CurrentUser user) {
        Dtos.DeviceFaultView fault = faultRepository.findById(faultId)
                .orElseThrow(() -> BusinessException.notFound("故障记录不存在"));
        if (!deviceId.equals(fault.deviceId())) {
            throw BusinessException.badRequest("故障记录不属于该设备组合");
        }
        if (!"reported".equals(fault.status()) && !"repairing".equals(fault.status())) {
            throw BusinessException.conflict("FAULT_RESOLVED", "该故障已经完成维修");
        }
        String note = resolution == null || resolution.isBlank() ? "维修完成" : resolution.trim();
        faultRepository.resolve(faultId, note);
        Dtos.DeviceView device = adminRepository.findDevice(deviceId)
                .orElseThrow(() -> BusinessException.notFound("设备组合不存在"));
        if (!faultRepository.hasActive(deviceId)) {
            adminRepository.updateDeviceStatus(deviceId, "available");
            notificationService.notifyDeviceRecovered(deviceId, device.name());
        }
        auditService.record(user, "DEVICE_FAULT_RESOLVE",
                "设备组合 " + device.deviceNo() + " 维修完成：" + note);
        return faultRepository.findById(faultId)
                .orElseThrow(() -> BusinessException.notFound("故障记录不存在"));
    }
}
