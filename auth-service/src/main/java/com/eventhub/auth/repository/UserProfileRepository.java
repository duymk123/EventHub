package com.eventhub.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eventhub.auth.entity.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByKeycloakSub(String keycloakSub);

    boolean existsByPhoneNumber(String phoneNumber);

}
