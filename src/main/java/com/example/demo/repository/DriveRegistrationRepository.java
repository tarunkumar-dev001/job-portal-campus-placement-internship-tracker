package com.example.demo.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.entity.DriveRegistration;

public interface DriveRegistrationRepository extends JpaRepository<DriveRegistration, Long> {
    List<DriveRegistration> findByDriveId(Long driveId);
    Optional<DriveRegistration> findByDriveIdAndStudentId(Long driveId, Long studentId);
    long countByDriveId(Long driveId);
}
