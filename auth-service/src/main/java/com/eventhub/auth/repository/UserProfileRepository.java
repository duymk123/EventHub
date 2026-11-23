package com.eventhub.auth.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eventhub.auth.entity.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByKeycloakSub(String keycloakSub);

    /** Một truy vấn cho cả trang user thay vì N truy vấn khi map danh sách cho ADMIN. */
    List<UserProfile> findAllByKeycloakSubIn(Collection<String> keycloakSubs);

    boolean existsByPhoneNumber(String phoneNumber);

}
