package com.farmlive.service;

import com.farmlive.auth.CurrentUser;
import com.farmlive.common.BusinessException;
import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import com.farmlive.repo.BookingRepository;
import com.farmlive.repo.SalesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 销售录入：只对已完成场次录入、修改、删除，修改和删除留日志。
 */
@Service
public class SalesService {
    private final SalesRepository repository;
    private final BookingRepository bookingRepository;
    private final AuditService auditService;

    public SalesService(SalesRepository repository, BookingRepository bookingRepository,
                        AuditService auditService) {
        this.repository = repository;
        this.bookingRepository = bookingRepository;
        this.auditService = auditService;
    }

    public PageResult<Dtos.SalesView> list(Long bookingId, String keyword, int page, int size) {
        return repository.page(bookingId, keyword, page, size);
    }

    @Transactional
    public Dtos.SalesView create(CurrentUser user, Dtos.SalesRequest request) {
        BookingRepository.BookingRow booking = completedBooking(request.bookingId());
        checkQuantity(request.quantity());
        Long id = repository.insert(booking.id(), request.productName().trim(),
                blankToNull(request.spec()), request.quantity(), request.unit().trim(), user.id());
        auditService.record(user, "SALES_CREATE",
                "为预约 " + booking.bookingNo() + " 录入 " + request.productName().trim());
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public Dtos.SalesView update(CurrentUser user, Long id, Dtos.SalesRequest request) {
        Dtos.SalesView existing = repository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("销售记录不存在"));
        completedBooking(request.bookingId());
        checkQuantity(request.quantity());
        repository.update(id, request.productName().trim(), blankToNull(request.spec()),
                request.quantity(), request.unit().trim());
        auditService.record(user, "SALES_UPDATE", "修改销售记录 " + existing.id());
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public void delete(CurrentUser user, Long id) {
        Dtos.SalesView existing = repository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("销售记录不存在"));
        repository.delete(id);
        auditService.record(user, "SALES_DELETE", "删除销售记录 " + existing.id());
    }

    private BookingRepository.BookingRow completedBooking(Long bookingId) {
        BookingRepository.BookingRow booking = bookingRepository.findRowById(bookingId)
                .orElseThrow(() -> BusinessException.notFound("预约不存在"));
        if (!"completed".equals(booking.status())) {
            throw BusinessException.conflict("BOOKING_NOT_COMPLETED", "只有已完成场次可以录入销售");
        }
        return booking;
    }

    private void checkQuantity(Double quantity) {
        if (quantity == null || quantity <= 0) {
            throw BusinessException.badRequest("数量必须大于 0");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
