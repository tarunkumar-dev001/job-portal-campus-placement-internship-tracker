package com.example.demo.dto.placement;

import jakarta.validation.constraints.NotBlank;

public record CollegeRequest(
        @NotBlank String code,
        @NotBlank String name,
        String city,
        String state,
        String university,
        Boolean active) {
}
