package com.eventhub.ticket.repository;

import com.eventhub.ticket.entity.ReservationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReservationItemRepository extends JpaRepository<ReservationItem, UUID> {
}
