package com.farmlive.service;

import com.farmlive.auth.CurrentUser;
import com.farmlive.common.BusinessException;
import com.farmlive.dto.Dtos;
import com.farmlive.repo.BookingRepository;
import com.farmlive.repo.QueueRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 排队与候补业务。
 * 晋升条件：场地该时段无有效预约、至少一台可用设备、早于开播前 24 小时。
 */
@Service
public class QueueService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final QueueRepository queueRepository;
    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;

    public QueueService(QueueRepository queueRepository, BookingRepository bookingRepository,
                        NotificationService notificationService) {
        this.queueRepository = queueRepository;
        this.bookingRepository = bookingRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public Dtos.QueueView join(CurrentUser user, Dtos.QueueCreateRequest request) {
        if (user.farmerId() == null) {
            throw BusinessException.badRequest("当前账号未关联农户，不能加入排队");
        }
        LocalDateTime start = parseTime(request.startTime(), "时间格式必须是 yyyy-MM-dd HH:mm:ss");
        LocalDateTime end = parseTime(request.endTime(), "时间格式必须是 yyyy-MM-dd HH:mm:ss");
        validateSlot(start, end);

        if (!bookingRepository.venueAvailable(request.venueId())) {
            throw BusinessException.badRequest("场地不存在或不可用");
        }
        if (queueRepository.farmerCooledDown(user.farmerId(), request.venueId())) {
            throw BusinessException.conflict("QUEUE_COOLDOWN", "候补确认后又取消，72 小时内不能对该场地排队");
        }
        if (queueRepository.farmerHasActiveInSlot(user.farmerId(), start)) {
            throw BusinessException.conflict("QUEUE_DUPLICATE", "同一时段您已有一个有效排队");
        }
        List<Long> occupiers = queueRepository.slotOccupierIds(request.venueId(), start, end);
        boolean occupiedByOthers = occupiers.stream().anyMatch(id -> !id.equals(user.farmerId()));
        if (!occupiedByOthers) {
            if (occupiers.contains(user.farmerId())) {
                throw BusinessException.badRequest("QUEUE_SELF_OCCUPIED", "该时段是您自己的预约，无需排队");
            }
            throw BusinessException.badRequest("SLOT_NOT_OCCUPIED", "该时段未被预约，可直接预约");
        }

        String farmerNo = queueRepository.farmerNoById(user.farmerId())
                .orElseThrow(() -> BusinessException.badRequest("农户档案不存在"));
        String queueNo = "Q-" + farmerNo + "-" + start.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + String.format("%03d", queueRepository.countTodayForFarmer(user.farmerId(), start) + 1);
        int position = queueRepository.nextPosition(request.venueId(), start);
        Long id = queueRepository.insert(user.farmerId(), request.venueId(), queueNo, start, end, position);
        return queueRepository.findViewById(id)
                .orElseThrow(() -> BusinessException.notFound("排队创建失败"));
    }

    @Transactional
    public Dtos.QueueView confirm(CurrentUser user, Long id) {
        QueueRepository.QueueRow row = ownedRow(user, id);
        if (!"candidate".equals(row.status())) {
            throw BusinessException.conflict("QUEUE_STATUS_INVALID", "只有候补状态能确认");
        }
        Long deviceId = queueRepository.pickAvailableDevice(row.venueId(), row.slotStart(), row.slotEnd())
                .orElseThrow(() -> BusinessException.conflict("QUEUE_NO_DEVICE", "暂无可用设备，候选资格保留至截止时间"));
        Long bookingId = bookingRepository.insertBooking(nextBookingNo(), row.farmerId(), row.venueId(),
                deviceId, row.slotStart(), row.slotEnd(), "候补直播");
        bookingRepository.updateReview(bookingId, "approved", null, null);
        try {
            for (LocalDateTime slot = row.slotStart(); slot.isBefore(row.slotEnd()); slot = slot.plusHours(1)) {
                bookingRepository.insertLock(row.venueId(), deviceId, slot, bookingId);
            }
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("QUEUE_NO_DEVICE", "该时段资源刚被占用，候选资格保留至截止时间");
        }
        queueRepository.markConfirmed(row.id(), deviceId, bookingId);
        return queueRepository.findViewById(row.id())
                .orElseThrow(() -> BusinessException.notFound("排队不存在"));
    }

    @Transactional
    public Dtos.QueueView abandon(CurrentUser user, Long id) {
        QueueRepository.QueueRow row = ownedRow(user, id);
        if (!"queued".equals(row.status()) && !"candidate".equals(row.status())) {
            throw BusinessException.conflict("QUEUE_STATUS_INVALID", "当前状态不能放弃");
        }
        queueRepository.updateStatus(row.id(), "abandoned");
        queueRepository.shiftPositions(row.venueId(), row.slotStart(), row.position());
        if ("candidate".equals(row.status())) {
            promoteNext(row.venueId(), row.slotStart(), row.slotEnd());
        } else {
            notifyNewFirstQueued(row.venueId(), row.slotStart());
        }
        return queueRepository.findViewById(row.id())
                .orElseThrow(() -> BusinessException.notFound("排队不存在"));
    }

    /**
     * 直播管理员关闭整个队列组（同场地同时段全部有效排队置为 closed）。
     */
    @Transactional
    public Dtos.QueueView close(Long id) {
        QueueRepository.QueueRow row = queueRepository.findRowById(id)
                .orElseThrow(() -> BusinessException.notFound("排队不存在"));
        queueRepository.closeGroup(row.venueId(), row.slotStart());
        return queueRepository.findViewById(row.id())
                .orElseThrow(() -> BusinessException.notFound("排队不存在"));
    }

    public List<Dtos.QueueView> listMine(CurrentUser user) {
        if (user.farmerId() == null) {
            throw BusinessException.badRequest("当前账号未关联农户");
        }
        return queueRepository.listByFarmer(user.farmerId());
    }

    /**
     * 时段状态查询：告诉农户该时段是空闲（直接预约）、被他人占用（可排队）、
     * 被自己占用（无需排队）还是处于不可排队窗口。
     */
    public Dtos.SlotStatusView slotStatus(CurrentUser user, Long venueId, String startTime, String endTime) {
        if (user.farmerId() == null) {
            throw BusinessException.badRequest("当前账号未关联农户");
        }
        LocalDateTime start = parseTime(startTime, "时间格式必须是 yyyy-MM-dd HH:mm:ss");
        LocalDateTime end = parseTime(endTime, "时间格式必须是 yyyy-MM-dd HH:mm:ss");
        if (start.getMinute() != 0 || start.getSecond() != 0 || end.getMinute() != 0 || end.getSecond() != 0
                || !start.toLocalDate().equals(end.toLocalDate())
                || Duration.between(start, end).toHours() < 1
                || Duration.between(start, end).toHours() > 4) {
            throw BusinessException.badRequest("时段必须为整点、不跨天且 1-4 小时");
        }
        List<Long> occupiers = queueRepository.slotOccupierIds(venueId, start, end);
        boolean occupied = !occupiers.isEmpty();
        boolean selfOccupied = occupiers.contains(user.farmerId());
        boolean occupiedByOthers = occupiers.stream().anyMatch(id -> !id.equals(user.farmerId()));
        boolean inWindow = Duration.between(LocalDateTime.now(), start).toHours() >= 24;
        boolean cooled = queueRepository.farmerCooledDown(user.farmerId(), venueId);
        boolean duplicated = queueRepository.farmerHasActiveInSlot(user.farmerId(), start);
        boolean canJoin = occupiedByOthers && inWindow && !cooled && !duplicated;

        String reason;
        if (!occupied) {
            reason = "该时段未被预约，可直接预约";
        } else if (occupiedByOthers) {
            reason = "该时段已被他人预约，当前排队 " + queueRepository.activeCountForSlot(venueId, start) + " 人"
                    + (canJoin ? "，可加入排队" : "");
        } else {
            reason = "该时段是您自己的预约，无需排队";
        }
        if (occupiedByOthers && !canJoin) {
            if (!inWindow) reason = "距开播不足 24 小时，不能加入排队";
            else if (cooled) reason = "候补确认后又取消，72 小时内不能对该场地排队";
            else if (duplicated) reason = "同一时段您已有一个有效排队";
        }
        return new Dtos.SlotStatusView(occupied, selfOccupied, canJoin,
                queueRepository.activeCountForSlot(venueId, start), reason);
    }

    public List<Dtos.QueueView> listForLive(Long venueId, String date) {
        if (date == null || date.isBlank()) {
            return queueRepository.listForLive(venueId, null, null);
        }
        LocalDate day = LocalDate.parse(date.trim());
        return queueRepository.listForLive(venueId, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }

    /**
     * 预约取消释放场地后晋升队首；晋升条件不满足则保持队列等待下次触发。
     */
    @Transactional
    public void promoteNext(Long venueId, LocalDateTime slotStart, LocalDateTime slotEnd) {
        if (Duration.between(LocalDateTime.now(), slotStart).toHours() < 24) {
            return;
        }
        if (bookingRepository.venueHasOverlap(venueId, slotStart, slotEnd)) {
            return;
        }
        if (queueRepository.pickAvailableDevice(venueId, slotStart, slotEnd).isEmpty()) {
            return;
        }
        queueRepository.firstQueued(venueId, slotStart).ifPresent(first -> {
            queueRepository.promoteToCandidate(first.id(), slotStart.minusHours(1));
            queueRepository.findViewById(first.id()).ifPresent(view -> notificationService.notifyFarmer(
                    view.farmerId(), "candidate", "candidate_promoted",
                    "candidate:" + view.id(),
                    Map.of(
                            "farmerName", view.farmerName() == null ? "" : view.farmerName(),
                            "venueName", view.venueName() == null ? "" : view.venueName(),
                            "startTime", view.slotStart() == null ? "" : view.slotStart(),
                            "deadline", view.candidateDeadline() == null ? "" : view.candidateDeadline()
                    )));
        });
    }

    /**
     * 顺延后通知新的队首当前顺位。
     */
    private void notifyNewFirstQueued(Long venueId, LocalDateTime slotStart) {
        queueRepository.firstQueued(venueId, slotStart).ifPresent(first ->
                queueRepository.findViewById(first.id()).ifPresent(view -> notificationService.notifyFarmer(
                        view.farmerId(), "queue", "queue_position",
                        "queue:" + view.id(),
                        Map.of(
                                "farmerName", view.farmerName() == null ? "" : view.farmerName(),
                                "venueName", view.venueName() == null ? "" : view.venueName(),
                                "startTime", view.slotStart() == null ? "" : view.slotStart(),
                                "position", String.valueOf(view.position())
                        ))));
    }

    /**
     * 候选超时：过截止时间未确认自动过期并顺延下一位。
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireOverdueCandidates() {
        for (QueueRepository.QueueRow row : queueRepository.expiredCandidates()) {
            queueRepository.updateStatus(row.id(), "expired");
            queueRepository.shiftPositions(row.venueId(), row.slotStart(), row.position());
            promoteNext(row.venueId(), row.slotStart(), row.slotEnd());
            notifyNewFirstQueued(row.venueId(), row.slotStart());
        }
    }

    // ---------------- 内部 ----------------

    private QueueRepository.QueueRow ownedRow(CurrentUser user, Long id) {
        QueueRepository.QueueRow row = queueRepository.findRowById(id)
                .orElseThrow(() -> BusinessException.notFound("排队不存在"));
        if (user.farmerId() == null || !user.farmerId().equals(row.farmerId())) {
            throw BusinessException.notFound("排队不存在");
        }
        return row;
    }

    private void validateSlot(LocalDateTime start, LocalDateTime end) {
        if (start.getMinute() != 0 || start.getSecond() != 0
                || end.getMinute() != 0 || end.getSecond() != 0) {
            throw BusinessException.badRequest("时段必须为整点");
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw BusinessException.badRequest("时段不能跨天");
        }
        long hours = Duration.between(start, end).toHours();
        if (hours < 1 || hours > 4) {
            throw BusinessException.badRequest("时长必须是 1-4 小时");
        }
        LocalDateTime now = LocalDateTime.now();
        if (Duration.between(now, start).toHours() < 24) {
            throw BusinessException.badRequest("开播前 24 小时内不能加入排队");
        }
        if (start.isAfter(now.plusDays(30))) {
            throw BusinessException.badRequest("最多提前 30 天排队");
        }
    }

    private LocalDateTime parseTime(String value, String message) {
        if (value == null || value.isBlank()) {
            throw BusinessException.badRequest(message);
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME);
        } catch (Exception ex) {
            throw BusinessException.badRequest(message);
        }
    }

    private String nextBookingNo() {
        return "BK" + DATE_TIME.format(LocalDateTime.now()).replaceAll("[- :]", "")
                + ThreadLocalRandom.current().nextInt(100, 1000);
    }
}
