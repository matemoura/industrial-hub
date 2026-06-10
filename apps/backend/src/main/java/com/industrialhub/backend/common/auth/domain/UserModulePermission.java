package com.industrialhub.backend.common.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "user_module_permission",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "module"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserModulePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AppModule module;

    private boolean canView;
    private boolean canCreate;
    private boolean canEdit;
    private boolean canDelete;
}
