package com.example.demo;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.entity.*;
import com.example.demo.repository.*;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardAnalyticsIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired PlacementOfficerProfileRepository officers;
    @Autowired CollegeRepository colleges;
    @Autowired CompanyRepository companies;
    @Autowired JobRepository jobs;
    @Autowired ApplicationRepository applications;
    @Autowired ApplicationStatusHistoryRepository history;
    @Autowired InterviewScheduleRepository interviews;
    @Autowired OfferRepository offers;
    @Autowired SavedJobRepository savedJobs;
    @Autowired PlacementDriveRepository drives;
    @Autowired DriveRegistrationRepository registrations;
    @Autowired PlacementSelectionRepository selections;
    @Autowired AuditLogRepository auditLogs;

    @Test
    @WithMockUser(username = "admin@example.test", roles = "ADMIN")
    void adminStatsHandleEmptyDatasetAndZeroDenominator() throws Exception {
        mvc.perform(get("/api/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students").value(0))
                .andExpect(jsonPath("$.totalApplications").value(0))
                .andExpect(jsonPath("$.applicationsByStatus.APPLIED").value(0))
                .andExpect(jsonPath("$.uniqueStudentsPlaced").value(0))
                .andExpect(jsonPath("$.placementPercentage").value(0.0))
                .andExpect(jsonPath("$.recentActivity", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "empty-officer@example.test", roles = "PLACEMENT_OFFICER")
    void placementStatsHandleEmptyCollegeAndZeroDenominator() throws Exception {
        College college = college("EMPTY-COL", "Empty College");
        User officer = user("empty-officer@example.test", User.Role.PLACEMENT_OFFICER);
        officers.save(PlacementOfficerProfile.builder().user(officer).college(college).build());

        mvc.perform(get("/api/placement/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStudents").value(0))
                .andExpect(jsonPath("$.totalApplications").value(0))
                .andExpect(jsonPath("$.totalDriveRegistrations").value(0))
                .andExpect(jsonPath("$.placementPercentage").value(0.0))
                .andExpect(jsonPath("$.studentsByDepartment", anEmptyMap()))
                .andExpect(jsonPath("$.rejectionReasons", anEmptyMap()));
    }

    @Test
    @WithMockUser(username = "empty-recruiter@example.test", roles = "RECRUITER")
    void recruiterStatsHandleEmptyDataset() throws Exception {
        user("empty-recruiter@example.test", User.Role.RECRUITER);

        mvc.perform(get("/api/recruiter/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobs").value(0))
                .andExpect(jsonPath("$.totalApplicants").value(0))
                .andExpect(jsonPath("$.applicationsByStatus.APPLIED").value(0))
                .andExpect(jsonPath("$.offersIssued").value(0));
    }

    @Test
    @WithMockUser(username = "empty-student@example.test", roles = "STUDENT")
    void studentStatsHandleEmptyDataset() throws Exception {
        user("empty-student@example.test", User.Role.STUDENT);

        mvc.perform(get("/api/student/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(0))
                .andExpect(jsonPath("$.upcomingInterviews").value(0))
                .andExpect(jsonPath("$.offersReceived").value(0))
                .andExpect(jsonPath("$.savedJobs").value(0))
                .andExpect(jsonPath("$.recentApplicationActivity", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "admin@example.test", roles = "ADMIN")
    void adminStatsCountDatabaseRecordsAndUniquePlacedStudents() throws Exception {
        User student = user("student-a@example.test", User.Role.STUDENT);
        User otherStudent = user("student-b@example.test", User.Role.STUDENT);
        User recruiter = user("recruiter-a@example.test", User.Role.RECRUITER);
        user("officer-a@example.test", User.Role.PLACEMENT_OFFICER);
        College college = college("ADMIN-COL", "Admin College");
        profiles.save(profile(student, college, "CSE", "A-1"));
        profiles.save(profile(otherStudent, college, "ECE", "A-2"));
        Company company = companies.save(Company.builder().name("Admin Demo Company")
                .ownerUserId(recruiter.getId()).verified(true).build());
        Job job = job(recruiter, company, "Engineer", false, true, false);
        Job internship = job(recruiter, company, "Intern", true, true, false);
        Application applied = application(student, job, Application.Status.APPLIED);
        Application rejected = application(otherStudent, job, Application.Status.REJECTED);
        application(student, internship, Application.Status.SHORTLISTED);
        PlacementDrive drive = drive(college, "Admin Drive", PlacementDrive.DriveStatus.OPEN);
        selections.save(selection(drive, student));
        auditLogs.save(AuditLog.builder().actorEmail("admin@example.test").action("TEST_ACTIVITY")
                .targetType("Test").targetId("1").createdAt(Instant.now()).build());

        mvc.perform(get("/api/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students").value(2))
                .andExpect(jsonPath("$.recruiters").value(1))
                .andExpect(jsonPath("$.placementOfficers").value(1))
                .andExpect(jsonPath("$.companies").value(1))
                .andExpect(jsonPath("$.activeJobs").value(1))
                .andExpect(jsonPath("$.activeInternships").value(1))
                .andExpect(jsonPath("$.totalApplications").value(3))
                .andExpect(jsonPath("$.applicationsByStatus.APPLIED").value(1))
                .andExpect(jsonPath("$.applicationsByStatus.SHORTLISTED").value(1))
                .andExpect(jsonPath("$.applicationsByStatus.REJECTED").value(1))
                .andExpect(jsonPath("$.uniqueStudentsPlaced").value(1))
                .andExpect(jsonPath("$.placementPercentage").value(50.0))
                .andExpect(jsonPath("$.recentActivity[0].action").value("TEST_ACTIVITY"));
    }

    @Test
    @WithMockUser(username = "officer@example.test", roles = "PLACEMENT_OFFICER")
    void placementStatsScopeToAssignedCollegeAndDeduplicatePlacements() throws Exception {
        College college = college("PLACEMENT-COL", "Placement College");
        College otherCollege = college("OTHER-COL", "Other College");
        User officer = user("officer@example.test", User.Role.PLACEMENT_OFFICER);
        officers.save(PlacementOfficerProfile.builder().user(officer).college(college).fullName("Officer").build());
        User first = user("placement-a@example.test", User.Role.STUDENT);
        User second = user("placement-b@example.test", User.Role.STUDENT);
        User outside = user("placement-outside@example.test", User.Role.STUDENT);
        profiles.save(profile(first, college, "CSE", "P-1"));
        profiles.save(profile(second, college, "ECE", "P-2"));
        profiles.save(profile(outside, otherCollege, "ME", "P-3"));

        PlacementDrive openDrive = drive(college, "Open Drive", PlacementDrive.DriveStatus.OPEN);
        PlacementDrive completedDrive = drive(college, "Completed Drive", PlacementDrive.DriveStatus.COMPLETED);
        PlacementDrive outsideDrive = drive(otherCollege, "Outside Drive", PlacementDrive.DriveStatus.OPEN);
        registration(openDrive, first);
        registration(openDrive, second);
        selections.save(selection(openDrive, first));
        selections.save(selection(completedDrive, first));
        selections.save(selection(outsideDrive, outside));

        User recruiter = user("placement-recruiter@example.test", User.Role.RECRUITER);
        Company company = companies.save(Company.builder().name("Placement Company")
                .ownerUserId(recruiter.getId()).verified(true).build());
        Job job = job(recruiter, company, "Placement Job", false, true, false);
        Application shortlisted = application(first, job, Application.Status.SHORTLISTED);
        history.save(statusHistory(shortlisted, Application.Status.SHORTLISTED, null));
        Application rejected = application(second, job, Application.Status.REJECTED);
        history.save(statusHistory(rejected, Application.Status.REJECTED, "Eligibility requirements not met"));
        Application outsideApplication = application(outside, job, Application.Status.REJECTED);
        history.save(statusHistory(outsideApplication, Application.Status.REJECTED, "Outside college"));

        mvc.perform(get("/api/placement/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStudents").value(2))
                .andExpect(jsonPath("$.studentsByDepartment.CSE").value(1))
                .andExpect(jsonPath("$.studentsByDepartment.ECE").value(1))
                .andExpect(jsonPath("$.totalDrives").value(2))
                .andExpect(jsonPath("$.activeDrives").value(1))
                .andExpect(jsonPath("$.totalApplications").value(2))
                .andExpect(jsonPath("$.totalDriveRegistrations").value(2))
                .andExpect(jsonPath("$.uniqueStudentsShortlisted").value(1))
                .andExpect(jsonPath("$.uniqueStudentsPlaced").value(1))
                .andExpect(jsonPath("$.unplacedStudents").value(1))
                .andExpect(jsonPath("$.placementPercentage").value(50.0))
                .andExpect(jsonPath("$.rejectedApplications").value(1))
                .andExpect(jsonPath("$.rejectionReasons['Eligibility requirements not met']").value(1))
                .andExpect(jsonPath("$.applicationsByStatus.REJECTED").value(1));
    }

    @Test
    @WithMockUser(username = "recruiter-a@example.test", roles = "RECRUITER")
    void recruiterStatsAreOwnedAndCountUniqueCandidates() throws Exception {
        User recruiter = user("recruiter-a@example.test", User.Role.RECRUITER);
        User otherRecruiter = user("recruiter-b@example.test", User.Role.RECRUITER);
        User student = user("recruiter-student@example.test", User.Role.STUDENT);
        User secondStudent = user("recruiter-student-b@example.test", User.Role.STUDENT);
        Company company = companies.save(Company.builder().name("Recruiter A Co")
                .ownerUserId(recruiter.getId()).verified(true).build());
        Company otherCompany = companies.save(Company.builder().name("Recruiter B Co")
                .ownerUserId(otherRecruiter.getId()).verified(true).build());
        Job firstJob = job(recruiter, company, "Recruiter Job", false, true, false);
        Job secondJob = job(recruiter, company, "Recruiter Internship", true, true, false);
        Job outsideJob = job(otherRecruiter, otherCompany, "Other Recruiter Job", false, true, false);
        job(recruiter, company, "Unpublished Job", false, false, false);
        job(recruiter, company, "Closed Internship", true, true, true);
        Job expiredJob = job(recruiter, company, "Expired Job", false, true, false);
        expiredJob.setDeadline(LocalDate.now().minusDays(1));
        jobs.save(expiredJob);
        Application applied = application(student, firstJob, Application.Status.APPLIED);
        Application shortlisted = application(student, secondJob, Application.Status.SHORTLISTED);
        Application rejected = application(secondStudent, firstJob, Application.Status.REJECTED);
        application(secondStudent, outsideJob, Application.Status.APPLIED);
        interviews.save(InterviewSchedule.builder().application(shortlisted)
                .scheduledAt(Instant.now().plusSeconds(3600)).mode("ONLINE").createdAt(Instant.now()).build());
        offer(applied, Offer.Status.ISSUED);
        offer(shortlisted, Offer.Status.ACCEPTED);
        offer(rejected, Offer.Status.DECLINED);

        mvc.perform(get("/api/recruiter/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobs").value(3))
                .andExpect(jsonPath("$.totalInternships").value(2))
                .andExpect(jsonPath("$.activeJobPostings").value(2))
                .andExpect(jsonPath("$.totalApplicants").value(2))
                .andExpect(jsonPath("$.applicationsByStatus.APPLIED").value(1))
                .andExpect(jsonPath("$.applicationsByStatus.SHORTLISTED").value(1))
                .andExpect(jsonPath("$.applicationsByStatus.REJECTED").value(1))
                .andExpect(jsonPath("$.shortlistedCandidates").value(1))
                .andExpect(jsonPath("$.rejectedCandidates").value(1))
                .andExpect(jsonPath("$.scheduledInterviews").value(1))
                .andExpect(jsonPath("$.offersIssued").value(3))
                .andExpect(jsonPath("$.acceptedOffers").value(1))
                .andExpect(jsonPath("$.declinedOffers").value(1));
    }

    @Test
    @WithMockUser(username = "student-a@example.test", roles = "STUDENT")
    void studentStatsOnlyIncludeOwnApplicationsAndRelatedRecords() throws Exception {
        User student = user("student-a@example.test", User.Role.STUDENT);
        User otherStudent = user("student-b@example.test", User.Role.STUDENT);
        User recruiter = user("student-recruiter@example.test", User.Role.RECRUITER);
        Company company = companies.save(Company.builder().name("Student Demo Company")
                .ownerUserId(recruiter.getId()).verified(true).build());
        Job firstJob = job(recruiter, company, "Student Job", false, true, false);
        Job secondJob = job(recruiter, company, "Student Internship", true, true, false);
        Application applied = application(student, firstJob, Application.Status.APPLIED);
        Application offered = application(student, secondJob, Application.Status.OFFERED);
        application(otherStudent, firstJob, Application.Status.REJECTED);
        history.save(statusHistory(applied, Application.Status.APPLIED, "Applied"));
        interviews.save(InterviewSchedule.builder().application(offered)
                .scheduledAt(Instant.now().plusSeconds(7200)).mode("ONLINE").createdAt(Instant.now()).build());
        offer(applied, Offer.Status.ACCEPTED);
        offer(offered, Offer.Status.DECLINED);
        savedJobs.save(SavedJob.builder().student(student).job(firstJob).savedAt(Instant.now()).build());

        mvc.perform(get("/api/student/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(2))
                .andExpect(jsonPath("$.applicationsByStatus.APPLIED").value(1))
                .andExpect(jsonPath("$.applicationsByStatus.OFFERED").value(1))
                .andExpect(jsonPath("$.applicationsByStatus.REJECTED").value(0))
                .andExpect(jsonPath("$.upcomingInterviews").value(1))
                .andExpect(jsonPath("$.offersReceived").value(2))
                .andExpect(jsonPath("$.acceptedOffers").value(1))
                .andExpect(jsonPath("$.declinedOffers").value(1))
                .andExpect(jsonPath("$.savedJobs").value(1))
                .andExpect(jsonPath("$.recentApplicationActivity", hasSize(1)))
                .andExpect(jsonPath("$.recentApplicationActivity[0].jobTitle").value("Student Job"));
    }

    @Test
    @WithMockUser(username = "student@example.test", roles = "STUDENT")
    void roleSpecificSummaryEndpointsRejectOtherRoles() throws Exception {
        mvc.perform(get("/api/admin/stats")).andExpect(status().isForbidden());
        mvc.perform(get("/api/placement/stats")).andExpect(status().isForbidden());
        mvc.perform(get("/api/recruiter/stats")).andExpect(status().isForbidden());
    }

    private User user(String email, User.Role role) {
        return users.save(User.builder().email(email).password("unused-test-hash").role(role)
                .enabled(true).emailVerified(true).build());
    }

    private College college(String code, String name) {
        return colleges.save(College.builder().code(code).name(name).active(true).build());
    }

    private StudentProfile profile(User user, College college, String department, String rollNumber) {
        return StudentProfile.builder().user(user).college(college).fullName(rollNumber)
                .rollNumber(rollNumber).department(department).cgpa(8.0).backlogs(0).build();
    }

    private Job job(User recruiter, Company company, String title, boolean internship, boolean published, boolean closed) {
        return jobs.save(Job.builder().title(title).description("Test role").companyName(company.getName())
                .recruiterId(recruiter.getId()).minCgpa(0.0).maxBacklogs(0)
                .deadline(LocalDate.now().plusDays(30)).internship(internship).published(published)
                .closed(closed).build());
    }

    private Application application(User student, Job job, Application.Status status) {
        return applications.save(Application.builder().student(student).job(job).status(status)
                .appliedAt(Instant.now()).build());
    }

    private PlacementDrive drive(College college, String title, PlacementDrive.DriveStatus status) {
        return drives.save(PlacementDrive.builder().college(college).title(title)
                .registrationDeadline(LocalDate.now().plusDays(10)).status(status).createdByOfficerId(1L).build());
    }

    private DriveRegistration registration(PlacementDrive drive, User student) {
        return registrations.save(DriveRegistration.builder().drive(drive).student(student)
                .status(DriveRegistration.RegistrationStatus.REGISTERED).registeredAt(Instant.now()).build());
    }

    private PlacementSelection selection(PlacementDrive drive, User student) {
        return PlacementSelection.builder().drive(drive).student(student).selectedAt(Instant.now()).build();
    }

    private ApplicationStatusHistory statusHistory(Application application, Application.Status status, String note) {
        return ApplicationStatusHistory.builder().application(application).status(status)
                .changedBy("recruiter@example.test").changedAt(Instant.now()).note(note).build();
    }

    private Offer offer(Application application, Offer.Status status) {
        return offers.save(Offer.builder().application(application).status(status)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }
}
