package com.eventhub.auth.entity;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter 
@Setter 
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table (name = "users")
public class User extends BaseEntity {
    @Id 
    @UuidGenerator
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id; // Lưu mã định danh UUID (sub) tự động sinh ra từ Keycloak - để đồng bộ với Keycloak

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "role", length = 50)
    private String role = "CUSTOMER"; // Đồng bộ từ Role khai báo trên Keycloak

    @Column(name = "status", length = 50)
    private String status = "ACTIVE"; // Đồng bộ trạng thái từ Keycloak

}
