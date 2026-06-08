package com.industrialhub.backend.common.changes.infrastructure;

import com.industrialhub.backend.common.changes.domain.ChangeEntityType;
import com.industrialhub.backend.common.changes.domain.ChangeRequestLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChangeRequestLinkRepository extends JpaRepository<ChangeRequestLink, UUID> {

    List<ChangeRequestLink> findByChangeRequestId(UUID changeRequestId);

    boolean existsByChangeRequestIdAndEntityTypeAndEntityId(
        UUID changeRequestId, ChangeEntityType entityType, UUID entityId);

    Optional<ChangeRequestLink> findByChangeRequestIdAndEntityTypeAndEntityId(
        UUID changeRequestId, ChangeEntityType entityType, UUID entityId);
}
