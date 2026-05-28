package com.industrialhub.backend.oee.infrastructure;

import com.industrialhub.backend.oee.domain.PlannedDowntime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PlannedDowntimeRepository extends JpaRepository<PlannedDowntime, UUID> {

    /**
     * Busca paradas planejadas cujo startAt cai no dia informado,
     * incluindo paradas de planta inteira (equipment IS NULL).
     */
    @Query("SELECT pd FROM PlannedDowntime pd " +
           "LEFT JOIN FETCH pd.equipment " +
           "WHERE pd.startAt < :dayEnd AND pd.endAt > :dayStart " +
           "AND (pd.equipment.id = :equipmentId OR pd.equipment IS NULL)")
    List<PlannedDowntime> findByDateAndEquipmentIdOrEquipmentIsNull(
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd")   LocalDateTime dayEnd,
            @Param("equipmentId") UUID equipmentId);

    // native query — CAST prevents "could not determine data type of parameter $N"
    // when null LocalDateTime params are passed to PostgreSQL IS NULL checks
    @Query(value = """
        SELECT pd.* FROM planned_downtime pd
        LEFT JOIN equipment e ON e.id = pd.equipment_id
        WHERE (CAST(:dayEnd AS TIMESTAMP) IS NULL OR pd.start_at < CAST(:dayEnd AS TIMESTAMP))
          AND (CAST(:dayStart AS TIMESTAMP) IS NULL OR pd.end_at > CAST(:dayStart AS TIMESTAMP))
          AND (CAST(:equipmentId AS UUID) IS NULL OR e.id = CAST(:equipmentId AS UUID))
        ORDER BY pd.start_at DESC, pd.registered_at DESC
    """, nativeQuery = true)
    List<PlannedDowntime> findByOptionalFilters(
            @Param("dayStart")    LocalDateTime dayStart,
            @Param("dayEnd")      LocalDateTime dayEnd,
            @Param("equipmentId") UUID equipmentId);
}
