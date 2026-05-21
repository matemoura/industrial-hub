package com.industrialhub.backend.common.infrastructure;

import com.industrialhub.backend.common.domain.UserPlant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserPlantRepository extends JpaRepository<UserPlant, UUID> {

    List<UserPlant> findByUserId(UUID userId);

    List<UserPlant> findByPlantId(UUID plantId);

    boolean existsByUserIdAndPlantId(UUID userId, UUID plantId);

    @Modifying
    void deleteByUserIdAndPlantId(UUID userId, UUID plantId);

    @Modifying
    void deleteByUserId(UUID userId);

    @Query("SELECT up.plant.id FROM UserPlant up WHERE up.user.username = :username")
    List<UUID> findPlantIdsByUsername(@Param("username") String username);

    @Query("SELECT up.plant.id FROM UserPlant up WHERE up.user.id = :userId")
    List<UUID> findPlantIdsByUserId(@Param("userId") UUID userId);
}
