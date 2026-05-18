package com.industrialhub.backend.qms.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "supplier", indexes = {
    @Index(name = "idx_supplier_code", columnList = "code", unique = true),
    @Index(name = "idx_supplier_active", columnList = "active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String contactEmail;

    @Column(length = 20)
    private String contactPhone;

    @Column(length = 200)
    private String address;

    @Builder.Default
    private boolean active = true;

    private LocalDate onboardedAt;
}
