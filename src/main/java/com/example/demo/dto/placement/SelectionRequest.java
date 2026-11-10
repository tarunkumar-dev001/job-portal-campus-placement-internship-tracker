package com.example.demo.dto.placement;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SelectionRequest(@NotNull Long studentId, String jobTitle,
                               @PositiveOrZero Double packageAmount) {
}
