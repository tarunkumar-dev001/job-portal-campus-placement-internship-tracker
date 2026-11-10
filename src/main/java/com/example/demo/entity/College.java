package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "colleges")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class College {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String code;
    @Column(nullable = false)
    private String name;
    private String city;
    private String state;
    private String university;
    @Column(nullable = false)
    private boolean active = true;
}
