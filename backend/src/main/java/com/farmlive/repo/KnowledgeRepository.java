package com.farmlive.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmlive.dto.Dtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * 知识库数据访问：文档元信息 + 分块与向量。
 */
@Repository
public class KnowledgeRepository {
    private static final String DOC_SELECT = """
            SELECT d.id, d.title, d.file_name, d.file_type, d.file_size, d.parse_status, d.enabled,
                   (SELECT COUNT(*) FROM knowledge_chunk c WHERE c.doc_id = d.id) AS chunk_count,
                   a.display_name AS uploader_name,
                   DATE_FORMAT(d.created_at, '%Y-%m-%d %H:%i:%s') AS created_at,
                   DATE_FORMAT(d.updated_at, '%Y-%m-%d %H:%i:%s') AS updated_at
            FROM knowledge_doc d
            LEFT JOIN account a ON a.id = d.uploaded_by
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public KnowledgeRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record DocRow(
            Long id,
            String title,
            String parseStatus,
            boolean enabled
    ) {
    }

    public record ChunkRow(
            Long id,
            Long docId,
            String docTitle,
            int chunkIndex,
            String content,
            double[] embedding
    ) {
    }

    private final RowMapper<Dtos.KnowledgeDocView> docMapper = (rs, rowNum) -> new Dtos.KnowledgeDocView(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("file_name"),
            rs.getString("file_type"),
            rs.getLong("file_size"),
            rs.getString("parse_status"),
            rs.getInt("enabled") == 1,
            rs.getInt("chunk_count"),
            rs.getString("uploader_name"),
            rs.getString("created_at"),
            rs.getString("updated_at")
    );

    private final RowMapper<DocRow> docRowMapper = (rs, rowNum) -> new DocRow(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("parse_status"),
            rs.getInt("enabled") == 1
    );

    public List<Dtos.KnowledgeDocView> listDocs() {
        return jdbc.query(DOC_SELECT + " ORDER BY d.id DESC", docMapper);
    }

    public Optional<DocRow> findDocRow(Long id) {
        return jdbc.query("SELECT id, title, parse_status, enabled FROM knowledge_doc WHERE id = ?",
                docRowMapper, id).stream().findFirst();
    }

    public boolean docExistsByTitle(String title) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_doc WHERE title = ?",
                Integer.class, title);
        return count != null && count > 0;
    }

    /**
     * 系统管理员账号 id，用于种子文档的 uploaded_by。
     */
    public Long findSystemAccountId() {
        return jdbc.query("SELECT id FROM account WHERE username = 'admin' ORDER BY id LIMIT 1",
                (rs, rowNum) -> rs.getLong("id")).stream().findFirst().orElse(null);
    }

    public Long insertDoc(String title, String fileName, String fileType, long fileSize, Long uploadedBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO knowledge_doc (title, file_name, file_type, file_size, parse_status, enabled, uploaded_by)
                    VALUES (?, ?, ?, ?, 'pending', 1, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, title);
            ps.setString(2, fileName);
            ps.setString(3, fileType);
            ps.setLong(4, fileSize);
            if (uploadedBy == null) {
                ps.setNull(5, java.sql.Types.BIGINT);
            } else {
                ps.setLong(5, uploadedBy);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void updateParseStatus(Long id, String status) {
        jdbc.update("UPDATE knowledge_doc SET parse_status = ? WHERE id = ?", status, id);
    }

    public void setEnabled(Long id, boolean enabled) {
        jdbc.update("UPDATE knowledge_doc SET enabled = ? WHERE id = ?", enabled ? 1 : 0, id);
    }

    public void deleteDoc(Long id) {
        jdbc.update("DELETE FROM knowledge_chunk WHERE doc_id = ?", id);
        jdbc.update("DELETE FROM knowledge_doc WHERE id = ?", id);
    }

    public void insertChunk(Long docId, int chunkIndex, String content, int charCount, double[] embedding) {
        try {
            jdbc.update("""
                    INSERT INTO knowledge_chunk (doc_id, chunk_index, content, char_count, embedding)
                    VALUES (?, ?, ?, ?, ?)
                    """, docId, chunkIndex, content, charCount, mapper.writeValueAsString(embedding));
        } catch (Exception ex) {
            throw new IllegalStateException("向量序列化失败", ex);
        }
    }

    public void deleteChunksByDocId(Long docId) {
        jdbc.update("DELETE FROM knowledge_chunk WHERE doc_id = ?", docId);
    }

    /**
     * 全量加载启用中文档的分块（知识库规模小，检索在应用层计算）。
     * 未配置向量接口时分块 embedding 为 null，走关键词检索。
     */
    public List<ChunkRow> listEnabledChunks() {
        return jdbc.query("""
                SELECT c.id, c.doc_id, d.title AS doc_title, c.chunk_index, c.content, c.embedding
                FROM knowledge_chunk c
                JOIN knowledge_doc d ON d.id = c.doc_id AND d.enabled = 1
                ORDER BY c.doc_id, c.chunk_index
                """, (rs, rowNum) -> new ChunkRow(
                rs.getLong("id"),
                rs.getLong("doc_id"),
                rs.getString("doc_title"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                parseVector(rs.getString("embedding"))
        ));
    }

    private double[] parseVector(String json) {
        try {
            return mapper.readValue(json, double[].class);
        } catch (Exception ex) {
            return new double[0];
        }
    }
}
