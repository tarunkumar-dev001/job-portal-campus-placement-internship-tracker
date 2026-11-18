package com.example.demo.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import jakarta.persistence.LockModeType;
import com.example.demo.entity.InterviewSchedule;

public interface InterviewScheduleRepository extends JpaRepository<InterviewSchedule, Long> {
    List<InterviewSchedule> findByApplicationStudentId(Long studentId);
    List<InterviewSchedule> findByApplicationJobId(Long jobId);

    @Query("select count(interview.id) from InterviewSchedule interview "
            + "where interview.application.job.recruiterId = :recruiterId "
            + "and interview.status in :statuses "
            + "and interview.scheduledAt >= :now")
    long countUpcomingByRecruiter(@Param("recruiterId") Long recruiterId,
            @Param("statuses") List<InterviewSchedule.Status> statuses, @Param("now") Instant now);

    @Query("select count(interview.id) from InterviewSchedule interview "
            + "where interview.application.student.id = :studentId "
            + "and interview.status in :statuses "
            + "and interview.scheduledAt >= :now")
    long countUpcomingByStudent(@Param("studentId") Long studentId,
            @Param("statuses") List<InterviewSchedule.Status> statuses, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InterviewSchedule i where i.id = :id")
    java.util.Optional<InterviewSchedule> findByIdForUpdate(@Param("id") Long id);
}
