package com.example.demo.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String actorEmail;
    @Column(nullable = false)
    private String action;
    private String targetType;
    private String targetId;
    @Column(columnDefinition = "TEXT")
    private String details;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
