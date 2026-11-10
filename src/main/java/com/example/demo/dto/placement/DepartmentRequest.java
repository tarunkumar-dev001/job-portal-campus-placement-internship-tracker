package com.example.demo.dto.placement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record DepartmentRequest(@NotBlank String code, @NotBlank String name,
                                @PositiveOrZero Integer seats) {
}
