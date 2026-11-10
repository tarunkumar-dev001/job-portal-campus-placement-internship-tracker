package com.example.demo.dto.placement;

public record CollegeResponse(Long id, String code, String name, String city, String state,
                              String university, boolean active) {
}
