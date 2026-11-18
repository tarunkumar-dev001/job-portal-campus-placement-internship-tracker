package com.example.demo.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.entity.SavedJob;

public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {
    List<SavedJob> findByStudentId(Long studentId);
    Optional<SavedJob> findByStudentIdAndJobId(Long studentId, Long jobId);
    long countByStudentId(Long studentId);
}
