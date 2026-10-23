package com.farmlive.service;

import com.farmlive.common.BusinessException;
import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import com.farmlive.repo.NotificationRepository;
import com.farmlive.repo.NotificationTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通知业务：站内 + 短信（模拟网关）双渠道分别记状态；
 * 短信失败按 1/5/15 分钟重试三次后最终失败；站内即时送达即兜底。
 * 模板变量缺失时不发送半截内容。
 * 通知失败不影响主业务事务，发送异常全部内部消化。
 */
@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");

    private final NotificationRepository repository;
    private final NotificationTemplateRepository templateRepository;

    @Value("${app.notification.sms-fail-rate:0.0}")
    private double smsFailRate;

    public NotificationService(NotificationRepository repository,
                               NotificationTemplateRepository templateRepository) {
        this.repository = repository;
        this.templateRepository = templateRepository;
    }

    /**
     * 按模板发送事件通知：站内即时送达，短信走模拟网关（可失败重试）。
     * eventKey 用于关联同一事件的站内/短信记录（我的通知按事件合并展示）。
     * 渲染失败（变量缺失）跳过发送，不影响调用方事务。
     */
    public void sendEvent(Long accountId, String type, String templateCode, String eventKey, Map<String, String> vars) {
        if (accountId == null) return;
        try {
            var template = templateRepository.findByCode(templateCode)
                    .filter(t -> t.enabled())
                    .orElse(null);
            if (template == null) {
                log.warn("通知模板不存在或已停用: {}", templateCode);
                return;
            }
            String title = render(template.titleTemplate(), vars, templateCode);
            String content = render(template.contentTemplate(), vars, templateCode);
            Long inAppId = repository.insert(accountId, type, eventKey, "in_app", title, content);
            repository.markSent(inAppId);
            Long smsId = repository.insert(accountId, type, eventKey, "sms", title, content);
            attemptSms(smsId);
        } catch (Exception ex) {
            log.warn("发送通知失败（不影响主业务）: {}", ex.getMessage());
        }
    }

    /**
     * 重试到期短信；重试间隔 1/5/15 分钟，第三次失败置为 final_failed。
     */
    @Scheduled(fixedDelay = 30_000)
    public void retryDueSms() {
        for (NotificationRepository.NotificationRow row : repository.dueForRetry()) {
            attemptSms(row.id());
        }
    }

    /**
     * 直播管理员手动重试。
     */
    @Transactional
    public void retryManual(Long id) {
        NotificationRepository.NotificationRow row = repository.findRowById(id)
                .orElseThrow(() -> BusinessException.notFound("通知不存在"));
        if (!"sms".equals(row.channel())) {
            throw BusinessException.badRequest("只有短信通知支持重试");
        }
        if ("sent".equals(row.status())) {
            throw BusinessException.conflict("NOTIFICATION_STATUS_INVALID", "该通知已发送成功");
        }
        attemptSms(id);
    }

    /**
     * 开播前 1 小时提醒扫描（幂等：同一预约只提醒一次）。
     */
    @Scheduled(fixedDelay = 60_000)
    public void scanReminders() {
        for (NotificationRepository.EventTarget target : repository.reminderCandidates()) {
            sendEvent(target.accountId(), "reminder", "booking_reminder",
                    "reminder:" + target.bookingNo(), Map.of(
                    "farmerName", target.farmerName(),
                    "topic", target.topic(),
                    "startTime", target.startTime(),
                    "venueName", target.venueName(),
                    "bookingNo", target.bookingNo()
            ));
        }
    }

    /**
     * 设备故障：通知该设备未来已通过预约的农户。
     */
    public void notifyDeviceFault(Long deviceId, String deviceName) {
        for (NotificationRepository.EventTarget target : repository.deviceFaultTargets(deviceId)) {
            sendEvent(target.accountId(), "device_fault", "device_fault",
                    "device_fault:" + target.bookingNo(), Map.of(
                    "farmerName", target.farmerName(),
                    "topic", target.topic(),
                    "startTime", target.startTime(),
                    "venueName", target.venueName(),
                    "deviceName", deviceName,
                    "bookingNo", target.bookingNo()
            ));
        }
    }

    /**
     * 设备恢复：通知该设备未来已通过预约的农户。
     */
    public void notifyDeviceRecovered(Long deviceId, String deviceName) {
        for (NotificationRepository.EventTarget target : repository.deviceFaultTargets(deviceId)) {
            sendEvent(target.accountId(), "device_fault", "device_recovered",
                    "device_recovered:" + target.bookingNo(), Map.of(
                    "farmerName", target.farmerName(),
                    "topic", target.topic(),
                    "startTime", target.startTime(),
                    "venueName", target.venueName(),
                    "deviceName", deviceName,
                    "bookingNo", target.bookingNo()
            ));
        }
    }

    public void notifyDeviceFaultCanceled(Long deviceId, String deviceName) {
        for (NotificationRepository.EventTarget target : repository.deviceFaultTargets(deviceId)) {
            sendEvent(target.accountId(), "device_fault", "device_fault_canceled",
                    "device_fault_canceled:" + target.bookingNo(), Map.of(
                    "farmerName", target.farmerName(),
                    "topic", target.topic(),
                    "startTime", target.startTime(),
                    "venueName", target.venueName(),
                    "deviceName", deviceName,
                    "bookingNo", target.bookingNo()
            ));
        }
    }

    /**
     * 按农户档案发送（自动解析农户登录账号；未关联账号则跳过）。
     */
    public void notifyFarmer(Long farmerId, String type, String templateCode, String eventKey, Map<String, String> vars) {
        Long accountId = repository.findAccountIdByFarmerId(farmerId).orElse(null);
        sendEvent(accountId, type, templateCode, eventKey, vars);
    }

    public PageResult<Dtos.NotificationView> listMine(Long accountId, String type, int page, int size) {
        return repository.listByAccount(accountId, type, page, size);
    }

    public void markRead(Long accountId, Long id) {
        repository.markRead(id, accountId);
    }

    public long unreadCount(Long accountId) {
        return repository.unreadCount(accountId);
    }

    public PageResult<Dtos.NotificationView> listAll(String type, String channel, String status, int page, int size) {
        return repository.listAll(type, channel, status, page, size);
    }

    // ---------------- 模板 CRUD ----------------

    public java.util.List<Dtos.TemplateView> listTemplates(String type) {
        return templateRepository.list(type);
    }

    @Transactional
    public void createTemplate(Dtos.TemplateRequest request) {
        String code = request.code().trim();
        if (!code.matches("^[a-z0-9_]{2,40}$")) {
            throw BusinessException.badRequest("模板编码只能是 2-40 位小写字母、数字或下划线");
        }
        if (templateRepository.codeExists(code, null)) {
            throw BusinessException.conflict("模板编码已存在");
        }
        validateType(request.type());
        validateTemplateVars(request.titleTemplate());
        validateTemplateVars(request.contentTemplate());
        templateRepository.insert(code, request.name().trim(), request.type().trim(),
                request.titleTemplate(), request.contentTemplate(), request.enabled() == null || request.enabled());
    }

    @Transactional
    public void updateTemplate(Long id, Dtos.TemplateRequest request) {
        templateRepository.findByCode(request.code().trim())
                .orElseThrow(() -> BusinessException.notFound("模板不存在"));
        if (templateRepository.codeExists(request.code().trim(), id)) {
            throw BusinessException.conflict("模板编码已存在");
        }
        validateType(request.type());
        validateTemplateVars(request.titleTemplate());
        validateTemplateVars(request.contentTemplate());
        templateRepository.update(id, request.name().trim(), request.type().trim(),
                request.titleTemplate(), request.contentTemplate(), request.enabled() == null || request.enabled());
    }

    @Transactional
    public void deleteTemplate(Long id) {
        templateRepository.delete(id);
    }

    // ---------------- 内部 ----------------

    private void attemptSms(Long id) {
        boolean ok = ThreadLocalRandom.current().nextDouble() >= smsFailRate;
        if (ok) {
            repository.markSent(id);
            return;
        }
        int retryCount = repository.findRowById(id).map(NotificationRepository.NotificationRow::retryCount).orElse(0) + 1;
        repository.markFailed(id, retryCount);
    }

    private String render(String template, Map<String, String> vars, String templateCode) {
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = vars.get(key);
            if (value == null) {
                throw new IllegalStateException("模板变量缺失: " + key);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private void validateType(String type) {
        if (!java.util.List.of("review", "queue", "candidate", "reminder", "device_fault").contains(type)) {
            throw BusinessException.badRequest("通知类型只能是 review/queue/candidate/reminder/device_fault");
        }
    }

    private void validateTemplateVars(String template) {
        Matcher matcher = VAR_PATTERN.matcher(template);
        java.util.Set<String> allowed = java.util.Set.of(
                "farmerName", "topic", "startTime", "venueName", "bookingNo",
                "reviewComment", "recommendedTime", "deadline", "position", "deviceName");
        while (matcher.find()) {
            if (!allowed.contains(matcher.group(1))) {
                throw BusinessException.badRequest("模板变量 ${" + matcher.group(1) + "} 不受支持");
            }
        }
    }
}
