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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 预约与资源占用数据访问。
 * 资源锁按小时唯一（场地、设备各一把），提交与审核通过时写入，取消时释放。
 */
@Repository
public class BookingRepository {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;

    public BookingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record BookingRow(
            Long id,
            String bookingNo,
            Long farmerId,
            Long venueId,
            Long deviceId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String topic,
            String status
    ) {
    }

    private static final String VIEW_SELECT = """
            SELECT b.id, b.booking_no, b.farmer_id, f.name AS farmer_name,
                   b.venue_id, v.name AS venue_name,
                   b.device_id, d.device_no, d.name AS device_name,
                   b.start_time, b.end_time, b.topic, b.status,
                   b.review_comment, b.recommended_time, b.cancel_reason, b.created_at
            FROM booking b
            JOIN farmer f ON f.id = b.farmer_id
            JOIN venue v ON v.id = b.venue_id
            LEFT JOIN device d ON d.id = b.device_id
            """;

    private final RowMapper<Dtos.BookingView> viewMapper = (rs, rowNum) -> new Dtos.BookingView(
            rs.getLong("id"),
            rs.getString("booking_no"),
            rs.getLong("farmer_id"),
            rs.getString("farmer_name"),
            rs.getLong("venue_id"),
            rs.getString("venue_name"),
            rs.getObject("device_id", Long.class),
            rs.getString("device_no"),
            rs.getString("device_name"),
            rs.getTimestamp("start_time").toLocalDateTime().format(DATE_TIME),
            rs.getTimestamp("end_time").toLocalDateTime().format(DATE_TIME),
            rs.getString("topic"),
            rs.getString("status"),
            rs.getString("review_comment"),
            rs.getTimestamp("recommended_time") == null
                    ? null : rs.getTimestamp("recommended_time").toLocalDateTime().format(DATE_TIME),
            rs.getString("cancel_reason"),
            List.of(),
            rs.getTimestamp("created_at").toLocalDateTime().format(DATE_TIME)
    );

    private final RowMapper<BookingRow> rowMapper = (rs, rowNum) -> new BookingRow(
            rs.getLong("id"),
            rs.getString("booking_no"),
            rs.getLong("farmer_id"),
            rs.getLong("venue_id"),
            rs.getObject("device_id", Long.class),
            rs.getTimestamp("start_time").toLocalDateTime(),
            rs.getTimestamp("end_time").toLocalDateTime(),
            rs.getString("topic"),
            rs.getString("status")
    );

    // ---------------- 写入 ----------------

    public Long insertBooking(String bookingNo, Long farmerId, Long venueId, Long deviceId,
                              LocalDateTime startTime, LocalDateTime endTime, String topic) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO booking (booking_no, farmer_id, venue_id, device_id, start_time, end_time, topic)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, bookingNo);
            ps.setLong(2, farmerId);
            ps.setLong(3, venueId);
            ps.setObject(4, deviceId);
            ps.setTimestamp(5, Timestamp.valueOf(startTime));
            ps.setTimestamp(6, Timestamp.valueOf(endTime));
            ps.setString(7, topic);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void insertProduct(Long bookingId, Dtos.BookingProductItem item) {
        jdbc.update("""
                INSERT INTO booking_product (booking_id, product_name, spec, quantity, unit)
                VALUES (?, ?, ?, ?, ?)
                """, bookingId, item.productName().trim(),
                item.spec() == null || item.spec().isBlank() ? null : item.spec().trim(),
                item.quantity() == null ? 1 : item.quantity(),
                item.unit() == null || item.unit().isBlank() ? "份" : item.unit().trim());
    }

    /**
     * 写入小时级资源锁；场地或设备任一时段已占用会触发唯一键冲突。
     */
    public void insertLock(Long venueId, Long deviceId, LocalDateTime slotTime, Long bookingId) {
        jdbc.update("""
                INSERT INTO resource_lock (venue_id, device_id, slot_time, booking_id)
                VALUES (?, ?, ?, ?)
                """, venueId, deviceId, Timestamp.valueOf(slotTime), bookingId);
    }

    public void deleteLocks(Long bookingId) {
        jdbc.update("DELETE FROM resource_lock WHERE booking_id = ?", bookingId);
    }

    public void updateReview(Long id, String status, String comment, LocalDateTime recommendedTime) {
        jdbc.update("""
                UPDATE booking SET status = ?, review_comment = ?, recommended_time = ? WHERE id = ?
                """, status, comment,
                recommendedTime == null ? null : Timestamp.valueOf(recommendedTime), id);
    }

    public void updateCancel(Long id, String reason) {
        jdbc.update("UPDATE booking SET status = 'canceled', cancel_reason = ? WHERE id = ?",
                reason, id);
    }

    public void updateStatus(Long id, String status) {
        jdbc.update("UPDATE booking SET status = ? WHERE id = ?", status, id);
    }

    /**
     * 候补确认时复用排队创建时的 queued 预约，补上分配的设备。
     */
    public void confirmQueuedBooking(Long id, Long deviceId) {
        jdbc.update("""
                UPDATE booking SET device_id = ?, status = 'approved'
                WHERE id = ? AND status = 'queued'
                """, deviceId, id);
    }

    public void closeQueuedBooking(Long id, String status, String reason) {
        jdbc.update("""
                UPDATE booking SET status = ?, cancel_reason = ?
                WHERE id = ? AND status = 'queued'
                """, status, reason, id);
    }

    public void updateResources(Long id, Long venueId, Long deviceId,
                                LocalDateTime startTime, LocalDateTime endTime) {
        jdbc.update("""
                UPDATE booking SET venue_id = ?, device_id = ?, start_time = ?, end_time = ?
                WHERE id = ?
                """, venueId, deviceId, Timestamp.valueOf(startTime), Timestamp.valueOf(endTime), id);
    }

    // ---------------- 查询 ----------------

    public Optional<BookingRow> findRowById(Long id) {
        return jdbc.query("""
                SELECT id, booking_no, farmer_id, venue_id, device_id, start_time, end_time, topic, status
                FROM booking WHERE id = ?
                """, rowMapper, id).stream().findFirst();
    }

    public List<BookingRow> approvedFutureByDevice(Long deviceId) {
        return jdbc.query("""
                SELECT id, booking_no, farmer_id, venue_id, device_id, start_time, end_time, topic, status
                FROM booking
                WHERE device_id = ? AND status = 'approved' AND start_time > NOW()
                """, rowMapper, deviceId);
    }

    public Optional<Dtos.BookingView> findViewById(Long id) {
        List<Dtos.BookingView> list = jdbc.query(VIEW_SELECT + " WHERE b.id = ?", viewMapper, id);
        if (list.isEmpty()) return Optional.empty();
        attachProducts(list);
        return Optional.of(list.get(0));
    }

    public PageResult<Dtos.BookingView> listByFarmer(Long farmerId, String status, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE b.farmer_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(farmerId);
        if (status != null && !status.isBlank()) {
            where.append(" AND b.status = ? ");
            args.add(status.trim());
        }
        return page(VIEW_SELECT + where + " ORDER BY b.id DESC", args, page, size);
    }

    public PageResult<Dtos.BookingView> listForReview(String status, String keyword, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            where.append(" AND b.status = ? ");
            args.add(status.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (b.booking_no LIKE ? OR b.topic LIKE ? OR f.name LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            for (int i = 0; i < 3; i++) args.add(like);
        }
        return page(VIEW_SELECT + where + " ORDER BY b.id DESC", args, page, size);
    }

    /**
     * 同一农户在时段内有待审核或已通过的预约视为重复。
     */
    public boolean farmerHasOverlap(Long farmerId, LocalDateTime start, LocalDateTime end) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM booking
                WHERE farmer_id = ? AND status IN ('pending_review', 'approved')
                  AND start_time < ? AND end_time > ?
                """, Long.class, farmerId, Timestamp.valueOf(end), Timestamp.valueOf(start));
        return count != null && count > 0;
    }

    // ---------------- 可用资源 ----------------

    public boolean venueAvailable(Long venueId) {
        if (venueId == null) return false;
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM venue WHERE id = ? AND status = 'available'",
                Integer.class, venueId);
        return count != null && count > 0;
    }

    public boolean deviceAvailableInVenue(Long deviceId, Long venueId) {
        if (deviceId == null || venueId == null) return false;
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM device WHERE id = ? AND venue_id = ? AND status = 'available'
                """, Integer.class, deviceId, venueId);
        return count != null && count > 0;
    }

    public List<Dtos.AvailableVenueView> listAvailableVenues(String countyCode,
                                                             List<Long> busyVenueIds,
                                                             List<Long> busyDeviceIds) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, venue_no, name, county_code,
                       DATE_FORMAT(open_start, '%H:%i') AS open_start,
                       DATE_FORMAT(open_end, '%H:%i') AS open_end
                FROM venue
                WHERE status = 'available'
                """);
        List<Object> args = new ArrayList<>();
        if (countyCode != null && !countyCode.isBlank()) {
            sql.append(" AND county_code = ? ");
            args.add(countyCode.trim());
        }
        if (!busyVenueIds.isEmpty()) {
            sql.append(" AND id NOT IN (").append(
                    busyVenueIds.stream().map(id -> "?").collect(Collectors.joining(","))).append(") ");
            busyVenueIds.forEach(args::add);
        }
        sql.append(" ORDER BY id ");
        List<Dtos.AvailableVenueView> venues = jdbc.query(sql.toString(), (rs, rowNum) ->
                new Dtos.AvailableVenueView(rs.getLong("id"), rs.getString("venue_no"),
                        rs.getString("name"), rs.getString("county_code"),
                        rs.getString("open_start"), rs.getString("open_end"), List.of()), args.toArray());
        if (venues.isEmpty()) return venues;
        List<Long> ids = venues.stream().map(Dtos.AvailableVenueView::venueId).toList();
        String in = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> deviceArgs = new ArrayList<>(ids);
        String deviceFilter = busyDeviceIds.isEmpty() ? "" : " AND id NOT IN (" +
                busyDeviceIds.stream().map(id -> "?").collect(Collectors.joining(",")) + ")";
        busyDeviceIds.forEach(deviceArgs::add);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                        SELECT id, venue_id, device_no, name, config FROM device
                        WHERE status = 'available' AND venue_id IN (""" + in + ")" + deviceFilter +
                        " ORDER BY id", deviceArgs.toArray());
        Map<Long, List<Dtos.AvailableDeviceView>> byVenue = rows.stream().collect(Collectors.groupingBy(
                r -> ((Number) r.get("venue_id")).longValue(),
                Collectors.mapping(r -> new Dtos.AvailableDeviceView(
                        ((Number) r.get("id")).longValue(),
                        (String) r.get("device_no"),
                        (String) r.get("name"),
                        (String) r.get("config")), Collectors.toList())));
        return venues.stream()
                .map(v -> new Dtos.AvailableVenueView(v.venueId(), v.venueNo(), v.venueName(),
                        v.countyCode(), v.openStart(), v.openEnd(),
                        byVenue.getOrDefault(v.venueId(), List.of())))
                .filter(v -> !v.devices().isEmpty())
                .toList();
    }

    /**
     * 时段内已有有效预约（待审核或已通过）的场地。
     */
    public List<Long> busyVenueIds(LocalDateTime start, LocalDateTime end) {
        return jdbc.queryForList("""
                        SELECT DISTINCT venue_id FROM booking
                        WHERE status IN ('pending_review', 'approved') AND start_time < ? AND end_time > ?
                        """, Long.class, Timestamp.valueOf(end), Timestamp.valueOf(start));
    }

    public List<Long> busyDeviceIds(LocalDateTime start, LocalDateTime end) {
        return jdbc.queryForList("""
                        SELECT DISTINCT device_id FROM booking
                        WHERE status IN ('pending_review', 'approved') AND start_time < ? AND end_time > ?
                        """, Long.class, Timestamp.valueOf(end), Timestamp.valueOf(start));
    }

    /**
     * 提交时的场地冲突：时段内有同场地的待审核或已通过预约。
     */
    public boolean venueHasOverlap(Long venueId, LocalDateTime start, LocalDateTime end) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM booking
                WHERE venue_id = ? AND status IN ('pending_review', 'approved')
                  AND start_time < ? AND end_time > ?
                """, Integer.class, venueId, Timestamp.valueOf(end), Timestamp.valueOf(start));
        return count != null && count > 0;
    }

    public boolean deviceHasOverlap(Long deviceId, LocalDateTime start, LocalDateTime end) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM booking
                WHERE device_id = ? AND status IN ('pending_review', 'approved')
                  AND start_time < ? AND end_time > ?
                """, Integer.class, deviceId, Timestamp.valueOf(end), Timestamp.valueOf(start));
        return count != null && count > 0;
    }

    // ---------------- 内部 ----------------

    private PageResult<Dtos.BookingView> page(String sql, List<Object> args, int page, int size) {
        String from = sql.substring(sql.indexOf("FROM booking"));
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Dtos.BookingView> items = jdbc.query(
                sql + " LIMIT ? OFFSET ?", viewMapper, pageArgs.toArray());
        attachProducts(items);
        return new PageResult<>(items, total == null ? 0 : total, page, size);
    }

    private void attachProducts(List<Dtos.BookingView> items) {
        if (items.isEmpty()) return;
        String in = items.stream().map(b -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>(items.stream().map(Dtos.BookingView::id).toList());
        Map<Long, List<Dtos.BookingProductView>> byBooking = jdbc.query(
                "SELECT booking_id, product_name, spec, quantity, unit FROM booking_product WHERE booking_id IN (" + in + ")",
                (rs, rowNum) -> new Object[]{
                        rs.getLong("booking_id"),
                        new Dtos.BookingProductView(rs.getString("product_name"), rs.getString("spec"),
                                rs.getDouble("quantity"), rs.getString("unit"))
                }, args.toArray()).stream().collect(Collectors.groupingBy(
                pair -> (Long) pair[0],
                Collectors.mapping(pair -> (Dtos.BookingProductView) pair[1], Collectors.toList())));
        for (int i = 0; i < items.size(); i++) {
            Dtos.BookingView item = items.get(i);
            items.set(i, new Dtos.BookingView(item.id(), item.bookingNo(), item.farmerId(), item.farmerName(),
                    item.venueId(), item.venueName(), item.deviceId(), item.deviceNo(), item.deviceName(),
                    item.startTime(), item.endTime(), item.topic(), item.status(),
                    item.reviewComment(), item.recommendedTime(), item.cancelReason(),
                    byBooking.getOrDefault(item.id(), List.of()), item.createdAt()));
        }
    }
}
