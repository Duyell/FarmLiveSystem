package com.farmlive.service;

import com.farmlive.repo.CalibrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 设备校准提醒：定期扫描 7 天内到达校准周期的可用设备，
 * 通知所有在职直播管理员；同一设备同一校准日期只提醒一次（幂等）。
 */
@Service
public class CalibrationService {
    private static final Logger log = LoggerFactory.getLogger(CalibrationService.class);

    private final CalibrationRepository repository;
    private final NotificationService notificationService;

    public CalibrationService(CalibrationRepository repository, NotificationService notificationService) {
        this.repository = repository;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelay = 300_000)
    public void scanCalibrationDue() {
        List<CalibrationRepository.DeviceDue> devices = repository.devicesDue();
        if (devices.isEmpty()) {
            return;
        }
        List<Long> adminIds = repository.liveAdminAccountIds();
        if (adminIds.isEmpty()) {
            return;
        }
        for (CalibrationRepository.DeviceDue device : devices) {
            for (Long adminId : adminIds) {
                notificationService.sendEvent(adminId, "calibration", "device_calibration",
                        "calibration:" + device.deviceNo() + ":" + device.calibrationDate(),
                        java.util.Map.of(
                                "deviceName", device.name(),
                                "deviceNo", device.deviceNo(),
                                "calibrationDate", device.calibrationDate()
                        ));
            }
        }
        log.info("设备校准提醒已发送：{} 台设备，通知 {} 位直播管理员", devices.size(), adminIds.size());
    }
}
