package com.industrialhub.backend.training;

import com.industrialhub.backend.common.application.NotificationService;
import com.industrialhub.backend.common.domain.NotificationSeverity;
import com.industrialhub.backend.training.application.usecase.TrainingExpiryAlertUseCase;
import com.industrialhub.backend.training.domain.TrainingCategory;
import com.industrialhub.backend.training.domain.TrainingCourse;
import com.industrialhub.backend.training.domain.TrainingRecord;
import com.industrialhub.backend.training.infrastructure.TrainingRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingExpiryAlertUseCaseTest {

    @Mock private TrainingRecordRepository recordRepository;
    @Mock private NotificationService notificationService;

    private TrainingExpiryAlertUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TrainingExpiryAlertUseCase(recordRepository, notificationService);
    }

    private TrainingRecord buildRecord(String username, LocalDate expiresAt) {
        TrainingCourse course = TrainingCourse.builder()
            .id(UUID.randomUUID()).code("GMP-001").title("Curso GMP")
            .category(TrainingCategory.GMP).durationHours(4)
            .active(true).createdAt(LocalDateTime.now()).build();

        return TrainingRecord.builder()
            .id(UUID.randomUUID()).course(course).username(username)
            .completedAt(LocalDate.now().minusMonths(11)).expiresAt(expiresAt)
            .passed(true).recordedBy("admin").build();
    }

    @Test
    void shouldSendWarningForExpiringRecords() {
        TrainingRecord expiring = buildRecord("joao", LocalDate.now().plusDays(15));
        when(recordRepository.findExpiringBetween(any(), any())).thenReturn(List.of(expiring));
        when(recordRepository.findExpired(any())).thenReturn(List.of());
        when(notificationService.createForUserWithDebounce(anyString(), anyString(), anyString(),
            eq(NotificationSeverity.WARNING), eq(144))).thenReturn(1);

        int count = useCase.execute();

        assertThat(count).isEqualTo(1);
        verify(notificationService).createForUserWithDebounce(
            eq("joao"), contains("vencendo"), anyString(),
            eq(NotificationSeverity.WARNING), eq(144));
    }

    @Test
    void shouldSendDangerForExpiredRecords() {
        TrainingRecord expired = buildRecord("maria", LocalDate.now().minusDays(5));
        when(recordRepository.findExpiringBetween(any(), any())).thenReturn(List.of());
        when(recordRepository.findExpired(any())).thenReturn(List.of(expired));
        when(notificationService.createForUserWithDebounce(anyString(), anyString(), anyString(),
            eq(NotificationSeverity.CRITICAL), eq(24))).thenReturn(1);

        int count = useCase.execute();

        assertThat(count).isEqualTo(1);
        verify(notificationService).createForUserWithDebounce(
            eq("maria"), contains("vencida"), anyString(),
            eq(NotificationSeverity.CRITICAL), eq(24));
    }

    @Test
    void shouldReturnZeroWhenDebounced() {
        TrainingRecord expiring = buildRecord("ana", LocalDate.now().plusDays(10));
        when(recordRepository.findExpiringBetween(any(), any())).thenReturn(List.of(expiring));
        when(recordRepository.findExpired(any())).thenReturn(List.of());
        when(notificationService.createForUserWithDebounce(anyString(), anyString(), anyString(),
            any(), anyInt())).thenReturn(0);

        int count = useCase.execute();

        assertThat(count).isEqualTo(0);
    }

    @Test
    void shouldHandleEmptyRepositories() {
        when(recordRepository.findExpiringBetween(any(), any())).thenReturn(List.of());
        when(recordRepository.findExpired(any())).thenReturn(List.of());

        int count = useCase.execute();

        assertThat(count).isEqualTo(0);
        verifyNoInteractions(notificationService);
    }
}
