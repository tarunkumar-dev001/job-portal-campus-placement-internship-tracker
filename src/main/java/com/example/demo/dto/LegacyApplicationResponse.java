package com.example.demo.dto;

import java.time.Instant;

import com.example.demo.entity.Application;

public record LegacyApplicationResponse(
        Long id,
        Long studentId,
        String studentEmail,
        Long jobId,
        String jobTitle,
        Application.Status status,
        Instant appliedAt,
        String recruiterFeedback) {

    public static LegacyApplicationResponse from(Application application) {
        return new LegacyApplicationResponse(
                application.getId(),
                application.getStudent().getId(),
                application.getStudent().getEmail(),
                application.getJob().getId(),
                application.getJob().getTitle(),
                application.getStatus(),
                application.getAppliedAt(),
                application.getRecruiterFeedback());
    }
}
