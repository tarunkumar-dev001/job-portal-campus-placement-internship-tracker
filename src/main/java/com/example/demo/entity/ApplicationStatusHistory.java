package com.example.demo.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "application_status_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApplicationStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Application.Status status;
    @Column(nullable = false)
    private String changedBy;
    @Column(nullable = false, updatable = false)
    private Instant changedAt;
    private String note;
}
