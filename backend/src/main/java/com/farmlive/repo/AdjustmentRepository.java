package com.farmlive.repo;

import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 预约调整申请数据访问。
 */
@Repository
public class AdjustmentRepository {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;

    public AdjustmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String VIEW_SELECT = """
            SELECT a.id, a.request_no, a.booking_id, b.booking_no,
                   b.farmer_id, f.name AS farmer_name,
                   b.venue_id AS old_venue_id, v1.name AS old_venue_name,
                   b.start_time AS old_start_time, b.end_time AS old_end_time,
                   a.new_venue_id, v2.name AS new_venue_name,
                   a.new_device_id, d.device_no AS new_device_no, d.name AS new_device_name,
                   a.new_start_time, a.new_end_time, a.reason, a.status,
                   a.review_comment, a.created_at, a.reviewed_at
            FROM adjustment_request a
            JOIN booking b ON b.id = a.booking_id
            JOIN farmer f ON f.id = b.farmer_id
            JOIN venue v1 ON v1.id = b.venue_id
            JOIN venue v2 ON v2.id = a.new_venue_id
            JOIN device d ON d.id = a.new_device_id
            """;

    private final RowMapper<Dtos.AdjustmentView> mapper = (rs, rowNum) -> new Dtos.AdjustmentView(
            rs.getLong("id"),
            rs.getString("request_no"),
            rs.getLong("booking_id"),
            rs.getString("booking_no"),
            rs.getLong("farmer_id"),
            rs.getString("farmer_name"),
            rs.getLong("old_venue_id"),
            rs.getString("old_venue_name"),
            rs.getTimestamp("old_start_time").toLocalDateTime().format(DATE_TIME),
            rs.getTimestamp("old_end_time").toLocalDateTime().format(DATE_TIME),
            rs.getLong("new_venue_id"),
            rs.getString("new_venue_name"),
            rs.getLong("new_device_id"),
            rs.getString("new_device_no"),
            rs.getString("new_device_name"),
            rs.getTimestamp("new_start_time").toLocalDateTime().format(DATE_TIME),
            rs.getTimestamp("new_end_time").toLocalDateTime().format(DATE_TIME),
            rs.getString("reason"),
            rs.getString("status"),
            rs.getString("review_comment"),
            rs.getTimestamp("created_at").toLocalDateTime().format(DATE_TIME),
            rs.getTimestamp("reviewed_at") == null
                    ? null : rs.getTimestamp("reviewed_at").toLocalDateTime().format(DATE_TIME)
    );

    public Long insert(String requestNo, Long bookingId, Long newVenueId, Long newDeviceId,
                       LocalDateTime start, LocalDateTime end, String reason) {
        org.springframework.jdbc.support.KeyHolder keys =
                new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            java.sql.PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO adjustment_request
                    (request_no, booking_id, new_venue_id, new_device_id, new_start_time, new_end_time, reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, requestNo);
            ps.setLong(2, bookingId);
            ps.setLong(3, newVenueId);
            ps.setLong(4, newDeviceId);
            ps.setTimestamp(5, Timestamp.valueOf(start));
            ps.setTimestamp(6, Timestamp.valueOf(end));
            ps.setString(7, reason);
            return ps;
        }, keys);
        Number key = keys.getKey();
        return key == null ? null : key.longValue();
    }

    public Optional<Dtos.AdjustmentView> findViewById(Long id) {
        List<Dtos.AdjustmentView> list = jdbc.query(VIEW_SELECT + " WHERE a.id = ?", mapper, id);
        return list.stream().findFirst();
    }

    public PageResult<Dtos.AdjustmentView> listByFarmer(Long farmerId, String status, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE b.farmer_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(farmerId);
        if (status != null && !status.isBlank()) {
            where.append(" AND a.status = ? ");
            args.add(status.trim());
        }
        return page(where, args, page, size);
    }

    public PageResult<Dtos.AdjustmentView> listForReview(String status, String keyword, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" AND a.status = ? ");
            args.add(status.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (b.booking_no LIKE ? OR f.name LIKE ? OR a.request_no LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        return page(where, args, page, size);
    }

    public boolean hasPending(Long bookingId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM adjustment_request WHERE booking_id = ? AND status = 'pending'",
                Integer.class, bookingId);
        return count != null && count > 0;
    }

    public void updateStatus(Long id, String status, String comment, LocalDateTime reviewedAt) {
        jdbc.update("""
                UPDATE adjustment_request
                SET status = ?, review_comment = ?, reviewed_at = ? WHERE id = ?
                """, status, comment, reviewedAt == null ? null : Timestamp.valueOf(reviewedAt), id);
    }

    private PageResult<Dtos.AdjustmentView> page(StringBuilder where, List<Object> args, int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM adjustment_request a "
                + "JOIN booking b ON b.id = a.booking_id JOIN farmer f ON f.id = b.farmer_id"
                + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Dtos.AdjustmentView> items = jdbc.query(VIEW_SELECT + where
                + " ORDER BY a.id DESC LIMIT ? OFFSET ?", mapper, pageArgs.toArray());
        return new PageResult<>(items, total == null ? 0 : total, page, size);
    }
}
