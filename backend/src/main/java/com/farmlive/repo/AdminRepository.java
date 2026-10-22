package com.farmlive.repo;

import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class AdminRepository {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbc;

    public AdminRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record AuthAccount(
            Long id,
            String username,
            String passwordHash,
            String displayName,
            Long roleId,
            String roleCode,
            String roleName,
            Long farmerId,
            String farmerName,
            String status,
            boolean mustChangePassword
    ) {
    }

    // ---------------- auth ----------------

    public Optional<AuthAccount> findAuthByUsername(String username) {
        String sql = """
                SELECT a.id, a.username, a.password_hash, a.display_name, a.role_id,
                       r.code AS role_code, r.name AS role_name,
                       a.farmer_id, f.name AS farmer_name, a.status, a.must_change_password
                FROM account a
                JOIN role r ON r.id = a.role_id
                LEFT JOIN farmer f ON f.id = a.farmer_id
                WHERE a.username = ?
                """;
        return jdbc.query(sql, authMapper, username).stream().findFirst();
    }

    public Optional<Dtos.UserView> findUserViewById(Long id) {
        String sql = """
                SELECT a.id, a.username, a.display_name, r.code AS role_code, r.name AS role_name,
                       a.farmer_id, f.name AS farmer_name, a.must_change_password
                FROM account a
                JOIN role r ON r.id = a.role_id
                LEFT JOIN farmer f ON f.id = a.farmer_id
                WHERE a.id = ?
                """;
        return jdbc.query(sql, (rs, rowNum) -> new Dtos.UserView(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("role_code"),
                rs.getString("role_name"),
                rs.getObject("farmer_id", Long.class),
                rs.getString("farmer_name"),
                rs.getBoolean("must_change_password")
        ), id).stream().findFirst();
    }

    public void updateLastLogin(Long id) {
        jdbc.update("UPDATE account SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?", id);
    }

    public void updatePassword(Long id, String passwordHash, boolean mustChange) {
        jdbc.update("UPDATE account SET password_hash = ?, must_change_password = ? WHERE id = ?",
                passwordHash, mustChange ? 1 : 0, id);
    }

    public long countSystemAdmins() {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM account a JOIN role r ON r.id = a.role_id
                WHERE r.code = 'system_admin' AND a.status = 'active'
                """, Long.class);
        return count == null ? 0 : count;
    }

    // ---------------- roles ----------------

    public List<Dtos.RoleView> listRoles() {
        return jdbc.query("SELECT id, code, name FROM role ORDER BY id",
                (rs, rowNum) -> new Dtos.RoleView(rs.getLong("id"), rs.getString("code"), rs.getString("name")));
    }

    public Optional<Dtos.RoleView> findRole(Long id) {
        return jdbc.query("SELECT id, code, name FROM role WHERE id = ?",
                (rs, rowNum) -> new Dtos.RoleView(rs.getLong("id"), rs.getString("code"), rs.getString("name")),
                id).stream().findFirst();
    }

    // ---------------- accounts ----------------

    public PageResult<Dtos.AccountView> listAccounts(String keyword, int page, int size) {
        String where = keyword == null || keyword.isBlank() ? "" :
                " WHERE (a.username LIKE ? OR a.display_name LIKE ? OR f.name LIKE ?) ";
        List<Object> args = new ArrayList<>();
        if (!where.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        String base = """
                FROM account a
                JOIN role r ON r.id = a.role_id
                LEFT JOIN farmer f ON f.id = a.farmer_id
                """ + where;
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + base, Long.class, args.toArray());
        List<Dtos.AccountView> items = jdbc.query("""
                SELECT a.id, a.username, a.display_name, a.role_id, r.code AS role_code, r.name AS role_name,
                       a.farmer_id, f.name AS farmer_name, a.status, a.must_change_password,
                       a.last_login_at, a.created_at
                """ + base + " ORDER BY a.id DESC LIMIT ? OFFSET ?",
                accountMapper,
                append(args, size, (page - 1) * size).toArray());
        return new PageResult<>(items, total == null ? 0 : total, page, size);
    }

    public Optional<Dtos.AccountView> findAccount(Long id) {
        return jdbc.query("""
                SELECT a.id, a.username, a.display_name, a.role_id, r.code AS role_code, r.name AS role_name,
                       a.farmer_id, f.name AS farmer_name, a.status, a.must_change_password,
                       a.last_login_at, a.created_at
                FROM account a
                JOIN role r ON r.id = a.role_id
                LEFT JOIN farmer f ON f.id = a.farmer_id
                WHERE a.id = ?
                """, accountMapper, id).stream().findFirst();
    }

    public boolean usernameExists(String username) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM account WHERE username = ?", Long.class, username);
        return count != null && count > 0;
    }

    public Long createAccount(String username, String passwordHash, String displayName,
                              Long roleId, Long farmerId, String status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO account
                    (username, password_hash, display_name, role_id, farmer_id, status, must_change_password)
                    VALUES (?, ?, ?, ?, ?, ?, 1)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, displayName);
            ps.setLong(4, roleId);
            if (farmerId == null) ps.setNull(5, java.sql.Types.BIGINT); else ps.setLong(5, farmerId);
            ps.setString(6, status);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void updateAccount(Long id, String displayName, Long roleId, Long farmerId, String status) {
        jdbc.update("""
                UPDATE account SET display_name = ?, role_id = ?, farmer_id = ?, status = ?
                WHERE id = ?
                """, displayName, roleId, farmerId, status, id);
    }

    public void deleteAccount(Long id) {
        jdbc.update("DELETE FROM account WHERE id = ?", id);
    }

    public boolean farmerHasAccount(Long farmerId, Long excludeAccountId) {
        Long count;
        if (excludeAccountId == null) {
            count = jdbc.queryForObject("SELECT COUNT(*) FROM account WHERE farmer_id = ?", Long.class, farmerId);
        } else {
            count = jdbc.queryForObject("SELECT COUNT(*) FROM account WHERE farmer_id = ? AND id <> ?",
                    Long.class, farmerId, excludeAccountId);
        }
        return count != null && count > 0;
    }

    public String findAccountRoleCode(Long accountId) {
        List<String> codes = jdbc.query("""
                SELECT r.code FROM account a JOIN role r ON r.id = a.role_id WHERE a.id = ?
                """, (rs, rowNum) -> rs.getString("code"), accountId);
        return codes.isEmpty() ? null : codes.get(0);
    }

    // ---------------- farmers ----------------

    public PageResult<Dtos.FarmerView> listFarmers(String keyword, String countyCode, int page, int size) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" WHERE (farmer_no LIKE ? OR name LIKE ? OR county LIKE ? OR phone LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            for (int i = 0; i < 5; i++) args.add(like);
        }
        if (countyCode != null && !countyCode.isBlank()) {
            where.append(where.length() == 0 ? " WHERE county_code = ? " : " AND county_code = ? ");
            args.add(countyCode.trim());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM farmer" + where, Long.class, args.toArray());
        List<Dtos.FarmerView> items = jdbc.query("""
                SELECT id, farmer_no, name, county, county_code, phone, qualification_status, status, created_at
                FROM farmer
                """ + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                farmerMapper, append(args, size, (page - 1) * size).toArray());
        return new PageResult<>(items, total == null ? 0 : total, page, size);
    }

    public Optional<Dtos.FarmerView> findFarmer(Long id) {
        return jdbc.query("""
                SELECT id, farmer_no, name, county, county_code, phone, qualification_status, status, created_at
                FROM farmer WHERE id = ?
                """, farmerMapper, id).stream().findFirst();
    }

    public Optional<String> findFarmerPhone(Long id) {
        return jdbc.query("SELECT phone FROM farmer WHERE id = ?",
                (rs, rowNum) -> rs.getString("phone"), id).stream().findFirst();
    }

    public Long createFarmer(String farmerNo, String name, String county, String countyCode, String phone,
                             String qualificationStatus, String status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO farmer (farmer_no, name, county, county_code, phone, qualification_status, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, farmerNo);
            ps.setString(2, name);
            ps.setString(3, county);
            ps.setString(4, countyCode);
            ps.setString(5, phone);
            ps.setString(6, qualificationStatus);
            ps.setString(7, status);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void updateFarmer(Long id, String name, String county, String countyCode, String phone,
                             String qualificationStatus, String status) {
        jdbc.update("""
                UPDATE farmer SET name = ?, county = ?, county_code = ?, phone = ?, qualification_status = ?, status = ?
                WHERE id = ?
                """, name, county, countyCode, phone, qualificationStatus, status, id);
    }

    public void deleteFarmer(Long id) {
        jdbc.update("DELETE FROM farmer WHERE id = ?", id);
    }

    public boolean farmerHasBooking(Long farmerId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE farmer_id = ?", Long.class, farmerId);
        return count != null && count > 0;
    }

    // ---------------- venues ----------------

    public PageResult<Dtos.VenueView> listVenues(String keyword, String countyCode, int page, int size) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" WHERE (venue_no LIKE ? OR name LIKE ? OR location LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            for (int i = 0; i < 3; i++) args.add(like);
        }
        if (countyCode != null && !countyCode.isBlank()) {
            where.append(where.length() == 0 ? " WHERE county_code = ? " : " AND county_code = ? ");
            args.add(countyCode.trim());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM venue" + where, Long.class, args.toArray());
        List<Dtos.VenueView> items = jdbc.query("""
                SELECT id, venue_no, name, location, capacity, open_start, open_end, county_code, status, created_at
                FROM venue
                """ + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                venueMapper, append(args, size, (page - 1) * size).toArray());
        return new PageResult<>(items, total == null ? 0 : total, page, size);
    }

    public Optional<Dtos.VenueView> findVenue(Long id) {
        return jdbc.query("""
                SELECT id, venue_no, name, location, capacity, open_start, open_end, county_code, status, created_at
                FROM venue WHERE id = ?
                """, venueMapper, id).stream().findFirst();
    }

    public Long createVenue(String venueNo, String name, String location, Integer capacity,
                            LocalTime openStart, LocalTime openEnd, String countyCode, String status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO venue (venue_no, name, location, capacity, open_start, open_end, county_code, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, venueNo);
            ps.setString(2, name);
            ps.setString(3, location);
            ps.setInt(4, capacity);
            ps.setTime(5, Time.valueOf(openStart));
            ps.setTime(6, Time.valueOf(openEnd));
            ps.setString(7, countyCode);
            ps.setString(8, status);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void updateVenue(Long id, String name, String location, Integer capacity,
                            LocalTime openStart, LocalTime openEnd, String countyCode, String status) {
        jdbc.update("""
                UPDATE venue SET name = ?, location = ?, capacity = ?, open_start = ?, open_end = ?,
                                county_code = ?, status = ?
                WHERE id = ?
                """, name, location, capacity, Time.valueOf(openStart), Time.valueOf(openEnd), countyCode, status, id);
    }

    public void deleteVenue(Long id) {
        jdbc.update("DELETE FROM venue WHERE id = ?", id);
    }

    public boolean venueHasDevices(Long venueId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM device WHERE venue_id = ?", Long.class, venueId);
        return count != null && count > 0;
    }

    public boolean venueHasBooking(Long venueId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE venue_id = ?", Long.class, venueId);
        return count != null && count > 0;
    }

    // ---------------- devices ----------------

    public PageResult<Dtos.DeviceView> listDevices(String keyword, int page, int size) {
        String where = keyword == null || keyword.isBlank() ? "" :
                " WHERE (d.device_no LIKE ? OR d.name LIKE ? OR d.model LIKE ? OR d.config LIKE ? OR v.name LIKE ?) ";
        List<Object> args = new ArrayList<>();
        if (!where.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            for (int i = 0; i < 5; i++) args.add(like);
        }
        String base = " FROM device d JOIN venue v ON v.id = d.venue_id " + where;
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + base, Long.class, args.toArray());
        List<Dtos.DeviceView> items = jdbc.query("""
                SELECT d.id, d.device_no, d.name, d.model, d.config, d.venue_id, v.name AS venue_name,
                       d.status, d.calibration_date, d.created_at
                """ + base + " ORDER BY d.id DESC LIMIT ? OFFSET ?",
                deviceMapper, append(args, size, (page - 1) * size).toArray());
        return new PageResult<>(items, total == null ? 0 : total, page, size);
    }

    public Optional<Dtos.DeviceView> findDevice(Long id) {
        return jdbc.query("""
                SELECT d.id, d.device_no, d.name, d.model, d.config, d.venue_id, v.name AS venue_name,
                       d.status, d.calibration_date, d.created_at
                FROM device d JOIN venue v ON v.id = d.venue_id
                WHERE d.id = ?
                """, deviceMapper, id).stream().findFirst();
    }

    public Long createDevice(String deviceNo, String name, String model, String config, Long venueId,
                             String status, LocalDate calibrationDate) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO device (device_no, name, model, config, venue_id, status, calibration_date)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, deviceNo);
            ps.setString(2, name);
            ps.setString(3, model);
            ps.setString(4, config);
            ps.setLong(5, venueId);
            ps.setString(6, status);
            if (calibrationDate == null) ps.setNull(7, java.sql.Types.DATE);
            else ps.setDate(7, Date.valueOf(calibrationDate));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void updateDevice(Long id, String name, String model, String config, Long venueId,
                             String status, LocalDate calibrationDate) {
        jdbc.update("""
                UPDATE device SET name = ?, model = ?, config = ?, venue_id = ?, status = ?, calibration_date = ?
                WHERE id = ?
                """, name, model, config, venueId, status,
                calibrationDate == null ? null : Date.valueOf(calibrationDate), id);
    }

    public void deleteDevice(Long id) {
        jdbc.update("DELETE FROM device WHERE id = ?", id);
    }

    public boolean deviceHasBooking(Long deviceId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE device_id = ?", Long.class, deviceId);
        return count != null && count > 0;
    }

    // ---------------- dashboard ----------------

    public Dtos.DashboardView dashboard() {
        return new Dtos.DashboardView(
                count("account"), count("farmer"), count("venue"), count("device"), count("booking"),
                countWhere("booking", "status = 'pending_review'"),
                countWhere("booking", "status IN ('approved','completed') AND start_time >= CURDATE() AND start_time < DATE_ADD(CURDATE(), INTERVAL 1 DAY)"),
                countWhere("venue", "status = 'available'"),
                countWhere("device", "status = 'available'")
        );
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0 : value;
    }

    private long countWhere(String table, String where) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Long.class);
        return value == null ? 0 : value;
    }

    private List<Object> append(List<Object> args, Object... extra) {
        List<Object> result = new ArrayList<>(args);
        for (Object item : extra) result.add(item);
        return result;
    }

    private String dateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().format(DATE_TIME);
    }

    private final RowMapper<AuthAccount> authMapper = (rs, rowNum) -> new AuthAccount(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("display_name"),
            rs.getLong("role_id"),
            rs.getString("role_code"),
            rs.getString("role_name"),
            rs.getObject("farmer_id", Long.class),
            rs.getString("farmer_name"),
            rs.getString("status"),
            rs.getBoolean("must_change_password")
    );

    private final RowMapper<Dtos.AccountView> accountMapper = (rs, rowNum) -> new Dtos.AccountView(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getLong("role_id"),
            rs.getString("role_code"),
            rs.getString("role_name"),
            rs.getObject("farmer_id", Long.class),
            rs.getString("farmer_name"),
            rs.getString("status"),
            rs.getBoolean("must_change_password"),
            dateTime(rs.getTimestamp("last_login_at")),
            dateTime(rs.getTimestamp("created_at"))
    );

    private final RowMapper<Dtos.FarmerView> farmerMapper = (rs, rowNum) -> new Dtos.FarmerView(
            rs.getLong("id"),
            rs.getString("farmer_no"),
            rs.getString("name"),
            rs.getString("county"),
            rs.getString("county_code"),
            maskPhone(rs.getString("phone")),
            rs.getString("qualification_status"),
            rs.getString("status"),
            dateTime(rs.getTimestamp("created_at"))
    );

    private final RowMapper<Dtos.VenueView> venueMapper = (rs, rowNum) -> new Dtos.VenueView(
            rs.getLong("id"),
            rs.getString("venue_no"),
            rs.getString("name"),
            rs.getString("location"),
            rs.getInt("capacity"),
            rs.getTime("open_start").toLocalTime().format(TIME),
            rs.getTime("open_end").toLocalTime().format(TIME),
            rs.getString("county_code"),
            rs.getString("status"),
            dateTime(rs.getTimestamp("created_at"))
    );

    private final RowMapper<Dtos.DeviceView> deviceMapper = (rs, rowNum) -> new Dtos.DeviceView(
            rs.getLong("id"),
            rs.getString("device_no"),
            rs.getString("name"),
            rs.getString("model"),
            rs.getString("config"),
            rs.getLong("venue_id"),
            rs.getString("venue_name"),
            rs.getString("status"),
            rs.getDate("calibration_date") == null ? null : rs.getDate("calibration_date").toLocalDate().format(DATE),
            dateTime(rs.getTimestamp("created_at"))
    );

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static LocalDateTime parseDateTime(String value) {
        return LocalDateTime.parse(value, DATE_TIME);
    }

    public static LocalDate parseDate(String value) {
        return LocalDate.parse(value, DATE);
    }

    public static LocalTime parseTime(String value) {
        return LocalTime.parse(value, TIME);
    }
}
