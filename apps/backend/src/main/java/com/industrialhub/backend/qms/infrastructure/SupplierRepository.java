package com.industrialhub.backend.qms.infrastructure;

import com.industrialhub.backend.qms.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    boolean existsByCode(String code);

    List<Supplier> findAllByActiveTrue();

    Optional<Supplier> findByIdAndActiveTrue(UUID id);
}
