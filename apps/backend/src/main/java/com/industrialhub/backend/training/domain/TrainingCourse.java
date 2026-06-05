package com.industrialhub.backend.training.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "training_course",
    uniqueConstraints = @UniqueConstraint(name = "uk_training_course_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30, unique = true)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TrainingCategory category;

    @Column(nullable = false)
    private Integer durationHours;

    private Integer validityMonths;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "training_course_roles",
        joinColumns = @JoinColumn(name = "course_id"))
    @Column(name = "role", length = 30)
    @Builder.Default
    private Set<String> requiredForRoles = new HashSet<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
