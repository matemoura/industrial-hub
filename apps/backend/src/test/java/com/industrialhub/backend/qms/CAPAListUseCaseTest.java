package com.industrialhub.backend.qms;

import com.industrialhub.backend.qms.application.dto.CAPASummaryResponse;
import com.industrialhub.backend.qms.application.usecase.ListCAPAsUseCase;
import com.industrialhub.backend.qms.domain.ActionStatus;
import com.industrialhub.backend.qms.domain.ActionType;
import com.industrialhub.backend.qms.infrastructure.CorrectiveActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CAPAListUseCaseTest {

    @Mock
    private CorrectiveActionRepository correctiveActionRepository;

    private ListCAPAsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListCAPAsUseCase(correctiveActionRepository);
    }

    @Test
    void listCapas_filterByType_returnsOnlyPreventive() {
        UUID ncId = UUID.randomUUID();
        CAPASummaryResponse summary = new CAPASummaryResponse(
                UUID.randomUUID(),
                ncId,
                "NC Test",
                "Preventive measure",
                ActionType.PREVENTIVE,
                ActionStatus.PENDING,
                "user",
                LocalDate.now().plusDays(10),
                null
        );

        Page<CAPASummaryResponse> page = new PageImpl<>(List.of(summary));
        when(correctiveActionRepository.findAllCapas(
                eq(ActionType.PREVENTIVE), any(), any(), any())).thenReturn(page);

        Page<CAPASummaryResponse> result = useCase.execute(
                ActionType.PREVENTIVE, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).type()).isEqualTo(ActionType.PREVENTIVE);
    }

    @Test
    void listCapas_pageable_passedThrough() {
        Pageable pageable = PageRequest.of(2, 5);
        when(correctiveActionRepository.findAllCapas(any(), any(), any(), any()))
                .thenReturn(Page.empty());

        useCase.execute(null, null, null, pageable);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(correctiveActionRepository).findAllCapas(any(), any(), any(), pageableCaptor.capture());

        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }
}
