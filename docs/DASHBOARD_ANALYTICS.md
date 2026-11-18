# Dashboard analytics API

These endpoints return database-backed summaries. All require a valid authenticated session and the role named in the endpoint. No schema migration is required.

## Endpoints

- `GET /api/admin/stats` — `ADMIN`; extends the existing platform summary.
- `GET /api/placement/stats` — `PLACEMENT_OFFICER`; extends the existing assigned-college summary.
- `GET /api/recruiter/stats` — `RECRUITER`; summary is restricted to jobs owned by the authenticated recruiter.
- `GET /api/student/stats` — `STUDENT`; summary is restricted to the authenticated student.

The existing `GET /api/admin/audit-logs` remains available and returns the full audit log. The admin stats response separately includes the 10 most recent audit entries.

## Example response shapes

```json
{
  "users": 24,
  "students": 18,
  "recruiters": 3,
  "placementOfficers": 2,
  "colleges": 1,
  "companies": 3,
  "drives": 2,
  "selections": 4,
  "activeJobs": 5,
  "activeInternships": 2,
  "totalApplications": 12,
  "applicationsByStatus": {
    "APPLIED": 5,
    "SHORTLISTED": 3,
    "INTERVIEW_SCHEDULED": 2,
    "REJECTED": 1,
    "OFFERED": 1
  },
  "uniqueStudentsPlaced": 4,
  "placementPercentage": 22.22222222222222,
  "recentActivity": []
}
```

```json
{
  "activeDrives": 1,
  "registeredStudents": 3,
  "selectedStudents": 2,
  "selectionsByDepartment": {"Computer Science": 2},
  "totalStudents": 10,
  "studentsByDepartment": {"Computer Science": 7, "Electronics": 3},
  "totalDrives": 2,
  "totalApplications": 6,
  "totalDriveRegistrations": 3,
  "uniqueStudentsShortlisted": 2,
  "uniqueStudentsPlaced": 2,
  "unplacedStudents": 8,
  "placementPercentage": 20.0,
  "rejectedApplications": 1,
  "rejectionReasons": {"Eligibility requirements not met": 1},
  "applicationsByStatus": {
    "APPLIED": 2,
    "SHORTLISTED": 2,
    "INTERVIEW_SCHEDULED": 1,
    "REJECTED": 1,
    "OFFERED": 0
  }
}
```

```json
{
  "totalJobs": 3,
  "totalInternships": 1,
  "activeJobPostings": 2,
  "totalApplicants": 4,
  "applicationsByStatus": {
    "APPLIED": 2,
    "SHORTLISTED": 1,
    "INTERVIEW_SCHEDULED": 1,
    "REJECTED": 0,
    "OFFERED": 0
  },
  "shortlistedCandidates": 1,
  "rejectedCandidates": 0,
  "scheduledInterviews": 1,
  "offersIssued": 1,
  "acceptedOffers": 0,
  "declinedOffers": 0
}
```

```json
{
  "totalApplications": 2,
  "applicationsByStatus": {
    "APPLIED": 1,
    "SHORTLISTED": 0,
    "INTERVIEW_SCHEDULED": 1,
    "REJECTED": 0,
    "OFFERED": 0
  },
  "upcomingInterviews": 1,
  "offersReceived": 0,
  "acceptedOffers": 0,
  "declinedOffers": 0,
  "savedJobs": 1,
  "recentApplicationActivity": [
    {
      "jobTitle": "Software Engineer",
      "companyName": "Example Company",
      "status": "APPLIED",
      "changedAt": "2026-11-13T07:00:00Z",
      "note": "Application submitted"
    }
  ]
}
```

Values above are illustrative response shapes only; production API responses are computed from persisted records.

## Metric definitions

- Admin `activeJobs` counts published, open, non-expired regular job postings; internship records are excluded, and a missing internship flag is treated as a regular job. Admin `activeInternships` counts published, open, non-expired postings with `internship=true`.
- Recruiter `activeJobPostings` counts all of that recruiter's published, open, non-expired postings, including both regular jobs and internships.
- Application-by-status values count application records by their current persisted enum status. Every enum key is returned with zero when absent. Withdrawals set `withdrawnAt` but do not change the existing `APPLIED` enum state.
- Admin placement percentage is distinct students with at least one `PlacementSelection`, divided by all student-role accounts on the platform.
- Placement-officer student counts use profiles assigned to the officer's college. Application counts/statuses include applications by those profiles; drive counts/registrations/selections are scoped to that college's drives. A student is considered placed only if selected for that college and their profile is assigned to it. The percentage is distinct placed students divided by assigned-college student profiles; unplaced is the nonnegative difference.
- Placement `registeredStudents` preserves its former meaning: registration rows summed across the officer's drives. `totalDriveRegistrations` is the same explicit event count; `totalStudents` is the assigned-college profile count.
- A placement drive is active when its persisted status is `OPEN`, matching the existing placement summary behavior.
- Placement shortlisted candidates are distinct assigned-college students with a `SHORTLISTED` status-history event. Rejected applications count current `REJECTED` applications. Rejection reasons group only nonblank notes recorded on rejection history events; no reason is inferred.
- Recruiter applicants, shortlisted candidates, and rejected candidates are distinct students across that recruiter's jobs, so multiple applications by one student do not inflate candidate counts. Application status values remain application-record counts. `offersIssued` counts all offer records ever issued (including subsequently accepted, declined, or withdrawn); accepted and declined counts are subsets.
- Student application/status counts, interviews, offers, and saved jobs are limited to the authenticated student's records. Upcoming interviews have a future schedule and `SCHEDULED` or `RESCHEDULED` status. Recent activity returns up to 10 latest application status-history records.
- Empty results return zero counts, empty maps/lists, and `0.0` percentages when the denominator is zero.

Status-history notes are user-provided text; clients should display rejection reasons only within the assigned-college authorization boundary and avoid treating free text as a validated category.
