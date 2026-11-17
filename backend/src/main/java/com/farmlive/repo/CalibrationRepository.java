package com.farmlive.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 设备校准提醒数据访问。
 */
@Repository
public class CalibrationRepository {
    private final JdbcTemplate jdbc;

    public CalibrationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record DeviceDue(Long id, String deviceNo, String name, String calibrationDate) {
    }

    /**
     * 7 天内到达校准周期且尚未提醒过的可用设备。
     */
    public List<DeviceDue> devicesDue() {
        return jdbc.query("""
                SELECT d.id, d.device_no, d.name, DATE_FORMAT(d.calibration_date, '%Y-%m-%d') AS calibration_date
                FROM device d
                WHERE d.status = 'available'
                  AND d.calibration_date IS NOT NULL
                  AND d.calibration_date BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 7 DAY)
                  AND NOT EXISTS (
                    SELECT 1 FROM notification n
                    WHERE n.type = 'calibration'
                      AND n.content LIKE CONCAT('%', d.device_no, '%')
                      AND n.content LIKE CONCAT('%', DATE_FORMAT(d.calibration_date, '%Y-%m-%d'), '%')
                  )
                ORDER BY d.calibration_date
                """, (rs, rowNum) -> new DeviceDue(
                rs.getLong("id"),
                rs.getString("device_no"),
                rs.getString("name"),
                rs.getString("calibration_date")
        ));
    }

    public List<Long> liveAdminAccountIds() {
        return jdbc.queryForList("""
                SELECT a.id FROM account a
                JOIN role r ON r.id = a.role_id
                WHERE r.code = 'live_admin' AND a.status = 'active'
                """, Long.class);
    }
}
