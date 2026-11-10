package com.example.demo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.dto.recruiter.RecruiterDtos.OfferRequest;
import com.example.demo.dto.recruiter.RecruiterDtos.InterviewRequest;
import com.example.demo.dto.recruiter.RecruiterDtos.StatusRequest;
import com.example.demo.dto.AuthFlowDtos.ResetPasswordRequest;
import com.example.demo.dto.placement.CollegeRequest;
import com.example.demo.dto.placement.DriveRequest;
import com.example.demo.dto.placement.SelectionRequest;
import com.example.demo.dto.student.StudentDtos.OfferStatusRequest;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.security.JwtUtils;
import com.example.demo.service.AuthService;
import com.example.demo.service.AuthSecurityService;
import com.example.demo.service.AuthTokenSink;
import com.example.demo.service.PlacementService;
import com.example.demo.service.RecruiterService;
import com.example.demo.service.StudentService;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(
        named = "MYSQL_TEST_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/careerconnect_phase8_fresh.*")
class MySqlCompatibilityIntegrationTests {
    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired JobRepository jobs;
    @Autowired ApplicationRepository applications;
    @Autowired OfferRepository offers;
    @Autowired SecurityTokenRepository tokens;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuthService authService;
    @Autowired AuthSecurityService authSecurityService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RecruiterService recruiterService;
    @Autowired StudentService studentService;
    @Autowired PlacementService placementService;
    @Autowired CollegeRepository colleges;
    @Autowired PlacementOfficerProfileRepository officerProfiles;
    @Autowired JwtUtils jwtUtils;
    @MockitoBean AuthTokenSink tokenSink;

    @DynamicPropertySource
    static void configureMysql(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("MYSQL_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("MYSQL_TEST_USERNAME"));
        properties.add("spring.datasource.password", () -> System.getenv("MYSQL_TEST_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> "true");
        properties.add("spring.flyway.validate-on-migrate", () -> "true");
    }

    @Test
    void freshMigrationsValidateAndReportActualMysqlVersion() {
        flyway.validate();
        var applied = flyway.info().applied();
        assertEquals(5, applied.length);
        for (var migration : applied) {
            assertNotNull(migration.getChecksum(), "Missing checksum for migration " + migration.getVersion());
        }
        String version = jdbc.queryForObject("SELECT VERSION()", String.class);
        assertNotNull(version);
        System.out.println("Disposable MySQL server version: " + version);
        assertTrue(version.startsWith("8."), "Expected MySQL 8.x, got " + version);
    }

    @Test
    void migrationUpgradeFromVersionTwoValidatesWithoutRepair() {
        String url = System.getenv("MYSQL_TEST_UPGRADE_URL");
        String username = System.getenv("MYSQL_TEST_USERNAME");
        String password = System.getenv("MYSQL_TEST_PASSWORD");
        assertNotNull(url, "MYSQL_TEST_UPGRADE_URL must refer to a second disposable schema");

        Flyway initial = Flyway.configure().dataSource(url, username, password)
                .locations("classpath:db/migration").target("2").load();
        assertEquals(2, initial.migrate().migrationsExecuted);
        Flyway upgrade = Flyway.configure().dataSource(url, username, password)
                .locations("classpath:db/migration").load();
        assertEquals(3, upgrade.migrate().migrationsExecuted);
        upgrade.validate();
        assertEquals(5, upgrade.info().applied().length);
    }

    @Test
    @Transactional
    void mysqlPersistsAuthAndBusinessRelationsWithMappedColumns() {
        String suffix = UUID.randomUUID().toString();
        User student = users.saveAndFlush(User.builder().email("student-" + suffix + "@example.test")
                .password(passwordEncoder.encode("Old-Test-Password-123"))
                .role(User.Role.STUDENT).enabled(true).emailVerified(false).build());
        profiles.saveAndFlush(StudentProfile.builder().user(student).fullName("Test Student")
                .rollNumber("R-" + suffix).department("Computer Science").cgpa(8.5).backlogs(0).build());
        String verificationToken = "mysql-verification-" + suffix;
        tokens.saveAndFlush(SecurityToken.builder().user(student)
                .tokenHash(AuthSecurityService.hash(verificationToken))
                .type(SecurityToken.Type.EMAIL_VERIFICATION).expiresAt(Instant.now().plusSeconds(300)).build());
        authSecurityService.verifyEmail(new com.example.demo.dto.AuthFlowDtos.TokenRequest(verificationToken));
        var session = authSecurityService.issueSession(student);
        String resetToken = "mysql-reset-" + suffix;
        tokens.saveAndFlush(SecurityToken.builder().user(student)
                .tokenHash(AuthSecurityService.hash(resetToken))
                .type(SecurityToken.Type.PASSWORD_RESET).expiresAt(Instant.now().plusSeconds(300)).build());
        authSecurityService.resetPassword(new ResetPasswordRequest(resetToken, "New-Test-Password-456"));
        assertNotNull(refreshTokens.findByTokenHash(AuthSecurityService.hash(session.refreshToken()))
                .orElseThrow().getRevokedAt());
        assertTrue(passwordEncoder.matches("New-Test-Password-456",
                users.findById(student.getId()).orElseThrow().getPassword()));
        assertNotNull(jwtUtils.generateToken(student.getEmail(), student.getRole().name()));

        User recruiter = users.saveAndFlush(User.builder().email("recruiter-" + suffix + "@example.test")
                .password("{bcrypt}unused").role(User.Role.RECRUITER).enabled(true).emailVerified(true).build());
        Job job = jobs.saveAndFlush(Job.builder().title("MySQL Engineer").description("Integration test")
                .companyName("Example Co").minCgpa(6.0).maxBacklogs(2).deadline(LocalDate.now().plusDays(30))
                .recruiterId(recruiter.getId()).published(true).closed(false).build());
        var application = studentService.apply(student.getEmail(), job.getId());
        recruiterService.updateStatus(recruiter.getEmail(), application.id(), new StatusRequest("SHORTLISTED", "Meets criteria"));
        recruiterService.scheduleInterview(recruiter.getEmail(), application.id(),
                new InterviewRequest(Instant.now().plusSeconds(3600), "ONLINE", null, "MySQL integration"));
        var offer = recruiterService.createOffer(recruiter.getEmail(), application.id(),
                new OfferRequest("Test package", 100000.0, Instant.now().plusSeconds(86400)));
        studentService.respondToOffer(student.getEmail(), offer.id(), new OfferStatusRequest("ACCEPTED"));
        assertEquals(Offer.Status.ACCEPTED, offers.findByApplicationId(application.id()).orElseThrow().getStatus());

        User officer = users.saveAndFlush(User.builder().email("officer-" + suffix + "@example.test")
                .password("{bcrypt}unused").role(User.Role.PLACEMENT_OFFICER).enabled(true).emailVerified(true).build());
        var collegeResponse = placementService.createCollege("admin@example.test",
                new CollegeRequest("C-" + suffix, "MySQL Test College", null, null, null, true));
        College college = colleges.findById(collegeResponse.id()).orElseThrow();
        officerProfiles.saveAndFlush(PlacementOfficerProfile.builder().user(officer).college(college)
                .fullName("Test Officer").build());
        var drive = placementService.createDrive(officer.getEmail(),
                new DriveRequest("MySQL Placement Drive", "Example Co", "Engineer", "FULL_TIME",
                        "Disposable MySQL validation", 7.0, 2, LocalDate.now().plusDays(30),
                        LocalDate.now().plusDays(45)));
        placementService.updateDriveStatus(officer.getEmail(), drive.id(), PlacementDrive.DriveStatus.OPEN);
        studentService.registerForDrive(student.getEmail(), drive.id());
        assertEquals(DriveRegistration.RegistrationStatus.ELIGIBLE.name(),
                placementService.verifyEligibility(officer.getEmail(), drive.id(), student.getId()).status());
        assertEquals(student.getId(), placementService.selectStudent(officer.getEmail(), drive.id(),
                new SelectionRequest(student.getId(), "Engineer", 100000.0)).studentId());
    }

    @Test
    void mysqlEnforcesUniqueEmailAndForeignKeyConstraints() {
        String email = "constraint-" + UUID.randomUUID() + "@example.test";
        users.saveAndFlush(User.builder().email(email).password("{bcrypt}unused")
                .role(User.Role.STUDENT).enabled(true).emailVerified(true).build());
        assertThrows(DataIntegrityViolationException.class,
                () -> users.saveAndFlush(User.builder().email(email).password("{bcrypt}unused")
                        .role(User.Role.STUDENT).enabled(true).emailVerified(true).build()));

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO security_tokens (user_id, token_hash, type, expires_at) VALUES (?, ?, ?, ?)",
                Long.MAX_VALUE, UUID.randomUUID().toString().replace("-", ""),
                SecurityToken.Type.PASSWORD_RESET.name(), java.sql.Timestamp.from(Instant.now().plusSeconds(300))));
    }

    @Test
    void concurrentDuplicateRegistrationAndOfferCreationRemainUnique() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String duplicateEmail = "race-" + suffix + "@example.test";
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var one = pool.submit(() -> registerConcurrently(duplicateEmail, ready, start));
            var two = pool.submit(() -> registerConcurrently(duplicateEmail, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int registrations = (one.get(30, TimeUnit.SECONDS) ? 1 : 0)
                    + (two.get(30, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, registrations);
            assertTrue(users.findByEmail(duplicateEmail).isPresent());
        } finally {
            start.countDown();
            pool.shutdownNow();
        }

        User recruiter = users.saveAndFlush(User.builder().email("offer-recruiter-" + suffix + "@example.test")
                .password("{bcrypt}unused").role(User.Role.RECRUITER).enabled(true).emailVerified(true).build());
        User student = users.saveAndFlush(User.builder().email("offer-student-" + suffix + "@example.test")
                .password("{bcrypt}unused").role(User.Role.STUDENT).enabled(true).emailVerified(true).build());
        Job job = jobs.saveAndFlush(Job.builder().title("Concurrent Offer").description("Integration test")
                .companyName("Example Co").minCgpa(6.0).maxBacklogs(2).deadline(LocalDate.now().plusDays(30))
                .recruiterId(recruiter.getId()).published(true).closed(false).build());
        Application application = applications.saveAndFlush(Application.builder().student(student).job(job)
                .status(Application.Status.INTERVIEW_SCHEDULED).appliedAt(Instant.now()).build());
        CountDownLatch offerStart = new CountDownLatch(1);
        var offerPool = Executors.newFixedThreadPool(2);
        try {
            var one = offerPool.submit(() -> createOfferConcurrently(recruiter.getEmail(), application.getId(), offerStart));
            var two = offerPool.submit(() -> createOfferConcurrently(recruiter.getEmail(), application.getId(), offerStart));
            offerStart.countDown();
            int created = (one.get(30, TimeUnit.SECONDS) ? 1 : 0)
                    + (two.get(30, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, created);
            assertTrue(offers.findByApplicationId(application.getId()).isPresent());
        } finally {
            offerPool.shutdownNow();
        }
    }

    private boolean registerConcurrently(String email, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        try {
            authService.register(new com.example.demo.dto.RegisterRequest(email, "Long-Enough-Test-Password",
                    User.Role.RECRUITER, null, null, null, null, null, null));
            return true;
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.CONFLICT) {
                throw exception;
            }
            return false;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    private boolean createOfferConcurrently(String email, Long applicationId, CountDownLatch start) {
        await(start);
        try {
            recruiterService.createOffer(email, applicationId,
                    new OfferRequest("Test package", 100000.0, Instant.now().plusSeconds(86400)));
            return true;
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.CONFLICT) {
                throw exception;
            }
            return false;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test worker interrupted");
        }
    }
}
