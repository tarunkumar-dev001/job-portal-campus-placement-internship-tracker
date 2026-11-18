package com.example.demo.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class DashboardStatsDtos {
    private DashboardStatsDtos() {}

    public record RecruiterDashboardStats(long totalJobs, long totalInternships, long activeJobPostings,
            long totalApplicants, Map<String, Long> applicationsByStatus, long shortlistedCandidates,
            long rejectedCandidates, long scheduledInterviews, long offersIssued, long acceptedOffers,
            long declinedOffers) {}

    public record StudentDashboardStats(long totalApplications, Map<String, Long> applicationsByStatus,
            long upcomingInterviews, long offersReceived, long acceptedOffers, long declinedOffers,
            long savedJobs, List<ApplicationActivity> recentApplicationActivity) {}

    public record ApplicationActivity(String jobTitle, String companyName, String status, Instant changedAt,
            String note) {}
}
