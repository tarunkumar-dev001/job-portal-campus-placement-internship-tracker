package com.example.demo.entity;

import java.time.LocalDate;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "placement_drives")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlacementDrive {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "college_id", nullable = false)
    private College college;
    @Column(nullable = false)
    private String title;
    private String companyName;
    private String jobTitle;
    private String driveType;
    private String description;
    private Double minCgpa;
    private Integer maxBacklogs;
    @Column(nullable = false)
    private LocalDate registrationDeadline;
    private LocalDate driveDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DriveStatus status = DriveStatus.DRAFT;
    @Column(nullable = false)
    private Long createdByOfficerId;

    public enum DriveStatus { DRAFT, OPEN, CLOSED, COMPLETED, CANCELLED }
}
