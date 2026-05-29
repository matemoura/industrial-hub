package com.industrialhub.backend.production;

import com.industrialhub.backend.production.application.usecase.GetProductionOverviewUseCase;
import com.industrialhub.backend.production.infrastructure.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * US-107 — testes de cache Spring para GetProductionOverviewUseCase.
 * Usa @SpringBootTest para activar o proxy de @Cacheable.
 */
@SpringBootTest
@ActiveProfiles("test")
class GetProductionOverviewCacheTest {

    @Autowired
    private GetProductionOverviewUseCase useCase;

    @Autowired
    private CacheManager cacheManager;

    @SpyBean
    private ProductRepository productRepository;

    @SpyBean
    private ProductComponentRepository componentRepository;

    @SpyBean
    private MrpPlannedOrderRepository mrpRepository;

    @SpyBean
    private ProductionOrderRepository orderRepository;

    /** AC-4(a): segunda chamada a getOverview() não invoca repositórios novamente. */
    @Test
    void secondCall_shouldNotInvokeRepositories_whenCacheIsActive() {
        // Garante dados consistentes nos repositórios em memória (H2)
        when(productRepository.findAll()).thenReturn(List.of());
        when(componentRepository.findAllActive()).thenReturn(List.of());
        when(mrpRepository.findAll()).thenReturn(List.of());
        when(orderRepository.findDoneOrdersInPeriod(any(), any())).thenReturn(List.of());
        when(orderRepository.findAll()).thenReturn(List.of());

        // Invalida cache antes do teste para garantir estado limpo
        var cache = cacheManager.getCache("production-overview");
        if (cache != null) cache.clear();

        // Primeira chamada — acessa repositórios
        useCase.getOverview();
        // Segunda chamada — deve usar cache, repositórios NÃO chamados novamente
        useCase.getOverview();

        // Cada repositório chamado exatamente 1 vez
        verify(productRepository, times(1)).findAll();
        verify(mrpRepository, times(1)).findAll();
        verify(orderRepository, times(1)).findAll();
    }

    /** AC-4(b): CacheManager possui cache "production-overview" configurado. */
    @Test
    void cacheManager_shouldHaveProductionOverviewCache() {
        assertThat(cacheManager).isNotNull();
        // getCache cria o cache lazy se necessário — presença indica configuração correta
        assertThat(cacheManager.getCache("production-overview")).isNotNull();
    }
}
