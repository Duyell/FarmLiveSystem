package com.farmlive.service;

import com.farmlive.auth.CurrentUser;
import com.farmlive.common.BusinessException;
import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import com.farmlive.repo.BookingRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 预约业务：提交、冲突锁、取消、审核。
 * 事务写在服务层，控制器只做参数校验和调用。
 */
@Service
public class BookingService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BookingRepository repository;
    private final QueueService queueService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public BookingService(BookingRepository repository, QueueService queueService,
                          NotificationService notificationService, AuditService auditService) {
        this.repository = repository;
        this.queueService = queueService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Transactional
    public Dtos.BookingView submit(CurrentUser user, Dtos.BookingCreateRequest request) {
        if (user.farmerId() == null) {
            throw BusinessException.badRequest("当前账号未关联农户，不能提交预约");
        }
        LocalDateTime start = parseTime(request.startTime(), "开始时间格式必须是 yyyy-MM-dd HH:mm:ss");
        LocalDateTime end = parseTime(request.endTime(), "结束时间格式必须是 yyyy-MM-dd HH:mm:ss");
        validateSlot(start, end);

        if (!repository.venueAvailable(request.venueId())) {
            throw BusinessException.badRequest("场地不存在或不可用");
        }
        if (!repository.deviceAvailableInVenue(request.deviceId(), request.venueId())) {
            throw BusinessException.badRequest("设备不存在、不可用或不属于所选场地");
        }
        if (repository.farmerHasOverlap(user.farmerId(), start, end)) {
            throw BusinessException.conflict("BOOKING_DUPLICATE", "同一时段您已有预约，请更换时间");
        }
        if (repository.venueHasOverlap(request.venueId(), start, end)
                || repository.deviceHasOverlap(request.deviceId(), start, end)) {
            throw BusinessException.conflict("BOOKING_CONFLICT", "所选时段的场地或设备已被占用");
        }

        String bookingNo = generateBookingNo();
        Long id;
        try {
            id = repository.insertBooking(bookingNo, user.farmerId(), request.venueId(),
                    request.deviceId(), start, end, request.topic().trim());
            for (Dtos.BookingProductItem item : productsOf(request)) {
                repository.insertProduct(id, item);
            }
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("BOOKING_CONFLICT", "所选时段的场地或设备已被占用");
        }
        return repository.findViewById(id)
                .orElseThrow(() -> BusinessException.notFound("预约创建失败"));
    }

    @Transactional
    public Dtos.BookingView review(Long bookingId, Dtos.BookingReviewRequest request) {
        BookingRepository.BookingRow booking = repository.findRowById(bookingId)
                .orElseThrow(() -> BusinessException.notFound("预约不存在"));
        if (!"pending_review".equals(booking.status())) {
            throw BusinessException.conflict("BOOKING_STATUS_INVALID", "当前状态不能审核");
        }
        String result = request.result().trim();
        String comment = request.comment() == null ? null : request.comment().trim();
        if ("approve".equals(result)) {
            try {
                for (LocalDateTime slot = booking.startTime(); slot.isBefore(booking.endTime()); slot = slot.plusHours(1)) {
                    repository.insertLock(booking.venueId(), booking.deviceId(), slot, booking.id());
                }
            } catch (DuplicateKeyException ex) {
                throw BusinessException.conflict("BOOKING_CONFLICT", "该时段资源已被占用，建议使用推荐时间另约");
            }
            repository.updateReview(booking.id(), "approved", comment, null);
        } else if ("reject".equals(result)) {
            if (comment == null || comment.length() < 5) {
                throw BusinessException.badRequest("驳回意见至少 5 个字");
            }
            LocalDateTime recommended = parseTime(request.recommendedTime(), "推荐时间格式必须是 yyyy-MM-dd HH:mm:ss");
            if (!recommended.isAfter(LocalDateTime.now())) {
                throw BusinessException.badRequest("推荐时间必须晚于当前时间");
            }
            repository.updateReview(booking.id(), "rejected", comment, recommended);
        } else {
            throw BusinessException.badRequest("审核结果只能是 approve 或 reject");
        }
        auditService.recordCurrent("BOOKING_REVIEW",
                "预约 " + booking.bookingNo() + " 审核为 " + result);
        Dtos.BookingView view = repository.findViewById(booking.id())
                .orElseThrow(() -> BusinessException.notFound("预约不存在"));
        notifyReviewResult(view);
        return view;
    }

    private void notifyReviewResult(Dtos.BookingView view) {
        boolean approved = "approved".equals(view.status());
        notificationService.notifyFarmer(view.farmerId(), "review",
                approved ? "review_approved" : "review_rejected",
                "review:" + view.id(),
                Map.of(
                        "farmerName", view.farmerName() == null ? "" : view.farmerName(),
                        "topic", view.topic() == null ? "" : view.topic(),
                        "startTime", view.startTime() == null ? "" : view.startTime(),
                        "venueName", view.venueName() == null ? "" : view.venueName(),
                        "reviewComment", view.reviewComment() == null ? "无" : view.reviewComment(),
                        "recommendedTime", view.recommendedTime() == null ? "无" : view.recommendedTime()
                ));
    }

    @Transactional
    public Dtos.BookingView cancel(CurrentUser user, Long bookingId, String reason) {
        if (user.farmerId() == null) {
            throw BusinessException.badRequest("当前账号未关联农户");
        }
        BookingRepository.BookingRow booking = repository.findRowById(bookingId)
                .orElseThrow(() -> BusinessException.notFound("预约不存在"));
        if (!user.farmerId().equals(booking.farmerId())) {
            throw BusinessException.notFound("预约不存在");
        }
        if (!"pending_review".equals(booking.status()) && !"approved".equals(booking.status())) {
            throw BusinessException.conflict("BOOKING_STATUS_INVALID", "当前状态不能取消");
        }
        if (Duration.between(LocalDateTime.now(), booking.startTime()).toHours() < 24) {
            throw BusinessException.conflict("CANCEL_WINDOW_CLOSED", "开播前 24 小时内不能取消，可申请调整");
        }
        repository.updateCancel(booking.id(),
                reason == null || reason.isBlank() ? "农户主动取消" : reason.trim());
        repository.deleteLocks(booking.id());
        auditService.record(user, "BOOKING_CANCEL", "取消预约 " + booking.bookingNo());
        queueService.promoteNext(booking.venueId(), booking.startTime(), booking.endTime());
        return repository.findViewById(booking.id())
                .orElseThrow(() -> BusinessException.notFound("预约不存在"));
    }

    @Transactional
    public Dtos.BookingView complete(CurrentUser user, Long bookingId, String status) {
        BookingRepository.BookingRow booking = repository.findRowById(bookingId)
                .orElseThrow(() -> BusinessException.notFound("预约不存在"));
        if (!"approved".equals(booking.status())) {
            throw BusinessException.conflict("BOOKING_STATUS_INVALID", "只有已通过的预约可以标记完成");
        }
        if (!"completed".equals(status) && !"no_show".equals(status)) {
            throw BusinessException.badRequest("状态只能是 completed 或 no_show");
        }
        if (LocalDateTime.now().isBefore(booking.endTime())) {
            throw BusinessException.conflict("BOOKING_NOT_ENDED", "场次结束后才能标记完成或未到场");
        }
        repository.updateStatus(booking.id(), status);
        auditService.record(user, "completed".equals(status) ? "BOOKING_COMPLETE" : "BOOKING_NO_SHOW",
                "预约 " + booking.bookingNo() + " 标记为 " + status);
        return repository.findViewById(booking.id())
                .orElseThrow(() -> BusinessException.notFound("预约不存在"));
    }

    public PageResult<Dtos.BookingView> myBookings(CurrentUser user, String status, int page, int size) {
        if (user.farmerId() == null) {
            throw BusinessException.badRequest("当前账号未关联农户");
        }
        return repository.listByFarmer(user.farmerId(), status, page, size);
    }

    public Dtos.BookingView myBooking(CurrentUser user, Long bookingId) {
        Dtos.BookingView view = repository.findViewById(bookingId)
                .orElseThrow(() -> BusinessException.notFound("预约不存在"));
        if (user.farmerId() == null || !user.farmerId().equals(view.farmerId())) {
            throw BusinessException.notFound("预约不存在");
        }
        return view;
    }

    public PageResult<Dtos.BookingView> listForReview(String status, String keyword, int page, int size) {
        return repository.listForReview(status, keyword, page, size);
    }

    public List<Dtos.AvailableVenueView> availableResources(String countyCode, String date,
                                                            String startTime, String endTime) {
        if (date == null || date.isBlank() || startTime == null || startTime.isBlank()
                || endTime == null || endTime.isBlank()) {
            return repository.listAvailableVenues(countyCode, List.of(), List.of());
        }
        LocalDateTime start = parseTime(date.trim() + " " + normalizeTime(startTime), "时间格式不正确");
        LocalDateTime end = parseTime(date.trim() + " " + normalizeTime(endTime), "时间格式不正确");
        if (!start.isBefore(end)) {
            throw BusinessException.badRequest("开始时间必须早于结束时间");
        }
        return repository.listAvailableVenues(countyCode,
                repository.busyVenueIds(start, end),
                repository.busyDeviceIds(start, end));
    }

    // ---------------- 内部 ----------------

    private List<Dtos.BookingProductItem> productsOf(Dtos.BookingCreateRequest request) {
        if (request.products() == null) return List.of();
        return request.products().stream()
                .filter(item -> item.productName() != null && !item.productName().isBlank())
                .toList();
    }

    private void validateRecommendedSlot(BookingRepository.BookingRow booking, LocalDateTime recommended) {
        long minutes = Duration.between(booking.startTime(), booking.endTime()).toMinutes();
        if (minutes <= 0) {
            throw BusinessException.badRequest("原预约时段不正确");
        }
        LocalDateTime recommendedEnd = recommended.plusMinutes(minutes);
        validateSlot(recommended, recommendedEnd);
        if (repository.venueHasOverlap(booking.venueId(), recommended, recommendedEnd)
                || repository.deviceHasOverlap(booking.deviceId(), recommended, recommendedEnd)) {
            throw BusinessException.conflict("BOOKING_CONFLICT", "推荐时间已被占用，请换一个时间");
        }
    }

    private void validateSlot(LocalDateTime start, LocalDateTime end) {
        if (start.getMinute() != 0 || start.getSecond() != 0
                || end.getMinute() != 0 || end.getSecond() != 0) {
            throw BusinessException.badRequest("BOOKING_TIME_INVALID", "时段必须为整点");
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw BusinessException.badRequest("BOOKING_TIME_INVALID", "时段不能跨天");
        }
        long hours = Duration.between(start, end).toHours();
        if (hours < 1 || hours > 4) {
            throw BusinessException.badRequest("BOOKING_TIME_INVALID", "时长必须是 1-4 小时");
        }
        LocalDateTime now = LocalDateTime.now();
        if (start.isBefore(now.plusHours(2))) {
            throw BusinessException.badRequest("BOOKING_TIME_INVALID", "开始时间至少晚于当前 2 小时");
        }
        if (start.isAfter(now.plusDays(30))) {
            throw BusinessException.badRequest("BOOKING_TIME_INVALID", "最多提前 30 天预约");
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

    private String normalizeTime(String time) {
        return time.trim().length() == 5 ? time.trim() + ":00" : time.trim();
    }

    private String generateBookingNo() {
        return "BK" + DATE_TIME.format(LocalDateTime.now()).replaceAll("[- :]", "")
                + ThreadLocalRandom.current().nextInt(100, 1000);
    }
}
