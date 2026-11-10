package com.example.demo.dto.placement;

import java.time.Instant;

public record DriveRegistrationResponse(Long id, Long studentId, String studentEmail, String studentName,
                                        String department, Double cgpa, Integer backlogs, String status,
                                        Instant registeredAt, Double offeredPackage) {
}
