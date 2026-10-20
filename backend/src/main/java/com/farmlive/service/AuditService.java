package com.farmlive.service;

import com.farmlive.auth.CurrentUser;
import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import com.farmlive.repo.AuditRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 操作日志：记录关键业务操作，提供查询和导出。
 * 日志只追加，校验值用于发现事后改动。
 */
@Service
public class AuditService {
    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    public void record(CurrentUser user, String action, String detail) {
        String name = user == null ? "系统" : safe(user.displayName(), user.username());
        String role = user == null ? "system" : safe(user.roleCode(), "system");
        Long operatorId = user == null ? null : user.id();
        String content = operatorId + "|" + name + "|" + role + "|" + action + "|" + detail;
        repository.insert(operatorId, name, role, action, detail, checksum(content));
    }

    public PageResult<Dtos.AuditLogView> page(String keyword, String action,
                                              String from, String to, int page, int size) {
        LocalDateTime fromTime = parseDate(from, false);
        LocalDateTime toTime = parseDate(to, true);
        return repository.page(keyword, action, fromTime, toTime, page, size);
    }

    public java.util.List<Dtos.AuditLogView> export(String keyword, String action, String from, String to) {
        return repository.listAll(keyword, action, parseDate(from, false), parseDate(to, true));
    }

    private LocalDateTime parseDate(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) return null;
        try {
            LocalDate date = LocalDate.parse(value.trim());
            return endOfDay ? date.plusDays(1).atStartOfDay() : date.atStartOfDay();
        } catch (Exception ex) {
            return null;
        }
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String checksum(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            return null;
        }
    }
}
