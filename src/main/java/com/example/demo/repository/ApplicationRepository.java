package com.example.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.example.demo.entity.Application;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByStudentId(Long studentId);
    long countByStudentId(Long studentId);

    List<Application> findByJobId(Long jobId);

    Optional<Application> findByStudentIdAndJobId(Long studentId, Long jobId);

    @Query("select application.status, count(application.id) from Application application "
            + "group by application.status")
    List<Object[]> countByStatus();

    @Query("select application.status, count(application.id) from Application application "
            + "where application.job.recruiterId = :recruiterId group by application.status")
    List<Object[]> countByStatusForRecruiter(@Param("recruiterId") Long recruiterId);

    @Query("select application.status, count(application.id) from Application application "
            + "where application.student.id = :studentId group by application.status")
    List<Object[]> countByStatusForStudent(@Param("studentId") Long studentId);

    @Query("select application.status, count(application.id) from Application application "
            + "where application.student.id in (select profile.user.id from StudentProfile profile "
            + "where profile.college.id = :collegeId) group by application.status")
    List<Object[]> countByStatusForCollege(@Param("collegeId") Long collegeId);

    @Query("select count(application.id) from Application application "
            + "where application.student.id in (select profile.user.id from StudentProfile profile "
            + "where profile.college.id = :collegeId)")
    long countByStudentCollegeId(@Param("collegeId") Long collegeId);

    @Query("select count(distinct application.student.id) from Application application "
            + "where application.job.recruiterId = :recruiterId")
    long countDistinctApplicantsByRecruiter(@Param("recruiterId") Long recruiterId);

    @Query("select count(distinct application.student.id) from Application application "
            + "where application.job.recruiterId = :recruiterId and application.status = :status")
    long countDistinctApplicantsByRecruiterAndStatus(@Param("recruiterId") Long recruiterId,
            @Param("status") Application.Status status);

    @Query("select count(application.id) from Application application "
            + "where application.student.id in (select profile.user.id from StudentProfile profile "
            + "where profile.college.id = :collegeId) and application.status = :status")
    long countByStudentCollegeIdAndStatus(@Param("collegeId") Long collegeId,
            @Param("status") Application.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Application a where a.id = :id")
    java.util.Optional<Application> findByIdForUpdate(@Param("id") Long id);
}
