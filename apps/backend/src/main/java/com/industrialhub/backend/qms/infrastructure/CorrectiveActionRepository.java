package com.industrialhub.backend.qms.infrastructure;

import com.industrialhub.backend.qms.domain.CorrectiveAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CorrectiveActionRepository extends JpaRepository<CorrectiveAction, UUID> {
}
