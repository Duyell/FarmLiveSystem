package com.farmlive.repo;

import com.farmlive.dto.Dtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 区域字典（省 / 市 / 县）数据访问。
 * 只查询启用中的区域；按编码排序保证逐级返回顺序稳定。
 */
@Repository
public class RegionRepository {
    private final JdbcTemplate jdbc;

    public RegionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 按上级编码查询下一级区域；parentCode 为空时返回省级（parent_code IS NULL）。
     */
    public List<Dtos.RegionView> listChildren(String parentCode) {
        String sql = """
                SELECT code, parent_code, level, name
                FROM region
                WHERE enabled = 1
                  AND (parent_code = ? OR (? IS NULL AND parent_code IS NULL))
                ORDER BY code
                """;
        return jdbc.query(sql, viewMapper, parentCode, parentCode);
    }

    /**
     * 按编码查询区域（不管是否启用），用于保存前的校验和名称快照。
     */
    public Optional<RegionRow> findRowByCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        String sql = """
                SELECT code, parent_code, level, name, enabled
                FROM region
                WHERE code = ?
                """;
        return jdbc.query(sql, rowMapper, code.trim()).stream().findFirst();
    }

    private final RowMapper<Dtos.RegionView> viewMapper = (rs, rowNum) -> new Dtos.RegionView(
            rs.getString("code"),
            rs.getString("parent_code"),
            rs.getInt("level"),
            rs.getString("name")
    );

    private final RowMapper<RegionRow> rowMapper = (rs, rowNum) -> new RegionRow(
            rs.getString("code"),
            rs.getString("parent_code"),
            rs.getInt("level"),
            rs.getString("name"),
            rs.getInt("enabled") == 1
    );

    /**
     * 区域行（含启用状态），仅用于服务端校验，不直接返回给前端。
     */
    public record RegionRow(
            String code,
            String parentCode,
            int level,
            String name,
            boolean enabled
    ) {
    }
}
