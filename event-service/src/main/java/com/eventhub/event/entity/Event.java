package com.eventhub.event.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.eventhub.event.entity.enums.EventStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter 
@Setter 
@AllArgsConstructor 
@NoArgsConstructor 
@Builder 
@Entity 
@Table (name = "events")
public class Event extends BaseEntity {

    @Id 
    @UuidGenerator 
    @Column (name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

   // Khóa ngoại mềm trỏ sang Auth DB: users.id (Keycloak UUID)
    @Column(name = "organizer_id", nullable = false, length = 36)
    private String organizerId;

    // Khóa ngoại vật lý nội bộ trỏ đến bảng categories
    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    @JoinColumn (name = "category_id", nullable = false)
    private Category category;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Khóa ngoại vật lý nội bộ trỏ đến bảng locations
    @Enumerated (EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private EventStatus status =  EventStatus.DRAFT;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;
}
