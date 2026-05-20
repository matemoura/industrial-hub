package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.CreateShiftRequest;
import com.industrialhub.backend.common.application.dto.ShiftResponse;
import com.industrialhub.backend.common.domain.Shift;
import com.industrialhub.backend.common.infrastructure.ShiftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CreateShiftUseCase {

    private final ShiftRepository shiftRepository;

    public CreateShiftUseCase(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    @Transactional
    public ShiftResponse execute(CreateShiftRequest request) {
        // Validação: turno não-noturno exige endTime > startTime
        if (!request.overnight() && !request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException(
                    "endTime deve ser posterior a startTime para turno não-noturno");
        }

        // Verificar sobreposição com turnos ativos
        List<Shift> activeShifts = shiftRepository.findAllByActiveTrueOrderByStartTime();
        Shift candidate = Shift.builder()
                .name(request.name())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .overnight(request.overnight())
                .build();

        for (Shift existing : activeShifts) {
            if (overlaps(existing, candidate)) {
                throw new IllegalStateException(
                        "Turno sobrepõe turno existente: " + existing.getName());
            }
        }

        Shift saved = shiftRepository.save(candidate);
        return ShiftResponse.from(saved);
    }

    /**
     * Verifica sobreposição entre dois turnos usando intervalos em minutos do dia.
     * Para turnos noturnos, desdobra em dois intervalos: [start, 1440) e [0, end).
     */
    static boolean overlaps(Shift a, Shift b) {
        List<int[]> rangesA = toMinuteRanges(a);
        List<int[]> rangesB = toMinuteRanges(b);

        for (int[] ra : rangesA) {
            for (int[] rb : rangesB) {
                if (rangesIntersect(ra, rb)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<int[]> toMinuteRanges(Shift s) {
        int start = s.getStartTime().toSecondOfDay() / 60;
        int end = s.getEndTime().toSecondOfDay() / 60;

        List<int[]> ranges = new ArrayList<>();
        if (!s.isOvernight()) {
            ranges.add(new int[]{start, end});
        } else {
            // Desdobra overnight em [start, 1440) e [0, end)
            if (start < 1440) {
                ranges.add(new int[]{start, 1440});
            }
            if (end > 0) {
                ranges.add(new int[]{0, end});
            }
        }
        return ranges;
    }

    /**
     * Verifica intersecção de dois intervalos semi-abertos [a0, a1) e [b0, b1).
     */
    private static boolean rangesIntersect(int[] ra, int[] rb) {
        return ra[0] < rb[1] && rb[0] < ra[1];
    }
}
