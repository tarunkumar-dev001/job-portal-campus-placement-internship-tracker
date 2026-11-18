package com.example.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.example.demo.entity.Application;
import com.example.demo.entity.ApplicationStatusHistory;
import com.example.demo.entity.College;
import com.example.demo.entity.Company;
import com.example.demo.entity.Department;
import com.example.demo.entity.DriveRegistration;
import com.example.demo.entity.InterviewSchedule;
import com.example.demo.entity.Job;
import com.example.demo.entity.Notification;
import com.example.demo.entity.Offer;
import com.example.demo.entity.PlacementDrive;
import com.example.demo.entity.PlacementSelection;
import com.example.demo.entity.StudentProfile;
import com.example.demo.entity.User;
import com.example.demo.repository.ApplicationRepository;
import com.example.demo.repository.ApplicationStatusHistoryRepository;
import com.example.demo.repository.CollegeRepository;
import com.example.demo.repository.CompanyRepository;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.DriveRegistrationRepository;
import com.example.demo.repository.InterviewScheduleRepository;
import com.example.demo.repository.JobRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.OfferRepository;
import com.example.demo.repository.PlacementDriveRepository;
import com.example.demo.repository.PlacementOfficerProfileRepository;
import com.example.demo.repository.PlacementSelectionRepository;
import com.example.demo.repository.StudentProfileRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.DemoDataSeedService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "app.demo.seed-enabled=true",
        "app.cors.allowed-origins=http://localhost:5173",
        "app.storage.resumes=${java.io.tmpdir}/careerconnect-demo-test-resumes"
})
@ActiveProfiles("demo")
class DemoDataSeedServiceTests {

    private static final String TEST_PASSWORD = UUID.randomUUID().toString();

    @Autowired private DemoDataSeedService seedService;
    @Autowired private UserRepository users;
    @Autowired private StudentProfileRepository studentProfiles;
    @Autowired private PlacementOfficerProfileRepository officerProfiles;
    @Autowired private CollegeRepository colleges;
    @Autowired private DepartmentRepository departments;
    @Autowired private CompanyRepository companies;
    @Autowired private JobRepository jobs;
    @Autowired private ApplicationRepository applications;
    @Autowired private ApplicationStatusHistoryRepository histories;
    @Autowired private InterviewScheduleRepository interviews;
    @Autowired private OfferRepository offers;
    @Autowired private PlacementDriveRepository drives;
    @Autowired private DriveRegistrationRepository registrations;
    @Autowired private PlacementSelectionRepository selections;
    @Autowired private NotificationRepository notifications;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private PasswordEncoder passwordEncoderSpy;

    @DynamicPropertySource
    static void demoPassword(DynamicPropertyRegistry registry) {
        registry.add("app.demo.password", () -> TEST_PASSWORD);
    }

    @BeforeEach
    void resetIsolatedDatabase() {
        Mockito.reset(passwordEncoderSpy);
        clearIsolatedDatabase();
    }

    @AfterEach
    void removeSeededFixture() {
        clearIsolatedDatabase();
    }

    @Test
    void createsExpectedDatasetWithValidOwnershipAndRelationships() {
        seedService.seed();

        assertEquals(1, users.countByRole(User.Role.ADMIN));
        assertEquals(2, users.countByRole(User.Role.PLACEMENT_OFFICER));
        assertEquals(5, users.countByRole(User.Role.RECRUITER));
        assertEquals(30, users.countByRole(User.Role.STUDENT));
        assertEquals(30, studentProfiles.count());
        assertEquals(2, officerProfiles.count());
        assertEquals(2, colleges.count());
        assertEquals(8, departments.count());
        assertEquals(5, companies.count());
        assertEquals(8, jobs.countNonInternshipJobs());
        assertEquals(6, jobs.countByInternshipTrue());
        assertEquals(60, applications.count());
        assertEquals(38, users.count());
        assertEquals(60, applications.findAll().stream()
                .map(application -> application.getStudent().getId() + ":" + application.getJob().getId())
                .collect(java.util.stream.Collectors.toSet()).size());
        assertEquals(15, applications.findAll().stream()
                .filter(application -> application.getStatus() == Application.Status.REJECTED).count());
        assertEquals(5, applications.findAll().stream()
                .filter(application -> application.getWithdrawnAt() != null).count());
        assertTrue(applications.findAll().stream()
                .anyMatch(application -> application.getStatus() == Application.Status.APPLIED));
        assertTrue(applications.findAll().stream()
                .anyMatch(application -> application.getStatus() == Application.Status.SHORTLISTED));
        assertEquals(15, interviews.count());
        assertEquals(8, offers.count());
        assertEquals(4, offers.findAll().stream().map(Offer::getStatus).distinct().count());
        assertEquals(8, offers.findAll().stream()
                .map(offer -> offer.getApplication().getId()).collect(java.util.stream.Collectors.toSet()).size());
        assertEquals(2, drives.count());
        assertEquals(20, registrations.count());
        assertEquals(4, selections.count());
        assertEquals(20, registrations.findAll().stream()
                .map(registration -> registration.getDrive().getId() + ":" + registration.getStudent().getId())
                .collect(java.util.stream.Collectors.toSet()).size());
        assertEquals(4, selections.findAll().stream()
                .map(selection -> selection.getDrive().getId() + ":" + selection.getStudent().getId())
                .collect(java.util.stream.Collectors.toSet()).size());
        assertEquals(128, histories.count());
        assertEquals(87, notifications.count());

        List<User> recruiters = users.findAll().stream()
                .filter(user -> user.getRole() == User.Role.RECRUITER).toList();
        assertEquals(5, recruiters.stream().filter(recruiter ->
                companies.findByOwnerUserId(recruiter.getId()).isPresent()).count());
        assertTrue(jobs.findAll().stream().allMatch(job ->
                recruiters.stream().anyMatch(recruiter -> recruiter.getId().equals(job.getRecruiterId()))
                        && companies.findByOwnerUserId(job.getRecruiterId())
                                .map(Company::getName).filter(job.getCompanyName()::equals).isPresent()));
        assertTrue(applications.findAll().stream().allMatch(application ->
                users.findById(application.getStudent().getId())
                        .map(user -> user.getRole() == User.Role.STUDENT).orElse(false)
                        && jobs.existsById(application.getJob().getId())));
        assertTrue(interviews.findAll().stream().allMatch(interview ->
                applications.existsById(interview.getApplication().getId())));
        assertTrue(offers.findAll().stream().allMatch(offer ->
                applications.existsById(offer.getApplication().getId())));
        assertTrue(selections.findAll().stream().allMatch(selection ->
                registrations.findByDriveIdAndStudentId(selection.getDrive().getId(),
                        selection.getStudent().getId()).map(registration ->
                                registration.getStatus() == DriveRegistration.RegistrationStatus.SELECTED)
                        .orElse(false)));
        assertTrue(drives.findAll().stream().allMatch(drive ->
                drive.getCollege() != null && users.findById(drive.getCreatedByOfficerId())
                        .map(user -> user.getRole() == User.Role.PLACEMENT_OFFICER).orElse(false)));
    }

    @Test
    void storesBcryptHashesAndVerifiesOnlySyntheticDemoAccounts() {
        seedService.seed();

        List<User> demoUsers = users.findAll();
        assertEquals(38, demoUsers.size());
        assertTrue(demoUsers.stream().allMatch(User::isEmailVerified));
        assertTrue(demoUsers.stream().allMatch(User::isEnabled));
        assertTrue(demoUsers.stream().allMatch(user ->
                user.getEmail().endsWith("@careerconnect-demo.test")));
        assertTrue(demoUsers.stream().allMatch(user ->
                user.getPassword().startsWith("$2a$") || user.getPassword().startsWith("$2b$")
                        || user.getPassword().startsWith("$2y$")));
        assertTrue(demoUsers.stream().allMatch(user ->
                passwordEncoder.matches(TEST_PASSWORD, user.getPassword())));
        assertFalse(demoUsers.stream().anyMatch(user -> user.getPassword().equals(TEST_PASSWORD)));
    }

    @Test
    void repeatedSeedIsIdempotentAcrossAllSeededTables() {
        seedService.seed();
        List<List<Map<String, Object>>> snapshot = datasetSnapshot();

        seedService.seed();
        seedService.seed();

        assertEquals(snapshot, datasetSnapshot());
    }

    @Test
    void rejectsIncompleteReservedDatasetEvenWhenExtraSameOwnerHistoryMasksTheCount() {
        seedService.seed();
        Application application = applications.findAll().get(0);
        ApplicationStatusHistory removed = histories.findAll().stream()
                .filter(history -> history.getApplication().getId().equals(application.getId()))
                .findFirst().orElseThrow();
        histories.deleteById(removed.getId());
        histories.save(ApplicationStatusHistory.builder()
                .application(application)
                .status(Application.Status.APPLIED)
                .changedBy(users.findById(application.getStudent().getId()).orElseThrow().getEmail())
                .changedAt(application.getAppliedAt())
                .note("Unexpected extra history")
                .build());
        List<List<Map<String, Object>>> beforeRejectedSeed = datasetSnapshot();

        assertThrows(IllegalStateException.class, seedService::seed);

        assertEquals(beforeRejectedSeed, datasetSnapshot());
        assertEquals(128, histories.count());
    }

    @Test
    void rejectsAdminOnlyPartialDatasetWithoutCreatingMoreRecords() {
        users.save(User.builder()
                .email("admin@careerconnect-demo.test")
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(User.Role.ADMIN)
                .enabled(true)
                .emailVerified(true)
                .build());

        assertThrows(IllegalStateException.class, seedService::seed);

        assertEquals(1, users.count());
        assertTrue(datasetCounts().subList(1, datasetCounts().size()).stream().allMatch(count -> count == 0L));
    }

    @Test
    void rejectsNullBlankShortAndOverLimitPasswordsBeforeCreatingRecords() {
        List<String> invalidPasswords = new ArrayList<>();
        invalidPasswords.add(null);
        invalidPasswords.add("");
        invalidPasswords.add(" \t ");
        invalidPasswords.add("x".repeat(11));
        invalidPasswords.add("x".repeat(73));

        for (String password : invalidPasswords) {
            DemoDataSeedService service = serviceWithPassword(password);
            assertThrows(IllegalStateException.class, service::seed);
            assertTrue(datasetCounts().stream().allMatch(count -> count == 0L));
        }
    }

    @Test
    void transactionRollsBackAllRecordsWhenSeedingFailsMidway() {
        AtomicInteger encodes = new AtomicInteger();
        doAnswer(invocation -> {
            if (encodes.incrementAndGet() == 5) {
                throw new IllegalStateException("Simulated seed failure");
            }
            return invocation.callRealMethod();
        }).when(passwordEncoderSpy).encode(anyString());

        assertThrows(IllegalStateException.class, seedService::seed);

        Mockito.reset(passwordEncoderSpy);
        assertTrue(datasetCounts().stream().allMatch(count -> count == 0L));
    }

    private List<Long> datasetCounts() {
        List<Long> counts = new ArrayList<>();
        counts.add(users.count());
        counts.add(studentProfiles.count());
        counts.add(officerProfiles.count());
        counts.add(colleges.count());
        counts.add(departments.count());
        counts.add(companies.count());
        counts.add(jobs.count());
        counts.add(applications.count());
        counts.add(histories.count());
        counts.add(interviews.count());
        counts.add(offers.count());
        counts.add(drives.count());
        counts.add(registrations.count());
        counts.add(selections.count());
        counts.add(notifications.count());
        return counts;
    }

    private List<List<Map<String, Object>>> datasetSnapshot() {
        List<List<Map<String, Object>>> snapshot = new ArrayList<>();
        for (String table : List.of(
                "users", "student_profiles", "placement_officer_profiles", "colleges", "departments",
                "companies", "jobs", "applications", "application_status_history", "interview_schedules",
                "offers", "placement_drives", "drive_registrations", "placement_selections", "notifications")) {
            snapshot.add(jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        }
        return snapshot;
    }

    private DemoDataSeedService serviceWithPassword(String password) {
        return new DemoDataSeedService(users, studentProfiles, officerProfiles, colleges, departments, companies,
                jobs, applications, histories, interviews, offers, drives, registrations, selections, notifications,
                passwordEncoder, password, true);
    }

    private void clearIsolatedDatabase() {
        for (String table : List.of(
                "notifications", "audit_logs", "security_tokens", "refresh_tokens", "offers",
                "interview_schedules", "application_status_history", "applications",
                "placement_selections", "drive_registrations", "placement_drives", "saved_jobs",
                "student_profiles", "placement_officer_profiles", "jobs", "companies",
                "departments", "colleges", "users")) {
            jdbc.update("DELETE FROM " + table);
        }
    }
}
