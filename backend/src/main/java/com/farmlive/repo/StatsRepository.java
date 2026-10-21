package com.farmlive.repo;

import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 统计查询：四项指标、完成场次明细、趋势和导出数据。
 */
@Repository
public class StatsRepository {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;

    public StatsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Filter(LocalDateTime from, LocalDateTime to, String countyCode,
                         Long venueId, String productName) {
    }

    private final RowMapper<Dtos.StatsDetailView> detailMapper = (rs, rowNum) -> new Dtos.StatsDetailView(
            rs.getLong("booking_id"),
            rs.getString("booking_no"),
            rs.getString("farmer_name"),
            rs.getString("venue_name"),
            rs.getString("county_code"),
            rs.getTimestamp("start_time").toLocalDateTime().format(DATE_TIME),
            rs.getString("topic"),
            rs.getDouble("sold_quantity"),
            rs.getLong("sales_count")
    );

    public long count(Filter filter, String statuses) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, args);
        String sql = "SELECT COUNT(*) FROM booking b JOIN venue v ON v.id = b.venue_id "
                + where + " AND b.status IN (" + statuses + ")";
        Long value = jdbc.queryForObject(sql, Long.class, args.toArray());
        return value == null ? 0 : value;
    }

    public long countAll(Filter filter) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, args);
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM booking b "
                + "JOIN venue v ON v.id = b.venue_id " + where, Long.class, args.toArray());
        return value == null ? 0 : value;
    }

    public double bookedHours(Filter filter) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, args);
        Double value = jdbc.queryForObject("SELECT COALESCE(SUM(TIMESTAMPDIFF(HOUR, b.start_time, b.end_time)),0) "
                + "FROM booking b JOIN venue v ON v.id = b.venue_id "
                + where + " AND b.status IN ('approved','completed')", Double.class, args.toArray());
        return value == null ? 0 : value;
    }

    public double soldQuantity(Filter filter) {
        List<Object> args = new ArrayList<>();
        String where = where(filter, args);
        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(s.quantity),0) FROM sales_record s "
                + "JOIN booking b ON b.id = s.booking_id JOIN venue v ON v.id = b.venue_id " + where);
        if (filter.productName() != null && !filter.productName().isBlank()) {
            sql.append(" AND s.product_name = ?");
            args.add(filter.productName().trim());
        }
        Double value = jdbc.queryForObject(sql.toString(), Double.class, args.toArray());
        return value == null ? 0 : value;
    }

    public double venueCapacityHours(Filter filter, long days) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT open_start, open_end FROM venue WHERE status = 'available'");
        if (filter.countyCode() != null && !filter.countyCode().isBlank()) {
            sql.append(" AND county_code = ?");
            args.add(filter.countyCode().trim());
        }
        if (filter.venueId() != null) {
            sql.append(" AND id = ?");
            args.add(filter.venueId());
        }
        List<java.util.Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        double dailyHours = 0;
        for (java.util.Map<String, Object> row : rows) {
            LocalTime start = ((java.sql.Time) row.get("open_start")).toLocalTime();
            LocalTime end = ((java.sql.Time) row.get("open_end")).toLocalTime();
            long minutes = java.time.Duration.between(start, end).toMinutes();
            if (minutes > 0) dailyHours += minutes / 60.0;
        }
        return dailyHours * Math.max(days, 1);
    }

    public PageResult<Dtos.StatsDetailView> details(Filter filter, int page, int size) {
        List<Object> args = new ArrayList<>();
        String baseWhere = where(filter, args);
        List<Object> countArgs = new ArrayList<>(args);
        String productWhere = productFilter(filter, args, "s");
        String countSql = "SELECT COUNT(*) FROM booking b JOIN venue v ON v.id = b.venue_id "
                + baseWhere + " AND b.status = 'completed'";
        if (hasProduct(filter)) {
            countSql += " AND EXISTS (SELECT 1 FROM sales_record sx WHERE sx.booking_id = b.id AND sx.product_name = ?)";
            countArgs.add(filter.productName().trim());
        }
        Long total = jdbc.queryForObject(countSql, Long.class, countArgs.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Dtos.StatsDetailView> items = jdbc.query("""
                SELECT b.id AS booking_id, b.booking_no, f.name AS farmer_name,
                       v.name AS venue_name, v.county_code, b.start_time, b.topic,
                       COALESCE(SUM(s.quantity),0) AS sold_quantity,
                       COUNT(s.id) AS sales_count
                FROM booking b
                JOIN farmer f ON f.id = b.farmer_id
                JOIN venue v ON v.id = b.venue_id
                LEFT JOIN sales_record s ON s.booking_id = b.id
                """ + baseWhere + productWhere + " AND b.status = 'completed' "
                + "GROUP BY b.id, b.booking_no, f.name, v.name, v.county_code, b.start_time, b.topic "
                + "ORDER BY b.start_time DESC LIMIT ? OFFSET ?", detailMapper, pageArgs.toArray());
        return new PageResult<>(items, total == null ? 0 : total, page, size);
    }

    public List<Dtos.StatsTrendView> trend(Filter filter, String granularity) {
        List<Object> args = new ArrayList<>();
        String baseWhere = where(filter, args);
        String productWhere = productFilter(filter, args, "s");
        String format = switch (granularity == null ? "day" : granularity) {
            case "week" -> "%x-%v";
            case "month" -> "%Y-%m";
            default -> "%Y-%m-%d";
        };
        List<Object> queryArgs = new ArrayList<>();
        queryArgs.add(format);
        queryArgs.addAll(args);
        return jdbc.query("""
                SELECT DATE_FORMAT(b.start_time, ?) AS period,
                       COUNT(DISTINCT b.id) AS live_count,
                       COALESCE(SUM(s.quantity),0) AS sold_quantity
                FROM booking b
                JOIN venue v ON v.id = b.venue_id
                LEFT JOIN sales_record s ON s.booking_id = b.id
                """ + baseWhere + productWhere + " AND b.status = 'completed' "
                + "GROUP BY period ORDER BY period", (rs, rowNum) -> new Dtos.StatsTrendView(
                rs.getString("period"), rs.getLong("live_count"), rs.getDouble("sold_quantity")
        ), queryArgs.toArray());
    }

    public List<Dtos.StatsDetailView> exportDetails(Filter filter) {
        List<Object> args = new ArrayList<>();
        String baseWhere = where(filter, args);
        String productWhere = productFilter(filter, args, "s");
        return jdbc.query("""
                SELECT b.id AS booking_id, b.booking_no, f.name AS farmer_name,
                       v.name AS venue_name, v.county_code, b.start_time, b.topic,
                       COALESCE(SUM(s.quantity),0) AS sold_quantity,
                       COUNT(s.id) AS sales_count
                FROM booking b
                JOIN farmer f ON f.id = b.farmer_id
                JOIN venue v ON v.id = b.venue_id
                LEFT JOIN sales_record s ON s.booking_id = b.id
                """ + baseWhere + productWhere + " AND b.status = 'completed' "
                + "GROUP BY b.id, b.booking_no, f.name, v.name, v.county_code, b.start_time, b.topic "
                + "ORDER BY b.start_time DESC", detailMapper, args.toArray());
    }

    public List<String> productNames() {
        return jdbc.queryForList("""
                SELECT product_name FROM (
                    SELECT product_name FROM sales_record
                    WHERE product_name IS NOT NULL AND product_name <> ''
                    UNION
                    SELECT product_name FROM booking_product
                    WHERE product_name IS NOT NULL AND product_name <> ''
                ) t ORDER BY product_name
                """, String.class);
    }

    private boolean hasProduct(Filter filter) {
        return filter.productName() != null && !filter.productName().isBlank();
    }

    private String productFilter(Filter filter, List<Object> args, String alias) {
        if (!hasProduct(filter)) return "";
        args.add(filter.productName().trim());
        return " AND " + alias + ".product_name = ? ";
    }

    private String where(Filter filter, List<Object> args) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        if (filter.from() != null) {
            where.append(" AND b.start_time >= ? ");
            args.add(Timestamp.valueOf(filter.from()));
        }
        if (filter.to() != null) {
            where.append(" AND b.start_time < ? ");
            args.add(Timestamp.valueOf(filter.to()));
        }
        if (filter.countyCode() != null && !filter.countyCode().isBlank()) {
            where.append(" AND v.county_code = ? ");
            args.add(filter.countyCode().trim());
        }
        if (filter.venueId() != null) {
            where.append(" AND b.venue_id = ? ");
            args.add(filter.venueId());
        }
        return where.toString();
    }
}
