package com.industrialhub.backend.maintenance.infrastructure;

import com.industrialhub.backend.maintenance.domain.CalibrationSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalibrationScheduleRepository extends JpaRepository<CalibrationSchedule, UUID> {

    List<CalibrationSchedule> findByActiveTrue();

    List<CalibrationSchedule> findByEquipmentIdAndActiveTrue(UUID equipmentId);

    List<CalibrationSchedule> findByActiveTrueAndNextDueAtBefore(LocalDate date);

    List<CalibrationSchedule> findByActiveTrueAndNextDueAtBetween(LocalDate from, LocalDate to);

    long countByActiveTrue();

    long countByActiveTrueAndNextDueAtBefore(LocalDate today);

    long countByActiveTrueAndNextDueAtBetween(LocalDate today, LocalDate in14);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM CalibrationSchedule s WHERE s.id = :id")
    Optional<CalibrationSchedule> findByIdForUpdate(@Param("id") UUID id);
}
