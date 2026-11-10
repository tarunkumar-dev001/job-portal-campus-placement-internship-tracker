package com.example.demo.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.example.demo.entity.InterviewSchedule;

public interface InterviewScheduleRepository extends JpaRepository<InterviewSchedule, Long> {
    List<InterviewSchedule> findByApplicationStudentId(Long studentId);
    List<InterviewSchedule> findByApplicationJobId(Long jobId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InterviewSchedule i where i.id = :id")
    java.util.Optional<InterviewSchedule> findByIdForUpdate(@Param("id") Long id);
}
