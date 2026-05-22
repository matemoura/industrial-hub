package com.industrialhub.backend.common.auth.infrastructure;

import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    List<User> findByRoleIn(List<Role> roles);
    List<User> findAllByOrderByUsernameAsc();
    long countByRoleAndActiveTrue(Role role);

    /**
     * Finds users deactivated before the given cutoff whose username has not been anonymized yet.
     * Used by DataRetentionService to enforce the 2-year retention window.
     */
    @Query("SELECT u FROM User u WHERE u.deactivatedAt < :cutoff AND u.username NOT LIKE '[usuario-%'")
    List<User> findByDeactivatedAtBeforeAndAnonymizedFalse(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Counts inactive users not yet anonymized — for the dry-run preview endpoint.
     * Also respects the 2-year cutoff so the count matches what executeAll() would process.
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.deactivatedAt < :cutoff AND u.username NOT LIKE '[usuario-%'")
    long countInactiveNotAnonymized(@Param("cutoff") LocalDateTime cutoff);
}
