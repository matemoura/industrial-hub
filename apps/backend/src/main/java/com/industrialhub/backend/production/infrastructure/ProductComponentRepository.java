package com.industrialhub.backend.production.infrastructure;

import com.industrialhub.backend.production.domain.ProductComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductComponentRepository extends JpaRepository<ProductComponent, UUID> {

    /** Retorna todos os componentes ativos de um produto pai (por dynamicsCode). */
    @Query("SELECT pc FROM ProductComponent pc " +
           "JOIN FETCH pc.componentProduct cp " +
           "WHERE pc.parentProduct.dynamicsCode = :parentCode AND pc.active = true " +
           "ORDER BY pc.level, cp.dynamicsCode")
    List<ProductComponent> findByParentProductCode(@Param("parentCode") String parentCode);

    /**
     * Retorna todos os componentes ativos de todos os produtos — pré-carregado antes do loop MRP.
     * ADR-044 Decisão 3 — evita N+1 no MrpCalculationService.
     */
    @Query("SELECT pc FROM ProductComponent pc " +
           "JOIN FETCH pc.parentProduct pp " +
           "JOIN FETCH pc.componentProduct cp " +
           "WHERE pc.active = true")
    List<ProductComponent> findAllActive();

    /**
     * ADR-044 Decisão 2 — substituição total por produto pai na importação.
     * Deleta todos os componentes existentes do produto antes de inserir os novos.
     */
    @Modifying
    @Query("DELETE FROM ProductComponent pc WHERE pc.parentProduct.dynamicsCode = :parentCode")
    void deleteByParentProductCode(@Param("parentCode") String parentCode);
}
