package com.industrialhub.backend.training.infrastructure;

import com.industrialhub.backend.training.domain.TrainingCourse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TrainingCourseRepository extends JpaRepository<TrainingCourse, UUID> {

    Page<TrainingCourse> findAllByOrderByCodeAsc(Pageable pageable);

    List<TrainingCourse> findByActiveTrue();

    @Query("""
        SELECT c FROM TrainingCourse c
        WHERE c.active = true
        AND EXISTS (
            SELECT r FROM c.requiredForRoles r WHERE r = :role
        )
    """)
    List<TrainingCourse> findActiveByRequiredRole(@Param("role") String role);

    boolean existsByCode(String code);
}
