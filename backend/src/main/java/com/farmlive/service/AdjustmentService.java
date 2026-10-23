package com.farmlive.service;

import com.farmlive.auth.CurrentUser;
import com.farmlive.common.BusinessException;
import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import com.farmlive.repo.AdjustmentRepository;
import com.farmlive.repo.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 预约调整：农户提交/撤回，直播管理员审批。
 * 规则：开播前 24 小时外申请；同一预约只能有一条待审批；通过后先锁新资源再释放旧资源。
 */
@Service
public class AdjustmentService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdjustmentRepository repository;
    private final BookingRepository bookingRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public AdjustmentService(AdjustmentRepository repository,
                             BookingRepository bookingRepository,
                             AuditService auditService,
                             NotificationService notificationService) {
        this.repository = repository;
        this.bookingRepository = bookingRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional
    public Dtos.AdjustmentView submit(CurrentUser user, Long bookingId, Dtos.AdjustmentCreateRequest request) {
        BookingRepository.BookingRow booking = ownedBooking(user, bookingId);
        if (!"approved".equals(booking.status())) {
            throw BusinessException.conflict("BOOKING_STATUS_INVALID", "只有已通过的预约可以申请调整");
        }
        if (Duration.between(LocalDateTime.now(), booking.startTime()).toHours() < 24) {
            throw BusinessException.conflict("ADJUST_WINDOW_CLOSED", "开播前 24 小时内不能申请调整");
        }
        if (repository.hasPending(bookingId)) {
            throw BusinessException.conflict("ADJUST_PENDING", "该预约已有待审批的调整申请");
        }
        LocalDateTime start = parseTime(request.newStartTime());
        LocalDateTime end = parseTime(request.newEndTime());
        validateSlot(start, end);
        validateResources(request.newVenueId(), request.newDeviceId(), start, end);

        Long id = repository.insert(generateNo(), bookingId, request.newVenueId(), request.newDeviceId(),
                start, end, request.reason().trim());
        auditService.record(user, "BOOKING_ADJUST_SUBMIT",
                "预约 " + booking.bookingNo() + " 申请调整到 " + start.format(DATE_TIME));
        return repository.findViewById(id)
                .orElseThrow(() -> BusinessException.notFound("调整申请创建失败"));
    }

    public PageResult<Dtos.AdjustmentView> myList(CurrentUser user, String status, int page, int size) {
        if (user.farmerId() == null) {
            throw BusinessException.forbidden("当前账号未关联农户");
        }
        return repository.listByFarmer(user.farmerId(), status, page, size);
    }

    @Transactional
    public Dtos.AdjustmentView withdraw(CurrentUser user, Long id) {
        Dtos.AdjustmentView view = repository.findViewById(id)
                .orElseThrow(() -> BusinessException.notFound("调整申请不存在"));
        if (!view.farmerId().equals(user.farmerId())) {
            throw BusinessException.notFound("调整申请不存在");
        }
        if (!"pending".equals(view.status())) {
            throw BusinessException.conflict("ADJUST_STATUS_INVALID", "当前状态不能撤回");
        }
        repository.updateStatus(id, "withdrawn", "农户撤回", LocalDateTime.now());
        auditService.record(user, "BOOKING_ADJUST_WITHDRAW", "撤回调整申请 " + view.requestNo());
        return repository.findViewById(id).orElseThrow();
    }

    public PageResult<Dtos.AdjustmentView> liveList(String status, String keyword, int page, int size) {
        return repository.listForReview(status, keyword, page, size);
    }

    @Transactional
    public Dtos.AdjustmentView review(CurrentUser user, Long id, Dtos.AdjustmentReviewRequest request) {
        Dtos.AdjustmentView view = repository.findViewById(id)
                .orElseThrow(() -> BusinessException.notFound("调整申请不存在"));
        if (!"pending".equals(view.status())) {
            throw BusinessException.conflict("ADJUST_STATUS_INVALID", "当前申请已经处理");
        }
        BookingRepository.BookingRow booking = bookingRepository.findRowById(view.bookingId())
                .orElseThrow(() -> BusinessException.notFound("预约不存在"));
        if (!"approved".equals(booking.status())) {
            throw BusinessException.conflict("BOOKING_STATUS_INVALID", "原预约不是已通过状态");
        }
        String result = request.result().trim();
        String comment = request.comment() == null ? null : request.comment().trim();
        if ("approve".equals(result)) {
            if (Duration.between(LocalDateTime.now(), booking.startTime()).toHours() < 24) {
                throw BusinessException.conflict("ADJUST_WINDOW_CLOSED", "开播前 24 小时内不能调整");
            }
            LocalDateTime start = parseTime(view.newStartTime());
            LocalDateTime end = parseTime(view.newEndTime());
            validateSlot(start, end);
            validateResources(view.newVenueId(), view.newDeviceId(), start, end);

            bookingRepository.deleteLocks(booking.id());
            for (LocalDateTime slot = start; slot.isBefore(end); slot = slot.plusHours(1)) {
                try {
                    bookingRepository.insertLock(view.newVenueId(), view.newDeviceId(), slot, booking.id());
                } catch (org.springframework.dao.DuplicateKeyException ex) {
                    throw BusinessException.conflict("BOOKING_CONFLICT", "新时段资源已被占用");
                }
            }
            bookingRepository.updateResources(booking.id(), view.newVenueId(), view.newDeviceId(), start, end);
            repository.updateStatus(id, "approved", comment, LocalDateTime.now());
            auditService.record(user, "BOOKING_ADJUST_APPROVE", "通过调整申请 " + view.requestNo());
            notificationService.notifyFarmer(view.farmerId(), "review", "adjustment_approved",
                    "adjustment_approved:" + view.requestNo(), Map.of(
                            "farmerName", view.farmerName() == null ? "" : view.farmerName(),
                            "bookingNo", view.bookingNo() == null ? "" : view.bookingNo(),
                            "startTime", view.newStartTime() == null ? "" : view.newStartTime(),
                            "venueName", view.newVenueName() == null ? "" : view.newVenueName()
                    ));
        } else if ("reject".equals(result)) {
            if (comment == null || comment.length() < 5) {
                throw BusinessException.badRequest("驳回意见至少 5 个字");
            }
            repository.updateStatus(id, "rejected", comment, LocalDateTime.now());
            auditService.record(user, "BOOKING_ADJUST_REJECT", "驳回调整申请 " + view.requestNo());
            notificationService.notifyFarmer(view.farmerId(), "review", "adjustment_rejected",
                    "adjustment_rejected:" + view.requestNo(), Map.of(
                            "farmerName", view.farmerName() == null ? "" : view.farmerName(),
                            "bookingNo", view.bookingNo() == null ? "" : view.bookingNo(),
                            "reviewComment", comment
                    ));
        } else {
            throw BusinessException.badRequest("审核结果只能是 approve 或 reject");
        }
        return repository.findViewById(id).orElseThrow();
    }

    private BookingRepository.BookingRow ownedBooking(CurrentUser user, Long bookingId) {
        BookingRepository.BookingRow booking = bookingRepository.findRowById(bookingId)
                .orElseThrow(() -> BusinessException.notFound("预约不存在"));
        if (user.farmerId() == null || !user.farmerId().equals(booking.farmerId())) {
            throw BusinessException.notFound("预约不存在");
        }
        return booking;
    }

    private void validateResources(Long venueId, Long deviceId, LocalDateTime start, LocalDateTime end) {
        if (!bookingRepository.venueAvailable(venueId)) {
            throw BusinessException.conflict("VENUE_UNAVAILABLE", "新场地不可用");
        }
        if (!bookingRepository.deviceAvailableInVenue(deviceId, venueId)) {
            throw BusinessException.conflict("DEVICE_VENUE_MISMATCH", "新设备不属于该场地或不可用");
        }
        if (bookingRepository.venueHasOverlap(venueId, start, end)
                || bookingRepository.deviceHasOverlap(deviceId, start, end)) {
            throw BusinessException.conflict("BOOKING_CONFLICT", "新时段资源已被占用");
        }
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
        if (start.isBefore(now.plusHours(2))) {
            throw BusinessException.badRequest("开始时间至少晚于当前 2 小时");
        }
        if (start.isAfter(now.plusDays(30))) {
            throw BusinessException.badRequest("最多提前 30 天预约");
        }
    }

    private LocalDateTime parseTime(String value) {
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME);
        } catch (Exception ex) {
            throw BusinessException.badRequest("时间格式必须是 yyyy-MM-dd HH:mm:ss");
        }
    }

    private String generateNo() {
        return "ADJ" + DATE_TIME.format(LocalDateTime.now()).replaceAll("[- :]", "")
                + ThreadLocalRandom.current().nextInt(100, 1000);
    }
}
