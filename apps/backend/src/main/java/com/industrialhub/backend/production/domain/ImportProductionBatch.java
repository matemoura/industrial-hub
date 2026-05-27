package com.industrialhub.backend.production.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "import_production_batch")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportProductionBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductionImportType type;

    @Column(length = 255)
    private String fileName;

    private LocalDateTime importedAt;

    @Column(length = 100)
    private String importedBy;

    private Integer totalRecords;
    private Integer createdRecords;
    private Integer updatedRecords;
    private Integer errorRecords;
}
