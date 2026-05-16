package com.industrialhub.backend.qms.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "root_cause_analysis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RootCauseAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nc_id", nullable = false, unique = true)
    private NonConformance nonConformance;

    @Column(nullable = false, length = 500)
    private String why1;

    @Column(length = 500)
    private String answer1;

    @Column(length = 500)
    private String why2;

    @Column(length = 500)
    private String answer2;

    @Column(length = 500)
    private String why3;

    @Column(length = 500)
    private String answer3;

    @Column(length = 500)
    private String why4;

    @Column(length = 500)
    private String answer4;

    @Column(length = 500)
    private String why5;

    @Column(length = 500)
    private String answer5;

    @Column(length = 1000)
    private String rootCause;

    @Column(nullable = false, length = 50)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
