package com.example.demo.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "drive_registrations", uniqueConstraints = @UniqueConstraint(columnNames = {"drive_id", "student_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DriveRegistration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drive_id", nullable = false)
    private PlacementDrive drive;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistrationStatus status = RegistrationStatus.REGISTERED;
    @Column(nullable = false, updatable = false)
    private Instant registeredAt;
    private Double offeredPackage;
    private String feedback;

    public enum RegistrationStatus { REGISTERED, ELIGIBLE, NOT_ELIGIBLE, SHORTLISTED, SELECTED, REJECTED }
}
