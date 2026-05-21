package com.industrialhub.backend.oee.domain;

import com.industrialhub.backend.common.domain.Plant;
import com.industrialhub.backend.common.domain.Shift;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "import_batch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private Instant importedAt;

    @Column(nullable = false, unique = true)
    private LocalDate periodDate;

    @Column(nullable = false)
    private int totalRecords;

    @Column(nullable = false)
    private int workerCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private Shift shift; // nullable

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plant_id")
    private Plant plant;
}
