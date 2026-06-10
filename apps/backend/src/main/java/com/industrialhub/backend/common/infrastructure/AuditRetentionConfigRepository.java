package com.industrialhub.backend.common.infrastructure;

import com.industrialhub.backend.common.domain.AuditRetentionConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRetentionConfigRepository extends JpaRepository<AuditRetentionConfig, Long> {
}
