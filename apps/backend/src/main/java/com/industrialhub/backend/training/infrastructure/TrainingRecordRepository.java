package com.industrialhub.backend.training.infrastructure;

import com.industrialhub.backend.training.domain.TrainingRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TrainingRecordRepository extends JpaRepository<TrainingRecord, UUID> {

    List<TrainingRecord> findByUsername(String username);

    @Query("""
        SELECT r FROM TrainingRecord r
        WHERE (:username IS NULL OR r.username = :username)
        AND (:courseId IS NULL OR r.course.id = :courseId)
        AND (:passed IS NULL OR r.passed = :passed)
        ORDER BY r.recordedAt DESC
    """)
    Page<TrainingRecord> findWithFilters(
        @Param("username") String username,
        @Param("courseId") UUID courseId,
        @Param("passed") Boolean passed,
        Pageable pageable
    );

    @Query("""
        SELECT r FROM TrainingRecord r
        WHERE r.course.id IN :courseIds
    """)
    List<TrainingRecord> findByCourseIdIn(@Param("courseIds") List<UUID> courseIds);

    @Query("""
        SELECT r FROM TrainingRecord r
        WHERE r.expiresAt IS NOT NULL
        AND r.expiresAt BETWEEN :start AND :end
        AND r.passed = true
    """)
    List<TrainingRecord> findExpiringBetween(
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );

    @Query("""
        SELECT r FROM TrainingRecord r
        WHERE r.expiresAt IS NOT NULL
        AND r.expiresAt < :today
        AND r.passed = true
    """)
    List<TrainingRecord> findExpired(@Param("today") LocalDate today);
}
