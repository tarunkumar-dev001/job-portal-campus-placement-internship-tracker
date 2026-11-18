package com.example.demo.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.demo.entity.DriveRegistration;

public interface DriveRegistrationRepository extends JpaRepository<DriveRegistration, Long> {
    List<DriveRegistration> findByDriveId(Long driveId);
    Optional<DriveRegistration> findByDriveIdAndStudentId(Long driveId, Long studentId);
    long countByDriveId(Long driveId);

    @Query("select count(registration.id) from DriveRegistration registration "
            + "where registration.drive.college.id = :collegeId")
    long countByCollegeId(@Param("collegeId") Long collegeId);
}
