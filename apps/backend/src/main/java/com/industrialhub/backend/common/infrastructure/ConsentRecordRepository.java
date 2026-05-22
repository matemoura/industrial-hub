package com.industrialhub.backend.common.infrastructure;

import com.industrialhub.backend.common.domain.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {
    Optional<ConsentRecord> findByUsername(String username);
    boolean existsByUsername(String username);
}
