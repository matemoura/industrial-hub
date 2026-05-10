package com.industrialhub.backend.oee.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "time_record", indexes = {
        @Index(name = "idx_time_record_worker_date", columnList = "worker_id, profile_date"),
        @Index(name = "idx_time_record_batch", columnList = "batch_id"),
        @Index(name = "idx_time_record_type", columnList = "record_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private ImportBatch batch;

    @Column(name = "worker_id", nullable = false)
    private Long workerId;

    @Column(name = "worker_name", nullable = false)
    private String workerName;

    @Column(name = "profile_date", nullable = false)
    private LocalDate profileDate;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false)
    private RecordType recordType;

    @Column(name = "reference")
    private String reference;

    @Column(name = "operation_number")
    private Integer operationNumber;

    @Column(name = "work_identifier")
    private String workIdentifier;

    @Column(name = "description")
    private String description;

    // Horas do registro (decimal, ex: 1.57 = 1h34min)
    @Column(name = "hours", precision = 10, scale = 4)
    private BigDecimal hours;
}
