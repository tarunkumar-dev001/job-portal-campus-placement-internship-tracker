package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "companies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Company {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String name;
    private String industry;
    private String website;
    private String location;
    @Column(columnDefinition = "TEXT")
    private String description;
    private String logoUrl;
    @Column(nullable = false)
    private boolean verified;
    @Column(nullable = false)
    private Long ownerUserId;
}
