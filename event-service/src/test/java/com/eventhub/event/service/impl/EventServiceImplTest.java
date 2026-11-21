package com.eventhub.event.service.impl;

import com.eventhub.event.client.TicketConfigurationClient;
import com.eventhub.event.common.Actor;
import com.eventhub.event.common.BusinessRole;
import com.eventhub.event.common.ErrorCode;
import com.eventhub.event.common.EventStatus;
import com.eventhub.event.common.ReviewDecision;
import com.eventhub.event.dto.request.EventRequest;
import com.eventhub.event.dto.request.ReviewRequest;
import com.eventhub.event.entity.Category;
import com.eventhub.event.entity.Event;
import com.eventhub.event.exception.BusinessException;
import com.eventhub.event.repository.CategoryRepository;
import com.eventhub.event.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");
    private final UUID ownerId = UUID.randomUUID();
    private final Actor owner = new Actor(ownerId, BusinessRole.ORGANIZER);
    private final Actor otherOrganizer = new Actor(UUID.randomUUID(), BusinessRole.ORGANIZER);
    private final Actor admin = new Actor(UUID.randomUUID(), BusinessRole.ADMIN);

    private EventRepository events;
    private CategoryRepository categories;
    private TicketConfigurationClient tickets;
    private EventServiceImpl service;
    private Event event;

    @BeforeEach
    void setUp() {
        events = mock(EventRepository.class);
        categories = mock(CategoryRepository.class);
        tickets = mock(TicketConfigurationClient.class);
        service = new EventServiceImpl(events, categories, tickets, mock(PlatformTransactionManager.class),
                Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Ho_Chi_Minh");
        Category category = Category.builder().id(UUID.randomUUID()).name("Music").build();
        event = Event.builder().id(UUID.randomUUID()).organizerId(ownerId).category(category).title("Show")
                .status(EventStatus.DRAFT).startTime(NOW.plusSeconds(86_400)).endTime(NOW.plusSeconds(90_000))
                .build();
        when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(events.findById(event.getId())).thenReturn(Optional.of(event));
        when(events.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(categories.findById(category.getId())).thenReturn(Optional.of(category));
    }

    @Test
    void nonOwnerCannotChangeEvent() {
        assertCode(() -> service.submit(otherOrganizer, event.getId()), ErrorCode.FORBIDDEN_NOT_OWNER);
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void fullLifecycle() {
        service.submit(owner, event.getId());
        assertThat(event.getStatus()).isEqualTo(EventStatus.SUBMITTED);
        service.review(admin, event.getId(), new ReviewRequest(ReviewDecision.APPROVE, null));
        assertThat(event.getStatus()).isEqualTo(EventStatus.APPROVED);
        when(tickets.isReadyToPublish(event.getId())).thenReturn(true);
        service.publish(owner, event.getId());
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        service.close(owner, event.getId());
        assertThat(event.getStatus()).isEqualTo(EventStatus.CLOSED);
    }

    @Test
    void rejectNeedsReasonAndEditingRejectedEventReturnsToDraft() {
        event.setStatus(EventStatus.SUBMITTED);
        assertCode(() -> service.review(admin, event.getId(), new ReviewRequest(ReviewDecision.REJECT, " ")),
                ErrorCode.VALIDATION_ERROR);
        service.review(admin, event.getId(), new ReviewRequest(ReviewDecision.REJECT, "Thiếu địa điểm"));
        assertThat(event.getStatus()).isEqualTo(EventStatus.REJECTED);
        assertThat(event.getReviewReason()).isEqualTo("Thiếu địa điểm");

        service.update(owner, event.getId(), new EventRequest("Show 2", null, event.getCategory().getId(),
                NOW.plusSeconds(100_000), NOW.plusSeconds(110_000)));
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(event.getReviewReason()).isNull();
    }

    @Test
    void publishBlockedWhenTicketsNotConfigured() {
        event.setStatus(EventStatus.APPROVED);
        when(tickets.isReadyToPublish(event.getId())).thenReturn(false);
        assertCode(() -> service.publish(owner, event.getId()), ErrorCode.TICKET_CONFIGURATION_NOT_READY);
        assertThat(event.getStatus()).isEqualTo(EventStatus.APPROVED);
    }

    @Test
    void publishChecksStateBeforeCallingTicket() {
        assertCode(() -> service.publish(owner, event.getId()), ErrorCode.INVALID_EVENT_STATE);
        verify(tickets, never()).isReadyToPublish(any());
    }

    @Test
    void invalidTimesAndDateRangeRejected() {
        UUID categoryId = event.getCategory().getId();
        assertCode(() -> service.create(owner, new EventRequest("x", null, categoryId, NOW.minusSeconds(1),
                NOW.plusSeconds(10))), ErrorCode.VALIDATION_ERROR);
        assertCode(() -> service.create(owner, new EventRequest("x", null, categoryId, NOW.plusSeconds(20),
                NOW.plusSeconds(10))), ErrorCode.VALIDATION_ERROR);
        assertCode(() -> service.searchPublic(null, null, LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 1),
                org.springframework.data.domain.Pageable.unpaged()), ErrorCode.INVALID_DATE_RANGE);
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(code);
    }
}
