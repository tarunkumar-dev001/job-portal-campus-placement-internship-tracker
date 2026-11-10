package com.example.demo.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.demo.entity.PlacementDrive;

public interface PlacementDriveRepository extends JpaRepository<PlacementDrive, Long> {
    List<PlacementDrive> findByCollegeIdOrderByRegistrationDeadlineDesc(Long collegeId);
    List<PlacementDrive> findByStatusAndRegistrationDeadlineGreaterThanEqualOrderByRegistrationDeadlineAsc(
            PlacementDrive.DriveStatus status, java.time.LocalDate deadline);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select drive from PlacementDrive drive where drive.id = :id")
    Optional<PlacementDrive> findByIdForUpdate(@Param("id") Long id);
}
