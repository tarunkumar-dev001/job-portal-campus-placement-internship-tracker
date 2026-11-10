package com.example.demo.dto.placement;

import java.time.LocalDate;
import jakarta.validation.constraints.*;

public record DriveRequest(
        @NotBlank String title, String companyName, String jobTitle, String driveType,
        String description, @DecimalMin("0.0") @DecimalMax("10.0") Double minCgpa,
        @PositiveOrZero Integer maxBacklogs, @NotNull LocalDate registrationDeadline,
        LocalDate driveDate) {
}
