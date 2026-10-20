package com.farmlive.repo;

import com.farmlive.dto.Dtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 农户资质材料数据访问。文件本体存磁盘，表里只存元数据。
 */
@Repository
public class QualificationRepository {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;

    public QualificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record QualificationRow(
            Long id,
            Long farmerId,
            String originalName,
            String storedName,
            String contentType,
            String status
    ) {
    }

    private final RowMapper<Dtos.QualificationView> viewMapper = (rs, rowNum) -> new Dtos.QualificationView(
            rs.getLong("id"),
            rs.getLong("farmer_id"),
            rs.getString("original_name"),
            rs.getString("content_type"),
            rs.getLong("file_size"),
            rs.getString("status"),
            rs.getString("review_comment"),
            rs.getString("uploader_name"),
            rs.getTimestamp("created_at").toLocalDateTime().format(DATE_TIME),
            rs.getTimestamp("reviewed_at") == null
                    ? null : rs.getTimestamp("reviewed_at").toLocalDateTime().format(DATE_TIME)
    );

    public Long insert(Long farmerId, String originalName, String storedName,
                       String contentType, long size, Long uploadedBy) {
        org.springframework.jdbc.support.KeyHolder keys =
                new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            java.sql.PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO farmer_qualification
                    (farmer_id, original_name, stored_name, content_type, file_size, uploaded_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, farmerId);
            ps.setString(2, originalName);
            ps.setString(3, storedName);
            ps.setString(4, contentType);
            ps.setLong(5, size);
            ps.setObject(6, uploadedBy);
            return ps;
        }, keys);
        Number key = keys.getKey();
        return key == null ? null : key.longValue();
    }

    public List<Dtos.QualificationView> listByFarmer(Long farmerId) {
        return jdbc.query("""
                SELECT q.id, q.farmer_id, q.original_name, q.content_type, q.file_size,
                       q.status, q.review_comment, a.display_name AS uploader_name,
                       q.created_at, q.reviewed_at
                FROM farmer_qualification q
                LEFT JOIN account a ON a.id = q.uploaded_by
                WHERE q.farmer_id = ? ORDER BY q.id DESC
                """, viewMapper, farmerId);
    }

    public Optional<QualificationRow> findRow(Long id) {
        return jdbc.query("""
                SELECT id, farmer_id, original_name, stored_name, content_type, status
                FROM farmer_qualification WHERE id = ?
                """, (rs, rowNum) -> new QualificationRow(
                rs.getLong("id"), rs.getLong("farmer_id"),
                rs.getString("original_name"), rs.getString("stored_name"),
                rs.getString("content_type"), rs.getString("status")
        ), id).stream().findFirst();
    }

    public void review(Long id, String status, String comment, Long reviewerId, LocalDateTime reviewedAt) {
        jdbc.update("""
                UPDATE farmer_qualification
                SET status = ?, review_comment = ?, reviewed_by = ?, reviewed_at = ?
                WHERE id = ?
                """, status, comment, reviewerId,
                reviewedAt == null ? null : Timestamp.valueOf(reviewedAt), id);
    }

    public void updateFarmerQualification(Long farmerId, String status) {
        jdbc.update("UPDATE farmer SET qualification_status = ? WHERE id = ?", status, farmerId);
    }

    public boolean farmerExists(Long farmerId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM farmer WHERE id = ?",
                Integer.class, farmerId);
        return count != null && count > 0;
    }
}
