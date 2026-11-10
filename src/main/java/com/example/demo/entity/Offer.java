package com.example.demo.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "offers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Offer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(optional = false)
    @JoinColumn(name = "application_id", unique = true)
    private Application application;
    private String packageDetails;
    private Double packageAmount;
    private Instant joiningDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant updatedAt;

    public enum Status { ISSUED, ACCEPTED, DECLINED, WITHDRAWN }
}
