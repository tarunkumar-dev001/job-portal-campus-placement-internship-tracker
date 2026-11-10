package com.example.demo.dto.placement;

import java.util.Map;

public record PlacementStatsResponse(long activeDrives, long registeredStudents, long selectedStudents,
                                     Map<String, Long> selectionsByDepartment) {
}
