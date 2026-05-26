package com.industrialhub.backend.common.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_dashboard_config",
       uniqueConstraints = @UniqueConstraint(name = "uk_user_dashboard_username", columnNames = "username"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDashboardConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String widgetsJson;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
