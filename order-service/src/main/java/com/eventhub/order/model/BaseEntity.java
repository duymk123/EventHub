package com.eventhub.order.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;

/**
 * Base class for all JPA entities that require auditing fields.
 * <p>
 * It provides automatic population of creation / update timestamps and
 * user information when {@code @EnableJpaAuditing} is active.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class) // Lắng nghe sự kiện Insert/Update
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "create_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "update_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by")
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "deleted")
    private boolean deleted = false;
}
