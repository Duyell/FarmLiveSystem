package com.farmlive.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 问答记录数据访问。
 */
@Repository
public class QaRepository {
    private static final String SELECT = """
            SELECT id, question, answer, citations,
                   DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at
            FROM qa_record
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public QaRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Long insert(Long accountId, String question, String answer, List<String> citations) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            String citationsJson = mapper.writeValueAsString(citations == null ? List.of() : citations);
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO qa_record (account_id, question, answer, citations, handoff)
                        VALUES (?, ?, ?, ?, 0)
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, accountId);
                ps.setString(2, question);
                ps.setString(3, answer);
                ps.setString(4, citationsJson);
                return ps;
            }, keyHolder);
        } catch (Exception ex) {
            throw new IllegalStateException("引用序列化失败", ex);
        }
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public PageResult<Dtos.QaView> pageByAccount(Long accountId, int page, int size) {
        String where = " WHERE account_id = ? ";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM qa_record" + where,
                Long.class, accountId);
        List<Object> args = new ArrayList<>();
        args.add(accountId);
        args.add(size);
        args.add((page - 1) * size);
        List<Dtos.QaView> items = jdbc.query(SELECT + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                viewMapper, args.toArray());
        return new PageResult<>(items, total == null ? 0 : total, page, size);
    }

    public Dtos.QaView findById(Long id) {
        return jdbc.query(SELECT + " WHERE id = ?", viewMapper, id).stream().findFirst().orElse(null);
    }

    private final RowMapper<Dtos.QaView> viewMapper = (rs, rowNum) -> new Dtos.QaView(
            rs.getLong("id"),
            rs.getString("question"),
            rs.getString("answer"),
            parseCitations(rs.getString("citations")),
            rs.getString("created_at")
    );

    private List<String> parseCitations(String json) {
        try {
            return json == null || json.isBlank() ? List.of()
                    : mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ex) {
            return List.of();
        }
    }
}
