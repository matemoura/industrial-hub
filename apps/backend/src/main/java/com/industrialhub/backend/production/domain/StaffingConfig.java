package com.industrialhub.backend.production.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** ADR-030 Decisão 5 — singleton de configuração de turno para cálculo de staffing */
@Entity
@Table(name = "staffing_config")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Horas de trabalho por turno (padrão: 8h) */
    @Column(name = "shift_hours", nullable = false)
    private Integer shiftHours;

    /** Número de turnos por dia (1, 2 ou 3) */
    @Column(name = "shifts_per_day", nullable = false)
    private Integer shiftsPerDay;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
