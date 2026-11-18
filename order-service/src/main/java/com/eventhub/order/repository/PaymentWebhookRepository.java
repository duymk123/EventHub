package com.eventhub.order.repository;

import com.eventhub.order.entity.PaymentWebhook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentWebhookRepository extends JpaRepository<PaymentWebhook, UUID> {
    Optional<PaymentWebhook> findByProviderAndProviderEventId(String provider, String providerEventId);
}
