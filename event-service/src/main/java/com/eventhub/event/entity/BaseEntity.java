package com.eventhub.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;


@Getter
@Setter
@MappedSuperclass  // Lớp cha trừu tượng, không tạo bảng trong cơ sở dữ liệu
@EntityListeners(AuditingEntityListener.class) // Lắng nghe sự kiện Insert/Update
// @EntityListeners(AuditingEntityListener.class) giúp tự động điền các trường createdAt, updatedAt, createdBy, updatedBy khi thực hiện các thao tác Insert/Update trên các thực thể kế thừa BaseEntity.
public class BaseEntity {
    // Các annotation @CreatedDate, @LastModifiedDate, @CreatedBy, @LastModifiedBy 
    // được sử dụng để đánh dấu các trường trong lớp BaseEntity để tự động điền giá trị khi thực hiện các thao tác Insert/Update trên các thực thể kế thừa BaseEntity.

    @CreatedDate
    @Column(name = "create_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "update_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}

