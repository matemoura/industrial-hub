package com.industrialhub.backend.common.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "alert_threshold")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 50)
    private AlertMetric metric;

    @Column(nullable = false)
    private Double threshold;

    @Builder.Default
    @Column(nullable = false)
    private boolean emailEnabled = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 50)
    private String createdBy;

    @UpdateTimestamp
    @Column
    private LocalDateTime updatedAt;
}
