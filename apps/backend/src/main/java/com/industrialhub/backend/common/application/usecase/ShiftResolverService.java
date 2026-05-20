package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.domain.Shift;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ShiftResolverService {

    /**
     * Retorna o primeiro turno ativo que cobre o horário {@code now}.
     *
     * <p>Turno normal ({@code overnight=false}): cobre {@code [startTime, endTime)}.
     * Turno noturno ({@code overnight=true}): cobre {@code [startTime, 24h)} ∪ {@code [0, endTime)}.
     */
    public Optional<Shift> resolveCurrentShift(List<Shift> activeShifts, LocalTime now) {
        return activeShifts.stream()
                .filter(s -> covers(s, now))
                .findFirst();
    }

    /**
     * Verifica sobreposição entre dois turnos usando intervalos em minutos do dia.
     * Para turnos noturnos, desdobra em dois intervalos: [start, 1440) e [0, end).
     * SH-52: movido de CreateShiftUseCase para reutilização.
     */
    public boolean overlaps(Shift a, Shift b) {
        List<int[]> rangesA = toMinuteRanges(a);
        List<int[]> rangesB = toMinuteRanges(b);
        for (int[] ra : rangesA) {
            for (int[] rb : rangesB) {
                if (rangesIntersect(ra, rb)) return true;
            }
        }
        return false;
    }

    private List<int[]> toMinuteRanges(Shift s) {
        int start = s.getStartTime().toSecondOfDay() / 60;
        int end   = s.getEndTime().toSecondOfDay()   / 60;
        List<int[]> ranges = new ArrayList<>();
        if (!s.isOvernight()) {
            ranges.add(new int[]{start, end});
        } else {
            if (start < 1440) ranges.add(new int[]{start, 1440});
            if (end > 0)      ranges.add(new int[]{0, end});
        }
        return ranges;
    }

    private boolean rangesIntersect(int[] ra, int[] rb) {
        return ra[0] < rb[1] && rb[0] < ra[1];
    }

    private boolean covers(Shift s, LocalTime now) {
        if (!s.isOvernight()) {
            // turno normal: [startTime, endTime)
            return !now.isBefore(s.getStartTime()) && now.isBefore(s.getEndTime());
        } else {
            // turno noturno: [startTime, 24h) OR [0, endTime)
            return !now.isBefore(s.getStartTime()) || now.isBefore(s.getEndTime());
        }
    }
}
