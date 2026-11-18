package com.eventhub.ticket.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ReservationExpiryScheduler {

    private final TicketService ticketService;

    public ReservationExpiryScheduler(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Scheduled(fixedDelayString = "${ticket.reservation-expiry-delay-ms:10000}")
    public void expireReservations() {
        ticketService.expireReservations(Instant.now());
    }
}
