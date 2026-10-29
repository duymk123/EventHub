package com.eventhub.notification.repository;

import com.eventhub.notification.common.NotificationChannel;
import com.eventhub.notification.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    /**
     * Tra cứu theo cặp (code, channel) duy nhất.
     */
    Optional<NotificationTemplate> findByCodeAndChannel(String code, NotificationChannel channel);

    /**
     * Danh sách admin, sắp xếp theo code rồi channel.
     */
    List<NotificationTemplate> findAllByOrderByCodeAscChannelAsc();
}
