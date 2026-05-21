package com.industrialhub.backend.maintenance.infrastructure;

import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.domain.EquipmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EquipmentRepository extends JpaRepository<Equipment, UUID> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    Optional<Equipment> findByIdAndActiveTrue(UUID id);

    List<Equipment> findByActiveTrueOrderByNameAsc();

    List<Equipment> findByActiveTrueAndTypeOrderByNameAsc(EquipmentType type);

    List<Equipment> findByActiveTrueAndStatusOrderByNameAsc(EquipmentStatus status);

    List<Equipment> findByActiveTrueAndTypeAndStatusOrderByNameAsc(EquipmentType type, EquipmentStatus status);

    @Query("SELECT COUNT(e) FROM Equipment e WHERE e.active = true")
    long countByActiveTrue();

    @Query("""
        SELECT e FROM Equipment e
        WHERE e.active = true
          AND e.plant.id IN :plantIds
        ORDER BY e.name ASC
        """)
    List<Equipment> findActiveByPlantIds(@Param("plantIds") List<UUID> plantIds);
}
