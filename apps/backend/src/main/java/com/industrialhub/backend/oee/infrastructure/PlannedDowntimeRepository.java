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

    @Query("SELECT pd FROM PlannedDowntime pd LEFT JOIN FETCH pd.equipment e " +
           "WHERE (:dayStart IS NULL OR pd.startAt < :dayEnd) " +
           "AND (:dayEnd IS NULL OR pd.endAt > :dayStart) " +
           "AND (:equipmentId IS NULL OR e.id = :equipmentId) " +
           "ORDER BY pd.startAt DESC, pd.registeredAt DESC")
    List<PlannedDowntime> findByOptionalFilters(
            @Param("dayStart")    LocalDateTime dayStart,
            @Param("dayEnd")      LocalDateTime dayEnd,
            @Param("equipmentId") UUID equipmentId);
}
