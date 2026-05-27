package com.industrialhub.backend.production.infrastructure;

import com.industrialhub.backend.production.domain.Product;
import com.industrialhub.backend.production.domain.ProductType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByDynamicsCode(String dynamicsCode);

    @Query("SELECT p FROM Product p LEFT JOIN p.family f " +
           "WHERE (:familyCode IS NULL OR f.code = :familyCode) " +
           "AND (:type IS NULL OR p.type = :type) " +
           "AND p.active = :active")
    Page<Product> findFiltered(
            @Param("familyCode") String familyCode,
            @Param("type") ProductType type,
            @Param("active") boolean active,
            Pageable pageable);
}
