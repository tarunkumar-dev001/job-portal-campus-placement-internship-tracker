package com.example.demo.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @NotBlank
    @Column(nullable = false)
    private String companyName;

    @Column(nullable = false)
    private Double minCgpa;

    @Column(nullable = false)
    private Integer maxBacklogs;

    @Column(nullable = false)
    private LocalDate deadline;

    @Column(nullable = false)
    private Long recruiterId;

    private String location;
    private String jobType;
    private String requiredSkills;
    private Double salaryMin;
    private Double salaryMax;
    private Boolean internship;
    @Builder.Default
    @Column(nullable = false)
    private boolean published = false;
    @Builder.Default
    @Column(nullable = false)
    private boolean closed = false;
}
