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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 直播销售记录数据访问。只允许对已完成场次录入。
 */
@Repository
public class SalesRepository {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;

    public SalesRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String VIEW_SELECT = """
            SELECT s.id, s.booking_id, b.booking_no, f.name AS farmer_name,
                   v.name AS venue_name, b.start_time,
                   s.product_name, s.spec, s.quantity, s.unit, s.created_at, s.updated_at
            FROM sales_record s
            JOIN booking b ON b.id = s.booking_id
            JOIN farmer f ON f.id = b.farmer_id
            JOIN venue v ON v.id = b.venue_id
            """;

    private final RowMapper<Dtos.SalesView> mapper = (rs, rowNum) -> new Dtos.SalesView(
            rs.getLong("id"),
            rs.getLong("booking_id"),
            rs.getString("booking_no"),
            rs.getString("farmer_name"),
            rs.getString("venue_name"),
            rs.getTimestamp("start_time").toLocalDateTime().format(DATE_TIME),
            rs.getString("product_name"),
            rs.getString("spec"),
            rs.getDouble("quantity"),
            rs.getString("unit"),
            rs.getTimestamp("created_at").toLocalDateTime().format(DATE_TIME),
            rs.getTimestamp("updated_at").toLocalDateTime().format(DATE_TIME)
    );

    public Long insert(Long bookingId, String productName, String spec,
                       double quantity, String unit, Long createdBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO sales_record (booking_id, product_name, spec, quantity, unit, created_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, bookingId);
            ps.setString(2, productName);
            ps.setString(3, spec);
            ps.setDouble(4, quantity);
            ps.setString(5, unit);
            ps.setLong(6, createdBy);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void update(Long id, String productName, String spec, double quantity, String unit) {
        jdbc.update("""
                UPDATE sales_record SET product_name = ?, spec = ?, quantity = ?, unit = ?
                WHERE id = ?
                """, productName, spec, quantity, unit, id);
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM sales_record WHERE id = ?", id);
    }

    public Optional<Dtos.SalesView> findById(Long id) {
        return jdbc.query(VIEW_SELECT + " WHERE s.id = ?", mapper, id).stream().findFirst();
    }

    public PageResult<Dtos.SalesView> page(Long bookingId, String keyword, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        if (bookingId != null) {
            where.append(" AND s.booking_id = ? ");
            args.add(bookingId);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (s.product_name LIKE ? OR b.booking_no LIKE ? OR f.name LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM sales_record s "
                + "JOIN booking b ON b.id = s.booking_id JOIN farmer f ON f.id = b.farmer_id"
                + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Dtos.SalesView> items = jdbc.query(VIEW_SELECT + where
                + " ORDER BY s.id DESC LIMIT ? OFFSET ?", mapper, pageArgs.toArray());
        return new PageResult<>(items, total == null ? 0 : total, page, size);
    }
}
