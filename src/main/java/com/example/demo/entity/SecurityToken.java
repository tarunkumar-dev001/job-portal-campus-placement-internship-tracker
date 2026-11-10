package com.example.demo.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "security_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SecurityToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    @Column(nullable = false, unique = true)
    private String tokenHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant usedAt;

    public enum Type { EMAIL_VERIFICATION, PASSWORD_RESET }
}
