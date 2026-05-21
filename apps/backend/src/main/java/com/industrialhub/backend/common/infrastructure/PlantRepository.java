package com.industrialhub.backend.common.infrastructure;

import com.industrialhub.backend.common.domain.Plant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlantRepository extends JpaRepository<Plant, UUID> {

    Optional<Plant> findByCode(String code);

    List<Plant> findByActiveTrueOrderByNameAsc();

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    Optional<Plant> findByIsDefaultTrue();
}
