package com.example.demo.dto.placement;

import java.util.Map;

public record PlacementStatsResponse(long activeDrives, long registeredStudents, long selectedStudents,
                                     Map<String, Long> selectionsByDepartment, long totalStudents,
                                     Map<String, Long> studentsByDepartment, long totalDrives,
                                     long totalApplications, long totalDriveRegistrations,
                                     long uniqueStudentsShortlisted, long uniqueStudentsPlaced,
                                     long unplacedStudents, double placementPercentage,
                                     long rejectedApplications, Map<String, Long> rejectionReasons,
                                     Map<String, Long> applicationsByStatus) {
}
