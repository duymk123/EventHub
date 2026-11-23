package com.eventhub.order.repository;

import com.eventhub.order.common.PaymentStatus;
import com.eventhub.order.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Kèm order để so key đã dùng cho đơn nào (ngoài transaction). */
    @Query("select p from Payment p join fetch p.order where p.idempotencyKey = :key")
    Optional<Payment> findWithOrderByIdempotencyKey(@Param("key") String idempotencyKey);

    /** IPN tìm payment theo vnp_TxnRef. */
    Optional<Payment> findByProviderAndProviderTxnRef(String provider, String providerTxnRef);

    List<Payment> findByOrderIdAndStatusOrderByCreatedAtAsc(UUID orderId, PaymentStatus status);

    /** C21: payment SUCCEEDED không phải payment đã làm đơn PAID, mới nhất trước. */
    @Query("select p from Payment p join fetch p.order o where p.status = com.eventhub.order.common.PaymentStatus.SUCCEEDED "
            + "and (o.paymentId is null or o.paymentId <> p.id) and p.reconciledAt is null "
            + "order by p.updatedAt desc, p.id")
    List<Payment> findNeedingReconciliation(Pageable page);

    @Query("select p.order.id from Payment p where p.id = :id")
    Optional<UUID> findOrderIdById(@Param("id") UUID id);

    /** Đọc bản mới nhất sau khi đã khóa đơn (thứ tự khóa luôn là order rồi payment). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);
}
