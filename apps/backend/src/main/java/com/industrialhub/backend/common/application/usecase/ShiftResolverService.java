package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.domain.Shift;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
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
