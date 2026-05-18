package com.industrialhub.backend.maintenance.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "maintenance_schedule", indexes = {
    @Index(name = "idx_schedule_equipment", columnList = "equipment_id"),
    @Index(name = "idx_schedule_next_run",  columnList = "next_run_at"),
    @Index(name = "idx_schedule_active",    columnList = "active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkOrderPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleRecurrence recurrence;

    private Integer dayOfWeek;   // 1=SEG … 7=DOM; nulo quando recurrence != WEEKLY
    private Integer dayOfMonth;  // 1–28; nulo quando recurrence != MONTHLY

    private LocalDate nextRunAt;
    private LocalDate lastRunAt;

    @Builder.Default
    private boolean active = true;

    @Column(nullable = false, length = 50)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
