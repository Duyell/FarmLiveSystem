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

/**
 * 排队数据访问。同一"场地 + 时段"为一个队列组，顺位按加入顺序维护。
 */
@Repository
public class QueueRepository {
    public static final java.time.format.DateTimeFormatter DATE_TIME =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;

    public QueueRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record QueueRow(
            Long id,
            String queueNo,
            Long farmerId,
            Long bookingId,
            Long venueId,
            LocalDateTime slotStart,
            LocalDateTime slotEnd,
            int position,
            String status,
            LocalDateTime candidateDeadline
    ) {
    }

    public record QueueGroup(Long venueId, LocalDateTime slotStart) {
    }

    private static final String VIEW_SELECT = """
            SELECT q.id, q.queue_no, q.farmer_id, f.name AS farmer_name, f.farmer_no,
                   q.booking_id, rb.booking_no AS request_booking_no,
                   q.venue_id, v.name AS venue_name,
                   q.slot_start, q.slot_end, q.position, q.status,
                   q.candidate_deadline, q.assigned_device_id, d.name AS device_name,
                   q.confirmed_booking_id, q.created_at
            FROM queue_ticket q
            JOIN farmer f ON f.id = q.farmer_id
            JOIN venue v ON v.id = q.venue_id
            LEFT JOIN booking rb ON rb.id = q.booking_id
            LEFT JOIN device d ON d.id = q.assigned_device_id
            """;

    private final RowMapper<Dtos.QueueView> viewMapper = (rs, rowNum) -> new Dtos.QueueView(
            rs.getLong("id"),
            rs.getString("queue_no"),
            rs.getLong("farmer_id"),
            rs.getString("farmer_name"),
            rs.getObject("booking_id", Long.class),
            rs.getString("request_booking_no"),
            rs.getLong("venue_id"),
            rs.getString("venue_name"),
            rs.getTimestamp("slot_start").toLocalDateTime().format(DATE_TIME),
            rs.getTimestamp("slot_end").toLocalDateTime().format(DATE_TIME),
            rs.getInt("position"),
            0,
            0,
            rs.getString("status"),
            rs.getTimestamp("candidate_deadline") == null
                    ? null : rs.getTimestamp("candidate_deadline").toLocalDateTime().format(DATE_TIME),
            rs.getObject("assigned_device_id", Long.class),
            rs.getString("device_name"),
            rs.getObject("confirmed_booking_id", Long.class),
            rs.getTimestamp("created_at").toLocalDateTime().format(DATE_TIME)
    );

    private final RowMapper<QueueRow> rowMapper = (rs, rowNum) -> new QueueRow(
            rs.getLong("id"),
            rs.getString("queue_no"),
            rs.getLong("farmer_id"),
            rs.getObject("booking_id", Long.class),
            rs.getLong("venue_id"),
            rs.getTimestamp("slot_start").toLocalDateTime(),
            rs.getTimestamp("slot_end").toLocalDateTime(),
            rs.getInt("position"),
            rs.getString("status"),
            rs.getTimestamp("candidate_deadline") == null
                    ? null : rs.getTimestamp("candidate_deadline").toLocalDateTime()
    );

    // ---------------- 写入 ----------------

    public Long insert(Long farmerId, Long venueId, String queueNo,
                       LocalDateTime slotStart, LocalDateTime slotEnd, int position, Long bookingId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO queue_ticket (queue_no, farmer_id, booking_id, venue_id, slot_start, slot_end, position)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, queueNo);
            ps.setLong(2, farmerId);
            ps.setObject(3, bookingId);
            ps.setLong(4, venueId);
            ps.setTimestamp(5, Timestamp.valueOf(slotStart));
            ps.setTimestamp(6, Timestamp.valueOf(slotEnd));
            ps.setInt(7, position);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void promoteToCandidate(Long id, LocalDateTime deadline) {
        jdbc.update("UPDATE queue_ticket SET status = 'candidate', candidate_deadline = ? WHERE id = ?",
                Timestamp.valueOf(deadline), id);
    }

    public void markConfirmed(Long id, Long deviceId, Long bookingId) {
        jdbc.update("""
                UPDATE queue_ticket SET status = 'confirmed', assigned_device_id = ?, confirmed_booking_id = ?
                WHERE id = ?
                """, deviceId, bookingId, id);
    }

    public void updateStatus(Long id, String status) {
        jdbc.update("UPDATE queue_ticket SET status = ? WHERE id = ?", status, id);
    }

    /**
     * 移除一个成员后，同队列组后续顺位前移。
     */
    public void shiftPositions(Long venueId, LocalDateTime slotStart, int removedPosition) {
        jdbc.update("""
                UPDATE queue_ticket SET position = position - 1
                WHERE venue_id = ? AND slot_start = ? AND status IN ('queued', 'candidate') AND position > ?
                """, venueId, Timestamp.valueOf(slotStart), removedPosition);
    }

    public void closeGroup(Long venueId, LocalDateTime slotStart) {
        jdbc.update("""
                UPDATE queue_ticket SET status = 'closed'
                WHERE venue_id = ? AND slot_start = ? AND status IN ('queued', 'candidate')
                """, venueId, Timestamp.valueOf(slotStart));
    }

    public void closeQueuedBookings(Long venueId, LocalDateTime slotStart, String status, String reason) {
        jdbc.update("""
                UPDATE booking b
                JOIN queue_ticket q ON q.booking_id = b.id
                SET b.status = ?, b.cancel_reason = ?
                WHERE q.venue_id = ? AND q.slot_start = ?
                  AND q.status IN ('queued', 'candidate')
                  AND b.status = 'queued'
                """, status, reason, venueId, Timestamp.valueOf(slotStart));
    }

    /**
     * 到开播前 24 小时仍未转正的队列组，统一关闭。
     */
    public List<QueueGroup> groupsToCloseAtDeadline() {
        return jdbc.query("""
                SELECT venue_id, slot_start
                FROM queue_ticket
                WHERE status IN ('queued', 'candidate')
                  AND slot_start <= DATE_ADD(NOW(), INTERVAL 24 HOUR)
                GROUP BY venue_id, slot_start
                """, (rs, rowNum) -> new QueueGroup(
                rs.getLong("venue_id"),
                rs.getTimestamp("slot_start").toLocalDateTime()
        ));
    }

    // ---------------- 查询 ----------------

    public Optional<QueueRow> findRowById(Long id) {
        return jdbc.query("""
                SELECT id, queue_no, farmer_id, booking_id, venue_id, slot_start, slot_end, position, status, candidate_deadline
                FROM queue_ticket WHERE id = ?
                """, rowMapper, id).stream().findFirst();
    }

    public Optional<Dtos.QueueView> findViewById(Long id) {
        List<Dtos.QueueView> list = jdbc.query(VIEW_SELECT + " WHERE q.id = ?", viewMapper, id);
        if (list.isEmpty()) return Optional.empty();
        fillTotals(list);
        return Optional.of(list.get(0));
    }

    public Optional<String> farmerNoById(Long farmerId) {
        return jdbc.query("SELECT farmer_no FROM farmer WHERE id = ?",
                (rs, rowNum) -> rs.getString("farmer_no"), farmerId).stream().findFirst();
    }

    public boolean farmerHasActiveInSlot(Long farmerId, LocalDateTime slotStart) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM queue_ticket
                WHERE farmer_id = ? AND status IN ('queued', 'candidate') AND slot_start = ?
                """, Integer.class, farmerId, Timestamp.valueOf(slotStart));
        return count != null && count > 0;
    }

    /**
     * 72 小时冷却：候补确认后预约又被取消，72 小时内不能对该场地排队。
     */
    public boolean farmerCooledDown(Long farmerId, Long venueId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM queue_ticket qt
                JOIN booking b ON b.id = qt.confirmed_booking_id
                WHERE qt.farmer_id = ? AND qt.venue_id = ? AND b.status = 'canceled'
                  AND b.updated_at > NOW() - INTERVAL 72 HOUR
                """, Integer.class, farmerId, venueId);
        return count != null && count > 0;
    }

    /**
     * 时段在该场地上的有效预约归属（待审核或已通过），用于判断时段是否被占用、是否被自己占用。
     */
    public List<Long> slotOccupierIds(Long venueId, LocalDateTime slotStart, LocalDateTime slotEnd) {
        return jdbc.queryForList("""
                SELECT DISTINCT farmer_id FROM booking
                WHERE venue_id = ? AND status IN ('pending_review', 'approved')
                  AND start_time < ? AND end_time > ?
                """, Long.class, venueId, Timestamp.valueOf(slotEnd), Timestamp.valueOf(slotStart));
    }

    public int activeCountForSlot(Long venueId, LocalDateTime slotStart) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM queue_ticket
                WHERE venue_id = ? AND slot_start = ? AND status IN ('queued', 'candidate')
                """, Integer.class, venueId, Timestamp.valueOf(slotStart));
        return count == null ? 0 : count;
    }

    public int nextPosition(Long venueId, LocalDateTime slotStart) {
        Integer max = jdbc.queryForObject("""
                SELECT COALESCE(MAX(position), 0) FROM queue_ticket
                WHERE venue_id = ? AND slot_start = ? AND status IN ('queued', 'candidate')
                """, Integer.class, venueId, Timestamp.valueOf(slotStart));
        return (max == null ? 0 : max) + 1;
    }

    public int activeTotal(Long venueId, LocalDateTime slotStart) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM queue_ticket
                WHERE venue_id = ? AND slot_start = ? AND status IN ('queued', 'candidate')
                """, Integer.class, venueId, Timestamp.valueOf(slotStart));
        return count == null ? 0 : count;
    }

    public int countTodayForFarmer(Long farmerId, LocalDateTime slotStart) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM queue_ticket
                WHERE farmer_id = ? AND DATE(slot_start) = DATE(?)
                """, Integer.class, farmerId, Timestamp.valueOf(slotStart));
        return count == null ? 0 : count;
    }

    public Optional<QueueRow> firstQueued(Long venueId, LocalDateTime slotStart) {
        return jdbc.query("""
                SELECT id, queue_no, farmer_id, booking_id, venue_id, slot_start, slot_end, position, status, candidate_deadline
                FROM queue_ticket
                WHERE venue_id = ? AND slot_start = ? AND status = 'queued'
                ORDER BY position LIMIT 1
                """, rowMapper, venueId, Timestamp.valueOf(slotStart)).stream().findFirst();
    }

    public List<Long> activeGroupIds(Long venueId, LocalDateTime slotStart) {
        return jdbc.queryForList("""
                SELECT id FROM queue_ticket
                WHERE venue_id = ? AND slot_start = ? AND status IN ('queued', 'candidate')
                """, Long.class, venueId, Timestamp.valueOf(slotStart));
    }

    public List<Dtos.QueueView> listByFarmer(Long farmerId) {
        List<Dtos.QueueView> list = jdbc.query(
                VIEW_SELECT + " WHERE q.farmer_id = ? ORDER BY q.slot_start DESC, q.position",
                viewMapper, farmerId);
        fillTotals(list);
        return list;
    }

    public List<Dtos.QueueView> listForLive(Long venueId, LocalDateTime dateFrom, LocalDateTime dateTo) {
        StringBuilder sql = new StringBuilder(VIEW_SELECT + " WHERE q.status IN ('queued','candidate','confirmed') ");
        List<Object> args = new java.util.ArrayList<>();
        if (venueId != null) {
            sql.append(" AND q.venue_id = ? ");
            args.add(venueId);
        }
        if (dateFrom != null && dateTo != null) {
            sql.append(" AND q.slot_start >= ? AND q.slot_start < ? ");
            args.add(Timestamp.valueOf(dateFrom));
            args.add(Timestamp.valueOf(dateTo));
        }
        sql.append(" ORDER BY q.slot_start, q.position ");
        List<Dtos.QueueView> list = jdbc.query(sql.toString(), viewMapper, args.toArray());
        fillTotals(list);
        return list;
    }

    public List<QueueRow> expiredCandidates() {
        return jdbc.query("""
                SELECT id, queue_no, farmer_id, booking_id, venue_id, slot_start, slot_end, position, status, candidate_deadline
                FROM queue_ticket
                WHERE status = 'candidate' AND candidate_deadline < NOW()
                """, rowMapper);
    }

    /**
     * 时段内可用设备：状态可用且未被有效预约占用。
     */
    public Optional<Long> pickAvailableDevice(Long venueId, LocalDateTime slotStart, LocalDateTime slotEnd) {
        List<Long> ids = jdbc.queryForList("""
                SELECT d.id FROM device d
                WHERE d.venue_id = ? AND d.status = 'available'
                  AND NOT EXISTS (
                    SELECT 1 FROM booking b
                    WHERE b.device_id = d.id AND b.status IN ('pending_review', 'approved')
                      AND b.start_time < ? AND b.end_time > ?
                  )
                ORDER BY d.id LIMIT 1
                """, Long.class, venueId, Timestamp.valueOf(slotEnd), Timestamp.valueOf(slotStart));
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    private void fillTotals(List<Dtos.QueueView> list) {
        for (int i = 0; i < list.size(); i++) {
            Dtos.QueueView item = list.get(i);
            LocalDateTime start = LocalDateTime.parse(item.slotStart(), DATE_TIME);
            int total = activeTotal(item.venueId(), start);
            list.set(i, new Dtos.QueueView(item.id(), item.queueNo(), item.farmerId(), item.farmerName(),
                    item.bookingId(), item.bookingNo(),
                    item.venueId(), item.venueName(), item.slotStart(), item.slotEnd(),
                    item.position(), total, Math.max(0, item.position() - 1), item.status(),
                    item.candidateDeadline(), item.assignedDeviceId(), item.assignedDeviceName(),
                    item.confirmedBookingId(), item.createdAt()));
        }
    }
}
