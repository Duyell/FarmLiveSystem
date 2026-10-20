package com.farmlive.repo;

import com.farmlive.dto.Dtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 通知模板数据访问。变量用 ${name} 占位，缺失变量时不发送。
 */
@Repository
public class NotificationTemplateRepository {
    private final JdbcTemplate jdbc;

    public NotificationTemplateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record TemplateRow(
            Long id,
            String code,
            String name,
            String type,
            String titleTemplate,
            String contentTemplate,
            boolean enabled
    ) {
    }

    private final RowMapper<Dtos.TemplateView> viewMapper = (rs, rowNum) -> new Dtos.TemplateView(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("type"),
            rs.getString("title_template"),
            rs.getString("content_template"),
            rs.getInt("enabled") == 1,
            rs.getTimestamp("updated_at").toLocalDateTime().format(QueueRepository.DATE_TIME)
    );

    private final RowMapper<TemplateRow> rowMapper = (rs, rowNum) -> new TemplateRow(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("type"),
            rs.getString("title_template"),
            rs.getString("content_template"),
            rs.getInt("enabled") == 1
    );

    public Optional<TemplateRow> findByCode(String code) {
        return jdbc.query("""
                SELECT id, code, name, type, title_template, content_template, enabled
                FROM notification_template WHERE code = ?
                """, rowMapper, code).stream().findFirst();
    }

    public boolean codeExists(String code, Long excludeId) {
        Integer count = excludeId == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM notification_template WHERE code = ?", Integer.class, code)
                : jdbc.queryForObject("SELECT COUNT(*) FROM notification_template WHERE code = ? AND id <> ?",
                        Integer.class, code, excludeId);
        return count != null && count > 0;
    }

    public List<Dtos.TemplateView> list(String type) {
        StringBuilder sql = new StringBuilder("SELECT id, code, name, type, title_template, content_template, enabled, updated_at FROM notification_template ");
        if (type != null && !type.isBlank()) {
            sql.append("WHERE type = ? ");
            return jdbc.query(sql + "ORDER BY id", viewMapper, type.trim());
        }
        return jdbc.query(sql + "ORDER BY id", viewMapper);
    }

    public Long insert(String code, String name, String type, String titleTemplate, String contentTemplate, boolean enabled) {
        jdbc.update("""
                INSERT INTO notification_template (code, name, type, title_template, content_template, enabled)
                VALUES (?, ?, ?, ?, ?, ?)
                """, code, name, type, titleTemplate, contentTemplate, enabled ? 1 : 0);
        Long id = jdbc.queryForObject("SELECT id FROM notification_template WHERE code = ?", Long.class, code);
        return id;
    }

    public void update(Long id, String name, String type, String titleTemplate, String contentTemplate, boolean enabled) {
        jdbc.update("""
                UPDATE notification_template SET name = ?, type = ?, title_template = ?, content_template = ?, enabled = ?
                WHERE id = ?
                """, name, type, titleTemplate, contentTemplate, enabled ? 1 : 0, id);
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM notification_template WHERE id = ?", id);
    }
}
