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

/**
 * 操作日志数据访问。日志只追加，不提供更新和删除。
 */
@Repository
public class AuditRepository {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<Dtos.AuditLogView> mapper = (rs, rowNum) -> new Dtos.AuditLogView(
            rs.getLong("id"),
            rs.getObject("operator_id") == null ? null : rs.getLong("operator_id"),
            rs.getString("operator_name"),
            rs.getString("role_code"),
            rs.getString("action"),
            rs.getString("detail"),
            rs.getString("ip"),
            rs.getTimestamp("created_at").toLocalDateTime().format(DATE_TIME)
    );

    public void insert(Long operatorId, String operatorName, String roleCode,
                       String action, String detail, String checksum) {
        jdbc.update("""
                INSERT INTO audit_log (operator_id, operator_name, role_code, action, detail, checksum)
                VALUES (?, ?, ?, ?, ?, ?)
                """, operatorId, operatorName, roleCode, action, detail, checksum);
    }

    public PageResult<Dtos.AuditLogView> page(String keyword, String action,
                                              LocalDateTime from, LocalDateTime to,
                                              int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (operator_name LIKE ? OR detail LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND action = ? ");
            args.add(action.trim());
        }
        if (from != null) {
            where.append(" AND created_at >= ? ");
            args.add(Timestamp.valueOf(from));
        }
        if (to != null) {
            where.append(" AND created_at < ? ");
            args.add(Timestamp.valueOf(to));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Dtos.AuditLogView> items = jdbc.query(
                "SELECT id, operator_id, operator_name, role_code, action, detail, ip, created_at FROM audit_log"
                        + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                mapper, pageArgs.toArray());
        return new PageResult<>(items, total == null ? 0 : total, page, size);
    }

    public List<Dtos.AuditLogView> listAll(String keyword, String action,
                                           LocalDateTime from, LocalDateTime to) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (operator_name LIKE ? OR detail LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND action = ? ");
            args.add(action.trim());
        }
        if (from != null) {
            where.append(" AND created_at >= ? ");
            args.add(Timestamp.valueOf(from));
        }
        if (to != null) {
            where.append(" AND created_at < ? ");
            args.add(Timestamp.valueOf(to));
        }
        return jdbc.query("SELECT id, operator_id, operator_name, role_code, action, detail, ip, created_at "
                + "FROM audit_log" + where + " ORDER BY id DESC", mapper, args.toArray());
    }
}
