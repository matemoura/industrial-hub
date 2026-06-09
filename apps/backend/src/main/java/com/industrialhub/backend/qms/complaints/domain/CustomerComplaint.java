package com.industrialhub.backend.qms.complaints.domain;

import com.industrialhub.backend.qms.domain.NcSeverity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customer_complaint", indexes = {
    @Index(name = "idx_complaint_status", columnList = "status"),
    @Index(name = "idx_complaint_reported_date", columnList = "reported_date"),
    @Index(name = "idx_complaint_product_code", columnList = "product_code"),
    @Index(name = "idx_complaint_reported_to_anvisa", columnList = "reported_to_anvisa")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerComplaint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintSource source;

    @Column(name = "product_code", length = 50)
    private String productCode;

    @Column(name = "batch_number", length = 50)
    private String batchNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NcSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintStatus status;

    @Column(name = "reported_date", nullable = false)
    private LocalDate reportedDate;

    @Column(name = "reported_by", nullable = false, length = 200)
    private String reportedBy;

    @Column(name = "assigned_to", nullable = false, length = 100)
    private String assignedTo;

    @Column(name = "investigation_summary", columnDefinition = "TEXT")
    private String investigationSummary;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "corrective_action", columnDefinition = "TEXT")
    private String correctiveAction;

    @Column(name = "reported_to_anvisa", nullable = false)
    @Builder.Default
    private boolean reportedToAnvisa = false;

    @Column(name = "anvisa_report_date")
    private LocalDate anvisaReportDate;

    @Column(name = "anvisa_report_number", length = 50)
    private String anvisaReportNumber;

    @Column(name = "linked_nc_id")
    private UUID linkedNcId;

    @Column(name = "linked_capa_id")
    private UUID linkedCapaId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
