package com.farmlive.service;

import com.farmlive.auth.CurrentUser;
import com.farmlive.common.BusinessException;
import com.farmlive.dto.Dtos;
import com.farmlive.repo.BookingRepository;
import com.farmlive.repo.QueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QueueReviewFlowTest {
    private final QueueRepository queues = mock(QueueRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AuditService audit = mock(AuditService.class);
    private final QueueService queueService = new QueueService(queues, bookings, notifications);
    private final BookingService bookingService = new BookingService(bookings, queueService, notifications, audit);
    private final LocalDateTime start = LocalDateTime.now().plusDays(4).withMinute(0).withSecond(0).withNano(0);
    private final LocalDateTime end = start.plusHours(2);
    private final CurrentUser user = new CurrentUser(3L, "farmer", "farmer", "farmer", "农户", 7L, "农户", false);

    @BeforeEach
    void setup() {
        var row = new QueueRepository.QueueRow(1L, "Q-1", 7L, 11L, 5L, start, end, 1, "candidate", start.minusHours(24));
        when(queues.findRowById(1L)).thenReturn(Optional.of(row));
        when(queues.pickAvailableDevice(5L, start, end)).thenReturn(Optional.of(9L));
        when(queues.findViewById(1L)).thenReturn(Optional.of(mock(Dtos.QueueView.class)));
        when(bookings.confirmQueuedBooking(11L, 9L)).thenReturn(1);
    }

    @Test
    void confirmingCandidateReservesSlotsAndSubmitsForReview() {
        queueService.confirm(user, 1L);
        verify(bookings).confirmQueuedBooking(11L, 9L);
        verify(bookings).insertLock(5L, 9L, start, 11L);
        verify(bookings).insertLock(5L, 9L, start.plusHours(1), 11L);
        verify(bookings, never()).updateReview(eq(11L), eq("approved"), any(), any());
        verify(queues).markConfirmed(1L, 9L, 11L);
    }

    @Test
    void confirmingDoesNotProceedWhenLinkedBookingHasChanged() {
        when(bookings.confirmQueuedBooking(11L, 9L)).thenReturn(0);
        assertThrows(BusinessException.class, () -> queueService.confirm(user, 1L));
        verify(bookings, never()).insertLock(anyLong(), anyLong(), any(), anyLong());
        verify(queues, never()).markConfirmed(anyLong(), anyLong(), anyLong());
    }

    @Test
    void approvalReusesExistingCandidateLocksAndCreatesMissingOnes() {
        stubPendingBooking();
        when(bookings.ownsLock(5L, 9L, start, 11L)).thenReturn(true);
        when(bookings.ownsLock(5L, 9L, start.plusHours(1), 11L)).thenReturn(false);
        bookingService.review(11L, new Dtos.BookingReviewRequest("approve", "通过", null));
        verify(bookings, never()).insertLock(5L, 9L, start, 11L);
        verify(bookings).insertLock(5L, 9L, start.plusHours(1), 11L);
        verify(bookings).updateReview(11L, "approved", "通过", null);
    }

    @Test
    void rejectionReleasesLocksAndPromotesNextCandidate() {
        stubPendingBooking();
        String recommended = start.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        bookingService.review(11L, new Dtos.BookingReviewRequest("reject", "审核未通过原因", recommended));
        verify(bookings).updateReview(eq(11L), eq("rejected"), anyString(), any());
        verify(bookings).deleteLocks(11L);
        verify(queues).hasCandidate(5L, start);
    }

    private void stubPendingBooking() {
        when(bookings.findRowById(11L)).thenReturn(Optional.of(
                new BookingRepository.BookingRow(11L, "BK-1", 7L, 5L, 9L, start, end, "topic", "pending_review")));
        Dtos.BookingView view = mock(Dtos.BookingView.class);
        when(view.id()).thenReturn(11L);
        when(view.farmerId()).thenReturn(7L);
        when(view.status()).thenReturn("approved");
        when(bookings.findViewById(11L)).thenReturn(Optional.of(view));
    }
}
