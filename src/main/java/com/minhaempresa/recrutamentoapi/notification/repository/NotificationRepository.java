package com.minhaempresa.recrutamentoapi.notification.repository;

import com.minhaempresa.recrutamentoapi.notification.entity.Notification;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserId(UUID userId, Pageable pageable);

    /** Only if it belongs to the user: another user's notification is "not found". */
    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndReadAtIsNull(UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM Notification n WHERE (n.readAt IS NOT NULL AND n.createdAt < :readBefore) OR n.createdAt < :allBefore")
    int deleteExpired(@Param("readBefore") Instant readBefore, @Param("allBefore") Instant allBefore);
}
