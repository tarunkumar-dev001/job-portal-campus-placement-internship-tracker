package com.example.demo.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.entity.StudentProfile;
import java.util.List;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
    Optional<StudentProfile> findByUserId(Long userId);
    long countByCollegeId(Long collegeId);

    @Query("select coalesce(profile.department, 'Unassigned'), count(profile.id) "
            + "from StudentProfile profile where profile.college.id = :collegeId "
            + "group by profile.department")
    List<Object[]> countStudentsByDepartment(@Param("collegeId") Long collegeId);
}
