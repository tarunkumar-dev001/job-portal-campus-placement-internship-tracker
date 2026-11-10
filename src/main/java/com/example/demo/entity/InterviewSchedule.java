package com.example.demo.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "interview_schedules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InterviewSchedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;
    @Column(nullable = false)
    private Instant scheduledAt;
    @Column(nullable = false)
    private String mode;
    private String locationOrLink;
    private String notes;
    private String feedback;
    @Column(nullable = false)
    private Instant createdAt;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private Status status = Status.SCHEDULED;
    private Instant cancelledAt;
    public enum Status { SCHEDULED, RESCHEDULED, COMPLETED, CANCELLED }
}
