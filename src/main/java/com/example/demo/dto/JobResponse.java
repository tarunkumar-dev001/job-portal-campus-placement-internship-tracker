package com.example.demo.dto;

import java.time.LocalDate;

import com.example.demo.entity.Job;

public record JobResponse(
        Long id,
        String title,
        String description,
        String companyName,
        Double minCgpa,
        Integer maxBacklogs,
        LocalDate deadline,
        String location,
        String jobType,
        String requiredSkills,
        Double salaryMin,
        Double salaryMax,
        Boolean internship,
        boolean published,
        boolean closed) {

    public static JobResponse from(Job job) {
        return new JobResponse(job.getId(), job.getTitle(), job.getDescription(), job.getCompanyName(),
                job.getMinCgpa(), job.getMaxBacklogs(), job.getDeadline(), job.getLocation(),
                job.getJobType(), job.getRequiredSkills(), job.getSalaryMin(), job.getSalaryMax(),
                job.getInternship(), job.isPublished(), job.isClosed());
    }
}
