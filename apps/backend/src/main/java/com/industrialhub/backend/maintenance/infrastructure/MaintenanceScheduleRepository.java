package com.industrialhub.backend.maintenance.infrastructure;

import com.industrialhub.backend.maintenance.domain.MaintenanceSchedule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceScheduleRepository extends JpaRepository<MaintenanceSchedule, UUID> {

    @EntityGraph(attributePaths = {"equipment"})
    List<MaintenanceSchedule> findByActiveTrueOrderByNextRunAtAsc();

    @EntityGraph(attributePaths = {"equipment"})
    List<MaintenanceSchedule> findByActiveTrueAndEquipmentIdOrderByNextRunAtAsc(UUID equipmentId);

    @EntityGraph(attributePaths = {"equipment"})
    Optional<MaintenanceSchedule> findById(UUID id);

    @EntityGraph(attributePaths = {"equipment"})
    @Query("SELECT s FROM MaintenanceSchedule s WHERE s.active = true AND s.nextRunAt <= :today")
    List<MaintenanceSchedule> findDueSchedules(@Param("today") LocalDate today);
}
