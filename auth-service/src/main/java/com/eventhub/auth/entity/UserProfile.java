package com.eventhub.auth.entity;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter 
@Setter 
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table (name = "user_profiles")
@Builder 
public class UserProfile extends BaseEntity {
    // id nội bộ trong EventHub != sub (UUID)
    @Id 
    @UuidGenerator
    @JdbcTypeCode (SqlTypes.CHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    // ID của người dùng trong Keycloak (sub) - để đồng bộ với Keycloak
    @Column(name = "keycloak_sub", length = 255, nullable = false, unique = true)
    private String keycloakSub;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column (name = "phone_number", length = 20, unique = true)
    private String phoneNumber;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

}
