package com.example.demo.dto.placement;

import java.time.LocalDate;
import com.example.demo.entity.PlacementDrive.DriveStatus;

public record DriveResponse(Long id, Long collegeId, String collegeName, String title,
                            String companyName, String jobTitle, String driveType, String description,
                            Double minCgpa, Integer maxBacklogs, LocalDate registrationDeadline,
                            LocalDate driveDate, DriveStatus status, long registeredCount,
                            long selectedCount) {
}
