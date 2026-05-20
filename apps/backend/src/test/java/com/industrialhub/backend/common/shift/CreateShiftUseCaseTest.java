package com.industrialhub.backend.common.shift;

import com.industrialhub.backend.common.application.dto.CreateShiftRequest;
import com.industrialhub.backend.common.application.usecase.CreateShiftUseCase;
import com.industrialhub.backend.common.application.dto.ShiftResponse;
import com.industrialhub.backend.common.domain.Shift;
import com.industrialhub.backend.common.infrastructure.ShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateShiftUseCaseTest {

    @Mock
    private ShiftRepository shiftRepository;

    private CreateShiftUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateShiftUseCase(shiftRepository);
    }

    // (a) turno diurno criado com sucesso
    @Test
    void shouldCreateDaytimeShiftSuccessfully() {
        when(shiftRepository.findAllByActiveTrueOrderByStartTime()).thenReturn(List.of());
        Shift saved = Shift.builder()
                .id(UUID.randomUUID())
                .name("Turno A")
                .startTime(LocalTime.of(6, 0))
                .endTime(LocalTime.of(14, 0))
                .overnight(false)
                .active(true)
                .build();
        when(shiftRepository.save(any())).thenReturn(saved);

        CreateShiftRequest request = new CreateShiftRequest("Turno A",
                LocalTime.of(6, 0), LocalTime.of(14, 0), false);

        ShiftResponse response = useCase.execute(request);

        assertThat(response.name()).isEqualTo("Turno A");
        assertThat(response.overnight()).isFalse();
        assertThat(response.active()).isTrue();
        verify(shiftRepository).save(any());
    }

    // (b) turno noturno 22:00–06:00 criado com sucesso
    @Test
    void shouldCreateOvernightShiftSuccessfully() {
        when(shiftRepository.findAllByActiveTrueOrderByStartTime()).thenReturn(List.of());
        Shift saved = Shift.builder()
                .id(UUID.randomUUID())
                .name("Turno Noturno")
                .startTime(LocalTime.of(22, 0))
                .endTime(LocalTime.of(6, 0))
                .overnight(true)
                .active(true)
                .build();
        when(shiftRepository.save(any())).thenReturn(saved);

        CreateShiftRequest request = new CreateShiftRequest("Turno Noturno",
                LocalTime.of(22, 0), LocalTime.of(6, 0), true);

        ShiftResponse response = useCase.execute(request);

        assertThat(response.name()).isEqualTo("Turno Noturno");
        assertThat(response.overnight()).isTrue();
        verify(shiftRepository).save(any());
    }

    // (c) sobreposição lança IllegalStateException
    @Test
    void shouldThrowWhenShiftOverlaps() {
        Shift existing = Shift.builder()
                .id(UUID.randomUUID())
                .name("Turno A")
                .startTime(LocalTime.of(6, 0))
                .endTime(LocalTime.of(14, 0))
                .overnight(false)
                .active(true)
                .build();
        when(shiftRepository.findAllByActiveTrueOrderByStartTime()).thenReturn(List.of(existing));

        // Novo turno 08:00–16:00 sobrepõe Turno A (06:00–14:00)
        CreateShiftRequest request = new CreateShiftRequest("Turno B",
                LocalTime.of(8, 0), LocalTime.of(16, 0), false);

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Turno A");

        verify(shiftRepository, never()).save(any());
    }

    // (d) overnight=false, endTime <= startTime lança IllegalArgumentException
    @Test
    void shouldThrowWhenEndTimeNotAfterStartTimeForDaytimeShift() {
        CreateShiftRequest request = new CreateShiftRequest("Turno X",
                LocalTime.of(14, 0), LocalTime.of(6, 0), false);

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endTime deve ser posterior a startTime");

        verify(shiftRepository, never()).save(any());
        verify(shiftRepository, never()).findAllByActiveTrueOrderByStartTime();
    }
}
