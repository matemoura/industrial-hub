package com.industrialhub.backend.production.infrastructure;

import com.industrialhub.backend.production.domain.ProductFamily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductFamilyRepository extends JpaRepository<ProductFamily, UUID> {
    Optional<ProductFamily> findByCode(String code);
    List<ProductFamily> findByActiveTrueOrderByNameAsc();
}
