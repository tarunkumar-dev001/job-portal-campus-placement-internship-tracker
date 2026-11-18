package com.example.demo.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.example.demo.entity.ApplicationStatusHistory;
import com.example.demo.entity.Application;

public interface ApplicationStatusHistoryRepository
        extends JpaRepository<ApplicationStatusHistory, Long> {
    List<ApplicationStatusHistory> findByApplicationIdOrderByChangedAtAscIdAsc(Long applicationId);

    List<ApplicationStatusHistory> findTop10ByApplicationStudentIdOrderByChangedAtDescIdDesc(Long studentId);

    @Query("select history.note, count(distinct history.application.id) from ApplicationStatusHistory history "
            + "where history.status = :status and history.note is not null and trim(history.note) <> '' "
            + "and history.application.student.id in (select profile.user.id from StudentProfile profile "
            + "where profile.college.id = :collegeId) group by history.note")
    List<Object[]> countRejectionReasonsByCollege(@Param("collegeId") Long collegeId,
            @Param("status") Application.Status status);

    @Query("select count(distinct history.application.student.id) from ApplicationStatusHistory history "
            + "where history.status = :status and history.application.student.id in "
            + "(select profile.user.id from StudentProfile profile where profile.college.id = :collegeId)")
    long countDistinctShortlistedStudentsByCollege(@Param("collegeId") Long collegeId,
            @Param("status") Application.Status status);
}
