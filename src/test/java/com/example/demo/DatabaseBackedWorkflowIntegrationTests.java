package com.example.demo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.security.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DatabaseBackedWorkflowIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired JobRepository jobs;
    @Autowired ApplicationRepository applications;
    @Autowired StudentProfileRepository profiles;
    @Autowired InterviewScheduleRepository interviews;
    @Autowired PlacementOfficerProfileRepository officerProfiles;
    @Autowired CollegeRepository colleges;
    @Autowired PlacementDriveRepository drives;
    @Autowired AuditLogRepository auditLogs;
    @Autowired OfferRepository offers;
    @Autowired JwtUtils jwtUtils;
    @Autowired ObjectMapper json;

    private User recruiterA, recruiterB, studentA, studentB, officerA, officerB, admin, disabled;
    private Job ownedJob;

    @BeforeEach
    void seed() {
        recruiterA = users.save(User.builder().email("r1@example.com").password("{bcrypt}unused").role(User.Role.RECRUITER).enabled(true).emailVerified(true).build());
        recruiterB = users.save(User.builder().email("r2@example.com").password("{bcrypt}unused").role(User.Role.RECRUITER).enabled(true).emailVerified(true).build());
        studentA = users.save(User.builder().email("s1@example.com").password("{bcrypt}unused").role(User.Role.STUDENT).enabled(true).emailVerified(true).build());
        studentB = users.save(User.builder().email("s2@example.com").password("{bcrypt}unused").role(User.Role.STUDENT).enabled(true).emailVerified(true).build());
        officerA = users.save(User.builder().email("o1@example.com").password("{bcrypt}unused").role(User.Role.PLACEMENT_OFFICER).enabled(true).emailVerified(true).build());
        officerB = users.save(User.builder().email("o2@example.com").password("{bcrypt}unused").role(User.Role.PLACEMENT_OFFICER).enabled(true).emailVerified(true).build());
        admin = users.save(User.builder().email("admin@example.com").password("{bcrypt}unused").role(User.Role.ADMIN).enabled(true).emailVerified(true).build());
        disabled = users.save(User.builder().email("disabled@example.com").password("{bcrypt}unused").role(User.Role.STUDENT).enabled(false).build());
        ownedJob = jobs.save(Job.builder().title("Backend Engineer").description("Build APIs").companyName("Verified Co")
                .minCgpa(7.0).maxBacklogs(2).deadline(LocalDate.now().plusDays(30)).recruiterId(recruiterA.getId())
                .published(true).closed(false).build());
        profiles.save(StudentProfile.builder().user(studentA).fullName("Student One").rollNumber("S1").department("CSE").cgpa(8.5).backlogs(0).build());
        profiles.save(StudentProfile.builder().user(studentB).fullName("Student Two").rollNumber("S2").department("ECE").cgpa(8.0).backlogs(0).build());
    }

    @Test
    @WithMockUser(username = "r2@example.com", roles = "RECRUITER")
    void recruiterCannotModifyOrListAnotherRecruitersJob() throws Exception {
        mvc.perform(put("/api/recruiter/jobs/" + ownedJob.getId())
                .contentType("application/json").content(jobJson()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/recruiter/jobs/" + ownedJob.getId() + "/applications"))
                .andExpect(status().isForbidden());
        Application application = applications.save(Application.builder().student(studentA).job(ownedJob)
                .status(Application.Status.INTERVIEW_SCHEDULED).appliedAt(Instant.now()).build());
        interviews.save(InterviewSchedule.builder().application(application).scheduledAt(Instant.now().plusSeconds(3600))
                .mode("ONLINE").createdAt(Instant.now()).build());
        mvc.perform(get("/api/recruiter/interviews")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "s1@example.com", roles = "STUDENT")
    void studentProfileApiReturnsOnlyAuthenticatedProfile() throws Exception {
        mvc.perform(get("/api/student/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Student One"));
    }

    @Test
    void disabledUserCannotAccessProtectedApi() throws Exception {
        String token = jwtUtils.generateToken(disabled.getEmail(), disabled.getRole().name());
        mvc.perform(get("/api/student/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unverifiedUserCannotAccessProtectedApi() throws Exception {
        User unverified = users.save(User.builder().email("unverified@example.com").password("{bcrypt}unused")
                .role(User.Role.STUDENT).enabled(true).emailVerified(false).build());
        String token = jwtUtils.generateToken(unverified.getEmail(), unverified.getRole().name());
        mvc.perform(get("/api/student/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "r1@example.com", roles = "RECRUITER")
    void legacyJobCreateCannotSetOwnershipOrPublish() throws Exception {
        companies.save(Company.builder().name("Verified Co").ownerUserId(recruiterA.getId()).verified(true).build());
        mvc.perform(post("/api/jobs").contentType("application/json")
                .content("{\"id\":" + ownedJob.getId()
                        + ",\"title\":\"Forged\",\"description\":\"Description\",\"companyName\":\"Verified Co\","
                        + "\"minCgpa\":6.0,\"maxBacklogs\":2,\"deadline\":\"" + LocalDate.now().plusDays(10)
                        + "\",\"recruiterId\":" + recruiterB.getId() + ",\"published\":true,\"closed\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(not(ownedJob.getId().intValue())))
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.closed").value(false))
                .andExpect(jsonPath("$.recruiterId").doesNotExist());
    }

    @Test
    @WithMockUser(username = "r1@example.com", roles = "RECRUITER")
    void legacyApplicationRoutesEnforceRoleAndOfferLifecycle() throws Exception {
        Application application = applications.save(Application.builder().student(studentA).job(ownedJob)
                .status(Application.Status.APPLIED).appliedAt(Instant.now()).build());
        mvc.perform(get("/api/applications/student")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/applications/" + application.getId() + "/status").param("status", "OFFERED"))
                .andExpect(status().isConflict());
    }

    @Test
    void enabledStudentJwtCanAccessStudentApi() throws Exception {
        String token = jwtUtils.generateToken(studentA.getEmail(), studentA.getRole().name());
        mvc.perform(get("/api/student/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(studentA.getEmail()));
    }

    @Test
    @WithMockUser(username = "r1@example.com", roles = "RECRUITER")
    void unverifiedCompanyCannotPublishJob() throws Exception {
        companies.save(Company.builder().name("Unverified Co").ownerUserId(recruiterA.getId()).verified(false).build());
        mvc.perform(post("/api/recruiter/jobs/" + ownedJob.getId() + "/publish"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "s1@example.com", roles = "STUDENT")
    void duplicateApplicationIsRejected() throws Exception {
        applications.save(Application.builder().student(studentA).job(ownedJob).status(Application.Status.APPLIED).appliedAt(Instant.now()).build());
        mvc.perform(post("/api/student/applications/" + ownedJob.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "s1@example.com", roles = "STUDENT")
    void expiredApplicationIsRejected() throws Exception {
        ownedJob.setDeadline(LocalDate.now().minusDays(1));
        jobs.save(ownedJob);
        mvc.perform(post("/api/student/applications/" + ownedJob.getId()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "r1@example.com", roles = "RECRUITER")
    void interviewFeedbackRequiresOwnedApplication() throws Exception {
        Application application = applications.save(Application.builder().student(studentA).job(ownedJob)
                .status(Application.Status.INTERVIEW_SCHEDULED).appliedAt(Instant.now()).build());
        InterviewSchedule interview = interviews.save(InterviewSchedule.builder().application(application)
                .scheduledAt(Instant.now().minusSeconds(3600)).mode("ONLINE").createdAt(Instant.now()).build());

        mvc.perform(post("/api/recruiter/applications/" + application.getId() + "/interviews/" + interview.getId() + "/feedback")
                .contentType("application/json").content("{\"feedback\":\"Strong technical performance\"}"))
                .andExpect(status().isOk());

        InterviewSchedule other = interviews.save(InterviewSchedule.builder().application(application)
                .scheduledAt(Instant.now().plusSeconds(7200)).mode("ONLINE").createdAt(Instant.now()).build());
        Job otherJob = jobs.save(Job.builder().title("Other").description("Other").companyName("Other")
                .minCgpa(0.0).maxBacklogs(10).deadline(LocalDate.now().plusDays(10)).recruiterId(recruiterB.getId())
                .published(true).closed(false).build());
        Application otherApplication = applications.save(Application.builder().student(studentB).job(otherJob)
                .status(Application.Status.INTERVIEW_SCHEDULED).appliedAt(Instant.now()).build());
        mvc.perform(post("/api/recruiter/applications/" + otherApplication.getId() + "/interviews/" + other.getId() + "/feedback")
                .contentType("application/json").content("{\"feedback\":\"Unauthorized\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "o1@example.com", roles = "PLACEMENT_OFFICER")
    void officerCannotUpdateAnotherCollegesDrive() throws Exception {
        College one = colleges.save(College.builder().code("C1").name("College One").active(true).build());
        College two = colleges.save(College.builder().code("C2").name("College Two").active(true).build());
        officerProfiles.save(PlacementOfficerProfile.builder().user(officerA).college(one).fullName("Officer One").build());
        officerProfiles.save(PlacementOfficerProfile.builder().user(officerB).college(two).fullName("Officer Two").build());
        PlacementDrive drive = drives.save(PlacementDrive.builder().college(two).title("Campus Drive").registrationDeadline(LocalDate.now().plusDays(20))
                .status(PlacementDrive.DriveStatus.OPEN).createdByOfficerId(officerB.getId()).build());
        mvc.perform(patch("/api/placement/drives/" + drive.getId() + "/status").param("status", "CLOSED"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "s1@example.com", roles = "STUDENT")
    void studentCanRegisterForOpenDriveOnlyOnce() throws Exception {
        College college = colleges.save(College.builder().code("DRIVE1").name("Drive College").active(true).build());
        PlacementDrive drive = drives.save(PlacementDrive.builder().college(college).title("Open Campus Drive")
                .registrationDeadline(LocalDate.now().plusDays(2)).status(PlacementDrive.DriveStatus.OPEN)
                .createdByOfficerId(officerA.getId()).build());

        mvc.perform(get("/api/student/placement-drives"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(drive.getId()));
        mvc.perform(post("/api/student/placement-drives/" + drive.getId() + "/registration"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.registrationStatus").value("REGISTERED"));
        mvc.perform(post("/api/student/placement-drives/" + drive.getId() + "/registration"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "s1@example.com", roles = "STUDENT")
    void studentCannotRegisterForExpiredOrClosedDrive() throws Exception {
        College college = colleges.save(College.builder().code("DRIVE2").name("Expired Drive College").active(true).build());
        PlacementDrive expired = drives.save(PlacementDrive.builder().college(college).title("Expired Drive")
                .registrationDeadline(LocalDate.now().minusDays(1)).status(PlacementDrive.DriveStatus.OPEN)
                .createdByOfficerId(officerA.getId()).build());
        PlacementDrive closed = drives.save(PlacementDrive.builder().college(college).title("Closed Drive")
                .registrationDeadline(LocalDate.now().plusDays(5)).status(PlacementDrive.DriveStatus.CLOSED)
                .createdByOfficerId(officerA.getId()).build());

        mvc.perform(post("/api/student/placement-drives/" + expired.getId() + "/registration"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/student/placement-drives/" + closed.getId() + "/registration"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void adminStatisticsAndAuditRecordsComeFromDatabase() throws Exception {
        auditLogs.save(AuditLog.builder().actorEmail("admin@example.com").action("TEST_ACTION").targetType("USER")
                .targetId("1").details("Integration test").createdAt(Instant.now()).build());
        mvc.perform(get("/api/admin/stats")).andExpect(status().isOk())
                .andExpect(jsonPath("$.users").value(greaterThanOrEqualTo(8)));
        mvc.perform(get("/api/admin/audit-logs")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action=='TEST_ACTION')]", hasSize(1)));
    }

    @Test
    @WithMockUser(username = "r1@example.com", roles = "RECRUITER")
    void interviewCanBeRescheduledAndCancelledByOwner() throws Exception {
        Application application = applications.save(Application.builder().student(studentA).job(ownedJob)
                .status(Application.Status.INTERVIEW_SCHEDULED).appliedAt(Instant.now()).build());
        InterviewSchedule interview = interviews.save(InterviewSchedule.builder().application(application)
                .scheduledAt(Instant.now().plusSeconds(3600)).mode("ONLINE").createdAt(Instant.now()).build());
        mvc.perform(put("/api/recruiter/applications/" + application.getId() + "/interviews/" + interview.getId())
                .contentType("application/json").content("{\"scheduledAt\":\"" + Instant.now().plusSeconds(7200) +
                        "\",\"mode\":\"ONLINE\",\"locationOrLink\":\"https://meet.example\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESCHEDULED"));
        mvc.perform(post("/api/recruiter/applications/" + application.getId() + "/interviews/" + interview.getId() + "/cancel"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(put("/api/recruiter/applications/" + application.getId() + "/interviews/" + interview.getId())
                .contentType("application/json").content("{\"scheduledAt\":\"" + Instant.now().plusSeconds(7200) +
                        "\",\"mode\":\"ONLINE\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/recruiter/applications/" + application.getId() + "/interviews/" + interview.getId() + "/cancel"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/recruiter/applications/" + application.getId() + "/interviews/" + interview.getId() + "/feedback")
                .contentType("application/json").content("{\"feedback\":\"Cannot submit after cancellation\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "r1@example.com", roles = "RECRUITER")
    void offerLifecycleAndDuplicatePreventionUseDatabase() throws Exception {
        Application application = applications.save(Application.builder().student(studentA).job(ownedJob)
                .status(Application.Status.INTERVIEW_SCHEDULED).appliedAt(Instant.now()).build());
        String body = "{\"packageDetails\":\"12 LPA\",\"packageAmount\":1200000,\"joiningDate\":\"" +
                Instant.now().plusSeconds(86400 * 30) + "\"}";
        mvc.perform(patch("/api/recruiter/applications/" + application.getId() + "/status")
                .contentType("application/json").content("{\"status\":\"OFFERED\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/recruiter/applications/" + application.getId() + "/offers")
                .contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ISSUED"));
        mvc.perform(post("/api/recruiter/applications/" + application.getId() + "/offers")
                .contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(get("/api/recruiter/offers")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ISSUED"));
    }

    @Test
    @WithMockUser(username = "s1@example.com", roles = "STUDENT")
    void studentCanOnlyAcceptOrRejectOwnOffer() throws Exception {
        Application application = applications.save(Application.builder().student(studentA).job(ownedJob)
                .status(Application.Status.OFFERED).appliedAt(Instant.now()).build());
        Offer offer = offers.save(Offer.builder().application(application).packageDetails("10 LPA")
                .packageAmount(1000000.0).joiningDate(Instant.now().plusSeconds(86400 * 30))
                .status(Offer.Status.ISSUED).createdAt(Instant.now()).updatedAt(Instant.now()).build());
        mvc.perform(get("/api/student/offers")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobTitle").value("Backend Engineer"));
        mvc.perform(patch("/api/student/offers/" + offer.getId() + "/status")
                .contentType("application/json").content("{\"status\":\"WITHDRAWN\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/student/offers/" + offer.getId() + "/status")
                .contentType("application/json").content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
        mvc.perform(patch("/api/student/offers/" + offer.getId() + "/status")
                .contentType("application/json").content("{\"status\":\"DECLINED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "r1@example.com", roles = "RECRUITER")
    void recruiterCannotAcceptOrRejectOfferForStudent() throws Exception {
        Application application = applications.save(Application.builder().student(studentA).job(ownedJob)
                .status(Application.Status.OFFERED).appliedAt(Instant.now()).build());
        Offer offer = offers.save(Offer.builder().application(application).packageDetails("10 LPA")
                .packageAmount(1000000.0).joiningDate(Instant.now().plusSeconds(86400 * 30))
                .status(Offer.Status.ISSUED).createdAt(Instant.now()).updatedAt(Instant.now()).build());
        mvc.perform(patch("/api/recruiter/offers/" + offer.getId() + "/status")
                .contentType("application/json").content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/recruiter/offers/" + offer.getId() + "/status")
                .contentType("application/json").content("{\"status\":\"DECLINED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "r2@example.com", roles = "RECRUITER")
    void recruiterCannotReadOrModifyAnotherRecruitersOffer() throws Exception {
        Application application = applications.save(Application.builder().student(studentA).job(ownedJob)
                .status(Application.Status.OFFERED).appliedAt(Instant.now()).build());
        Offer offer = offers.save(Offer.builder().application(application).packageDetails("10 LPA")
                .packageAmount(1000000.0).joiningDate(Instant.now().plusSeconds(86400 * 30))
                .status(Offer.Status.ISSUED).createdAt(Instant.now()).updatedAt(Instant.now()).build());
        mvc.perform(get("/api/recruiter/offers")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(patch("/api/recruiter/offers/" + offer.getId() + "/status")
                .contentType("application/json").content("{\"status\":\"WITHDRAWN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "s2@example.com", roles = "STUDENT")
    void studentCannotReadOrRespondToAnotherStudentsOfferOrInterview() throws Exception {
        Application application = applications.save(Application.builder().student(studentA).job(ownedJob)
                .status(Application.Status.OFFERED).appliedAt(Instant.now()).build());
        Offer offer = offers.save(Offer.builder().application(application).packageDetails("10 LPA")
                .packageAmount(1000000.0).joiningDate(Instant.now().plusSeconds(86400 * 30))
                .status(Offer.Status.ISSUED).createdAt(Instant.now()).updatedAt(Instant.now()).build());
        interviews.save(InterviewSchedule.builder().application(application).scheduledAt(Instant.now().plusSeconds(3600))
                .mode("ONLINE").createdAt(Instant.now()).build());
        mvc.perform(get("/api/student/offers")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/student/applications")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(patch("/api/student/offers/" + offer.getId() + "/status")
                .contentType("application/json").content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/student/interviews")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    private String jobJson() throws Exception {
        return json.writeValueAsString(Map.of(
                "title", "Backend Engineer", "description", "Build APIs", "companyName", "Verified Co",
                "minCgpa", 7.0, "maxBacklogs", 2, "deadline", LocalDate.now().plusDays(30).toString()));
    }
}
