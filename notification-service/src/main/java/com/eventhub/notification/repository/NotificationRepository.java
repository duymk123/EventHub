package com.eventhub.notification.repository;

import com.eventhub.notification.common.NotificationChannel;
import com.eventhub.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Lấy thông báo IN_APP của người dùng hiện tại, phân trang và sắp xếp theo Pageable.
     */
    @Query("SELECT n FROM Notification n " +
            "WHERE n.recipientUserId = :userId AND n.channel = :channel")
    Page<Notification> findByRecipientUserIdAndChannel(
            @Param("userId") UUID userId,
            @Param("channel") NotificationChannel channel,
            Pageable pageable);

    /**
     * Lấy thông báo chưa đọc (readAt IS NULL), cùng ngữ nghĩa với trên.
     */
    @Query("SELECT n FROM Notification n " +
            "WHERE n.recipientUserId = :userId AND n.channel = :channel AND n.readAt IS NULL")
    Page<Notification> findByRecipientUserIdAndChannelAndReadAtIsNull(
            @Param("userId") UUID userId,
            @Param("channel") NotificationChannel channel,
            Pageable pageable);

    /**
     * Kiểm tra quyền sở hữu khi đánh dấu đã đọc — trả về thông báo nếu thuộc về người dùng.
     */
    @Query("SELECT n FROM Notification n " +
            "WHERE n.id = :id AND n.recipientUserId = :userId AND n.channel = :channel")
    Optional<Notification> findByIdAndRecipientUserIdAndChannel(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("channel") NotificationChannel channel);

    /**
     * Đánh dấu đã đọc (idempotent): chỉ đặt readAt khi nó đang NULL.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :at " +
            "WHERE n.id = :id AND n.recipientUserId = :userId AND n.channel = :channel AND n.readAt IS NULL")
    int markReadIfUnread(@Param("id") UUID id,
                         @Param("userId") UUID userId,
                         @Param("channel") NotificationChannel channel,
                         @Param("at") Instant at);

    /**
     * Đếm số thông báo IN_APP chưa đọc của một người dùng.
     */
    @Query("SELECT COUNT(n) FROM Notification n " +
            "WHERE n.recipientUserId = :userId AND n.channel = :channel AND n.readAt IS NULL")
    long countByRecipientUserIdAndChannelAndReadAtIsNull(
            @Param("userId") UUID userId,
            @Param("channel") NotificationChannel channel);

    /**
     * Tra cứu để tránh trùng lặp (dedup).
     */
    Optional<Notification> findByDedupKey(String dedupKey);

    /**
     * Tìm tất cả thông báo của một lô tin nhắn nguồn, sắp xếp theo id.
     */
    List<Notification> findBySourceMessageIdOrderByIdAsc(UUID sourceMessageId);
}
