package com.example.demo.repository;

import java.util.List;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.entity.Job;

public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findByMinCgpaLessThanEqual(Double cgpa);

    List<Job> findByDeadlineGreaterThanEqual(LocalDate date);

    List<Job> findByRecruiterId(Long recruiterId);

    Page<Job> findByDeadlineGreaterThanEqual(LocalDate date, Pageable pageable);

    List<Job> findByPublishedTrueAndClosedFalseAndDeadlineGreaterThanEqual(LocalDate date);
}
