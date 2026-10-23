package com.farmlive.repo;

import com.farmlive.dto.Dtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class DeviceFaultRepository {
    private final JdbcTemplate jdbc;

    public DeviceFaultRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<Dtos.DeviceFaultView> mapper = (rs, rowNum) -> new Dtos.DeviceFaultView(
            rs.getLong("id"),
            rs.getLong("device_id"),
            rs.getString("device_name"),
            rs.getString("fault_part"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getString("reported_by_name"),
            rs.getString("reported_at"),
            rs.getString("resolved_at"),
            rs.getString("resolution")
    );

    private static final String SELECT = """
            SELECT f.id, f.device_id, d.name AS device_name, f.fault_part, f.description,
                   f.status, a.display_name AS reported_by_name,
                   DATE_FORMAT(f.reported_at, '%Y-%m-%d %H:%i') AS reported_at,
                   DATE_FORMAT(f.resolved_at, '%Y-%m-%d %H:%i') AS resolved_at,
                   f.resolution
            FROM device_fault f
            JOIN device d ON d.id = f.device_id
            JOIN account a ON a.id = f.reported_by
            """;

    public List<Dtos.DeviceFaultView> listByDevice(Long deviceId) {
        return jdbc.query(SELECT + " WHERE f.device_id = ? ORDER BY f.reported_at DESC, f.id DESC",
                mapper, deviceId);
    }

    public Optional<Dtos.DeviceFaultView> findById(Long id) {
        return jdbc.query(SELECT + " WHERE f.id = ?", mapper, id).stream().findFirst();
    }

    public boolean hasActive(Long deviceId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM device_fault
                WHERE device_id = ? AND status IN ('reported','repairing')
                """, Long.class, deviceId);
        return count != null && count > 0;
    }

    public Long create(Long deviceId, String faultPart, String description, Long reportedBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO device_fault (device_id, fault_part, description, status, reported_by)
                    VALUES (?, ?, ?, 'reported', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, deviceId);
            ps.setString(2, faultPart);
            ps.setString(3, description);
            ps.setLong(4, reportedBy);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void resolve(Long id, String resolution) {
        jdbc.update("""
                UPDATE device_fault
                SET status = 'resolved', resolved_at = ?, resolution = ?
                WHERE id = ? AND status IN ('reported','repairing')
                """, Timestamp.valueOf(LocalDateTime.now()), resolution, id);
    }
}
