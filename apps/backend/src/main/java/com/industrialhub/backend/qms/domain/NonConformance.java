package com.industrialhub.backend.qms.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "non_conformance", indexes = {
    @Index(name = "idx_nc_status",   columnList = "status"),
    @Index(name = "idx_nc_severity", columnList = "severity"),
    @Index(name = "idx_nc_reported", columnList = "reportedAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NonConformance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NcType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NcSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NcStatus status;

    @Column(nullable = false, length = 50)
    private String reportedBy;

    @Column(nullable = false)
    private LocalDateTime reportedAt;

    private LocalDateTime closedAt;

    @Column(length = 50)
    private String closedBy;

    @OneToMany(mappedBy = "nonConformance", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CorrectiveAction> actions = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @OneToOne(mappedBy = "nonConformance", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private RootCauseAnalysis rca;
}
