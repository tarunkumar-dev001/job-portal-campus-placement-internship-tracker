package com.example.demo.dto;

import com.example.demo.entity.User.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotNull Role role,
        String fullName,
        String rollNumber,
        String department,
        Double cgpa,
        Integer backlogs,
        String resumeUrl) {
}
