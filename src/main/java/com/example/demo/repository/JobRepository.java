package com.example.demo.repository;

import java.util.List;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.entity.Job;

public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findByMinCgpaLessThanEqual(Double cgpa);

    List<Job> findByDeadlineGreaterThanEqual(LocalDate date);

    List<Job> findByRecruiterId(Long recruiterId);

    Page<Job> findByDeadlineGreaterThanEqual(LocalDate date, Pageable pageable);

    List<Job> findByPublishedTrueAndClosedFalseAndDeadlineGreaterThanEqual(LocalDate date);

    @Query("select count(job) from Job job where job.internship is null or job.internship = false")
    long countNonInternshipJobs();

    long countByInternshipTrue();

    @Query("select count(job) from Job job where job.published = true and job.closed = false "
            + "and job.deadline >= :today and (job.internship is null or job.internship = false)")
    long countActiveNonInternshipJobs(@Param("today") LocalDate today);

    @Query("select count(job) from Job job where job.published = true and job.closed = false "
            + "and job.deadline >= :today and job.internship = true")
    long countActiveInternships(@Param("today") LocalDate today);

    @Query("select count(job) from Job job where job.recruiterId = :recruiterId "
            + "and (job.internship is null or job.internship = false)")
    long countNonInternshipJobsByRecruiter(@Param("recruiterId") Long recruiterId);

    long countByRecruiterIdAndInternshipTrue(Long recruiterId);

    @Query("select count(job) from Job job where job.recruiterId = :recruiterId "
            + "and job.published = true and job.closed = false and job.deadline >= :today")
    long countActiveJobsByRecruiter(@Param("recruiterId") Long recruiterId, @Param("today") LocalDate today);
}
