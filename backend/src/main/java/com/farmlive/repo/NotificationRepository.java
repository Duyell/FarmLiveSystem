package com.farmlive.repo;

import com.farmlive.common.PageResult;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 通知数据访问：站内与短信按渠道分别记状态；发送、重试、已读、查询。
 */
@Repository
public class NotificationRepository {
    private static final String VIEW_SELECT = """
            SELECT id, type, channel, title, content, status, retry_count, sent_at, read_at, created_at
            FROM notification
            """;

    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record NotificationRow(
            Long id,
            String type,
            String channel,
            String status,
            int retryCount
    ) {
    }

    private final RowMapper<Dtos.NotificationView> viewMapper = (rs, rowNum) -> new Dtos.NotificationView(
            rs.getLong("id"),
            rs.getString("type"),
            rs.getString("channel"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getString("status"),
            rs.getInt("retry_count"),
            rs.getTimestamp("sent_at") == null
                    ? null : rs.getTimestamp("sent_at").toLocalDateTime().format(QueueRepository.DATE_TIME),
            rs.getTimestamp("read_at") != null,
            rs.getTimestamp("created_at").toLocalDateTime().format(QueueRepository.DATE_TIME)
    );

    private final RowMapper<NotificationRow> rowMapper = (rs, rowNum) -> new NotificationRow(
            rs.getLong("id"),
            rs.getString("type"),
            rs.getString("channel"),
            rs.getString("status"),
            rs.getInt("retry_count")
    );

    public Long insert(Long accountId, String type, String channel, String title, String content) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO notification (account_id, type, channel, title, content, status, next_retry_at)
                    VALUES (?, ?, ?, ?, ?, 'pending', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, accountId);
            ps.setString(2, type);
            ps.setString(3, channel);
            ps.setString(4, title);
            ps.setString(5, content);
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void markSent(Long id) {
        jdbc.update("UPDATE notification SET status = 'sent', sent_at = NOW() WHERE id = ?", id);
    }

    /**
     * 发送失败：记录重试次数与下次重试时间；第三次失败置为最终失败。
     */
    public void markFailed(Long id, int newRetryCount) {
        if (newRetryCount >= 3) {
            jdbc.update("UPDATE notification SET status = 'final_failed', retry_count = ?, next_retry_at = NULL WHERE id = ?",
                    newRetryCount, id);
            return;
        }
        long minutes = newRetryCount == 1 ? 1 : (newRetryCount == 2 ? 5 : 15);
        jdbc.update("""
                UPDATE notification SET status = 'failed', retry_count = ?, next_retry_at = ?
                WHERE id = ?
                """, newRetryCount, Timestamp.valueOf(LocalDateTime.now().plusMinutes(minutes)), id);
    }

    public List<NotificationRow> dueForRetry() {
        return jdbc.query("""
                SELECT id, type, channel, status, retry_count FROM notification
                WHERE status = 'failed' AND channel = 'sms' AND next_retry_at IS NOT NULL AND next_retry_at <= NOW()
                """, rowMapper);
    }

    public Optional<NotificationRow> findRowById(Long id) {
        return jdbc.query("SELECT id, type, channel, status, retry_count FROM notification WHERE id = ?",
                rowMapper, id).stream().findFirst();
    }

    public PageResult<Dtos.NotificationView> listByAccount(Long accountId, String type, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE account_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(accountId);
        if (type != null && !type.isBlank()) {
            where.append(" AND type = ? ");
            args.add(type.trim());
        }
        return page(VIEW_SELECT + where + " ORDER BY id DESC", args, page, size);
    }

    public PageResult<Dtos.NotificationView> listAll(String type, String channel, String status, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        if (type != null && !type.isBlank()) {
            where.append(" AND type = ? ");
            args.add(type.trim());
        }
        if (channel != null && !channel.isBlank()) {
            where.append(" AND channel = ? ");
            args.add(channel.trim());
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ? ");
            args.add(status.trim());
        }
        return page(VIEW_SELECT + where + " ORDER BY id DESC", args, page, size);
    }

    public void markRead(Long id, Long accountId) {
        jdbc.update("UPDATE notification SET read_at = NOW() WHERE id = ? AND account_id = ? AND read_at IS NULL",
                id, accountId);
    }

    public Optional<Long> findAccountIdByFarmerId(Long farmerId) {
        if (farmerId == null) return Optional.empty();
        return jdbc.query("SELECT id FROM account WHERE farmer_id = ? LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"), farmerId).stream().findFirst();
    }

    public record EventTarget(
            Long accountId,
            String bookingNo,
            String topic,
            String startTime,
            String venueName,
            String farmerName,
            String deviceName
    ) {
    }

    /**
     * 开播前 1 小时提醒候选：已通过预约、即将开播、且尚未对该预约发过提醒。
     */
    public List<EventTarget> reminderCandidates() {
        return jdbc.query("""
                SELECT a.id AS account_id, b.booking_no, b.topic,
                       DATE_FORMAT(b.start_time, '%Y-%m-%d %H:%i') AS start_time,
                       v.name AS venue_name, f.name AS farmer_name, '' AS device_name
                FROM booking b
                JOIN venue v ON v.id = b.venue_id
                JOIN farmer f ON f.id = b.farmer_id
                JOIN account a ON a.farmer_id = f.id
                WHERE b.status = 'approved'
                  AND b.start_time BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 1 HOUR)
                  AND NOT EXISTS (
                    SELECT 1 FROM notification n
                    WHERE n.account_id = a.id AND n.type = 'reminder' AND n.content LIKE CONCAT('%', b.booking_no, '%')
                  )
                """, eventMapper);
    }

    /**
     * 设备故障受影响对象：该设备未来的已通过预约。
     */
    public List<EventTarget> deviceFaultTargets(Long deviceId) {
        return jdbc.query("""
                SELECT a.id AS account_id, b.booking_no, b.topic,
                       DATE_FORMAT(b.start_time, '%Y-%m-%d %H:%i') AS start_time,
                       v.name AS venue_name, f.name AS farmer_name, d.name AS device_name
                FROM booking b
                JOIN venue v ON v.id = b.venue_id
                JOIN farmer f ON f.id = b.farmer_id
                JOIN account a ON a.farmer_id = f.id
                JOIN device d ON d.id = b.device_id
                WHERE b.device_id = ? AND b.status = 'approved' AND b.start_time > NOW()
                """, eventMapper, deviceId);
    }

    private final RowMapper<EventTarget> eventMapper = (rs, rowNum) -> new EventTarget(
            rs.getLong("account_id"),
            rs.getString("booking_no"),
            rs.getString("topic"),
            rs.getString("start_time"),
            rs.getString("venue_name"),
            rs.getString("farmer_name"),
            rs.getString("device_name")
    );

    private PageResult<Dtos.NotificationView> page(String sql, List<Object> args, int page, int size) {
        String from = sql.substring(sql.indexOf("FROM notification"));
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Dtos.NotificationView> items = jdbc.query(sql + " LIMIT ? OFFSET ?", viewMapper, pageArgs.toArray());
        return new PageResult<>(items, total == null ? 0 : total, page, size);
    }
}
