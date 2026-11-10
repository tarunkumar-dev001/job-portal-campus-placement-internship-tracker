package com.example.demo.dto.placement;

import java.time.Instant;

public record SelectionResponse(Long id, Long driveId, Long studentId, String studentEmail, String jobTitle,
                                Double packageAmount, Instant selectedAt) {
}
