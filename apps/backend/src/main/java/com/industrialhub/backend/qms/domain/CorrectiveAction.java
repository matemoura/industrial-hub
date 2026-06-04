package com.industrialhub.backend.qms.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "corrective_action")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorrectiveAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "non_conformance_id", nullable = false)
    private NonConformance nonConformance;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(nullable = false, length = 50)
    private String responsible;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, updatable = false)
    private LocalDate createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionStatus status;

    private LocalDateTime completedAt;

    @Column(length = 50)
    private String completedBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, columnDefinition = "varchar(20) default 'CORRECTIVE'")
    @Builder.Default
    private ActionType type = ActionType.CORRECTIVE;

    @Column(columnDefinition = "text")
    private String rootCauseConfirmed;

    @Column(columnDefinition = "text")
    private String preventiveMeasure;

    private LocalDate effectivenessCheckDate;

    @Column(length = 255)
    private String effectivenessCheckedBy;

    @Column(columnDefinition = "text")
    private String effectivenessResult;
}
