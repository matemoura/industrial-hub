package com.industrialhub.backend.common.infrastructure;

import com.industrialhub.backend.common.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByMetricAndCreatedAtAfter(String metric, LocalDateTime since);

    @Query("""
        SELECT n FROM Notification n
        WHERE n.username = :username OR n.username IS NULL
        ORDER BY
            CASE WHEN n.readAt IS NULL THEN 0 ELSE 1 END ASC,
            n.createdAt DESC
    """)
    Page<Notification> findByUsernameOrUsernameIsNullOrderByReadAtAscCreatedAtDesc(
            @Param("username") String username,
            Pageable pageable);

    @Query("""
        SELECT COUNT(n) FROM Notification n
        WHERE (n.username = :username OR n.username IS NULL)
        AND n.readAt IS NULL
    """)
    long countByUsernameOrUsernameIsNullAndReadAtIsNull(@Param("username") String username);

    @Modifying
    @Query("""
        UPDATE Notification n SET n.readAt = :now
        WHERE (n.username = :username OR n.username IS NULL)
        AND n.readAt IS NULL
    """)
    int markAllReadForUser(@Param("username") String username, @Param("now") LocalDateTime now);
}
