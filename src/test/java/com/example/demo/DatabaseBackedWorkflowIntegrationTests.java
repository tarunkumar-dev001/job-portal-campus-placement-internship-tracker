package com.example.demo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.AuthSecurityService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @Autowired ApplicationStatusHistoryRepository applicationHistory;
    @Autowired StudentProfileRepository profiles;
    @Autowired InterviewScheduleRepository interviews;
    @Autowired PlacementOfficerProfileRepository officerProfiles;
    @Autowired CollegeRepository colleges;
    @Autowired DepartmentRepository departments;
    @Autowired PlacementDriveRepository drives;
    @Autowired AuditLogRepository auditLogs;
    @Autowired OfferRepository offers;
    @Autowired NotificationRepository notifications;
    @Autowired DriveRegistrationRepository driveRegistrations;
    @Autowired AuthSecurityService authSecurityService;
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
        disabled.setEnabled(true);
        disabled.setEmailVerified(true);
        users.saveAndFlush(disabled);
        String token = authSecurityService.issueSession(disabled).token();
        disabled.setEnabled(false);
        users.saveAndFlush(disabled);
        mvc.perform(get("/api/student/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unverifiedUserCannotAccessProtectedApi() throws Exception {
        User unverified = users.save(User.builder().email("unverified@example.com").password("{bcrypt}unused")
                .role(User.Role.STUDENT).enabled(true).emailVerified(true).build());
        String token = authSecurityService.issueSession(unverified).token();
        unverified.setEmailVerified(false);
        users.saveAndFlush(unverified);
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
        String token = authSecurityService.issueSession(studentA).token();
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
    @WithMockUser(username = "r1@example.com", roles = "RECRUITER")
    void recruiterMustProvideAndPersistsTrimmedRejectionExplanation() throws Exception {
        Application application = applications.save(Application.builder().student(studentA).job(ownedJob)
                .status(Application.Status.APPLIED).appliedAt(Instant.now()).build());

        mvc.perform(patch("/api/recruiter/applications/" + application.getId() + "/status")
                .contentType("application/json").content("{\"status\":\"REJECTED\",\"rejectionExplanation\":\"Too short\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/applications/" + application.getId() + "/status")
                .param("status", "REJECTED").param("rejectionExplanation", "x".repeat(1001)))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/recruiter/applications/" + application.getId() + "/status")
                .contentType("application/json").content("{\"status\":\"SHORTLISTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHORTLISTED"));
        mvc.perform(patch("/api/recruiter/applications/" + application.getId() + "/status")
                .contentType("application/json")
                .content("{\"status\":\"REJECTED\",\"rejectionExplanation\":\"  The candidate does not have the required backend experience.  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(patch("/api/recruiter/applications/" + application.getId() + "/status")
                .contentType("application/json").content("{\"status\":\"SHORTLISTED\"}"))
                .andExpect(status().isConflict());

        assertEquals("The candidate does not have the required backend experience.",
                applicationHistory.findByApplicationIdOrderByChangedAtAscIdAsc(application.getId()).get(1).getNote());

        Job anotherRecruitersJob = jobs.save(Job.builder().title("Other role").description("Other role")
                .companyName("Other Co").minCgpa(0.0).maxBacklogs(10).deadline(LocalDate.now().plusDays(10))
                .recruiterId(recruiterB.getId()).published(true).closed(false).build());
        Application anotherApplication = applications.save(Application.builder().student(studentB)
                .job(anotherRecruitersJob).status(Application.Status.APPLIED).appliedAt(Instant.now()).build());
        mvc.perform(patch("/api/recruiter/applications/" + anotherApplication.getId() + "/status")
                .contentType("application/json").content("{\"status\":\"SHORTLISTED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "s1@example.com", roles = "STUDENT")
    void studentSeesOwnRejectionExplanationButNotAnotherStudentsHistory() throws Exception {
        Application ownApplication = applications.save(Application.builder().student(studentA).job(ownedJob)
                .status(Application.Status.REJECTED).appliedAt(Instant.now()).build());
        Application otherApplication = applications.save(Application.builder().student(studentB).job(ownedJob)
                .status(Application.Status.REJECTED).appliedAt(Instant.now()).build());
        applicationHistory.save(ApplicationStatusHistory.builder().application(ownApplication)
                .status(Application.Status.REJECTED).changedBy("r1@example.com").changedAt(Instant.now())
                .note("The role requires more production Java experience.").build());
        applicationHistory.save(ApplicationStatusHistory.builder().application(otherApplication)
                .status(Application.Status.REJECTED).changedBy("r1@example.com").changedAt(Instant.now())
                .note("Private information for another student.").build());

        mvc.perform(get("/api/student/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].history[0].rejectionExplanation")
                        .value("The role requires more production Java experience."))
                .andExpect(content().string(not(containsString("Private information for another student."))));
    }

    @Test
    @WithMockUser(username = "s1@example.com", roles = "STUDENT")
    void withdrawalIsRecordedAndReturnedWithItsTimestamp() throws Exception {
        Application application = applications.save(Application.builder().student(studentA).job(ownedJob)
                .status(Application.Status.APPLIED).appliedAt(Instant.now()).build());
        applicationHistory.save(ApplicationStatusHistory.builder().application(application)
                .status(Application.Status.APPLIED).changedBy("s1@example.com").changedAt(Instant.now())
                .note("Application submitted").build());

        String response = mvc.perform(post("/api/student/applications/" + application.getId() + "/withdraw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withdrawnAt").isNotEmpty())
                .andExpect(jsonPath("$.history[1].status").value("WITHDRAWN"))
                .andReturn().getResponse().getContentAsString();
        var responseJson = json.readTree(response);
        var withdrawalHistory = applicationHistory
                .findByApplicationIdOrderByChangedAtAscIdAsc(application.getId()).get(1);
        assertEquals(responseJson.get("withdrawnAt").asText(), responseJson.get("history").get(1)
                .get("changedAt").asText());
        assertEquals(withdrawalHistory.getChangedAt(), applications.findById(application.getId()).orElseThrow()
                .getWithdrawnAt());

        mvc.perform(post("/api/student/applications/" + application.getId() + "/withdraw"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "s1@example.com", roles = "STUDENT")
    void studentCannotWithdrawAnotherStudentsApplication() throws Exception {
        Application otherApplication = applications.save(Application.builder().student(studentB).job(ownedJob)
                .status(Application.Status.APPLIED).appliedAt(Instant.now()).build());
        mvc.perform(post("/api/student/applications/" + otherApplication.getId() + "/withdraw"))
                .andExpect(status().isForbidden());
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
    @WithMockUser(username = "o1@example.com", roles = "PLACEMENT_OFFICER")
    void officerCanReadAndCreateDepartmentsInOwnCollege() throws Exception {
        College ownCollege = colleges.save(College.builder().code("OWN1").name("Own College").active(true).build());
        College otherCollege = colleges.save(College.builder().code("OTHER1").name("Other College").active(true).build());
        officerProfiles.save(PlacementOfficerProfile.builder().user(officerA)
                .college(ownCollege).fullName("Officer One").build());
        departments.save(Department.builder().college(ownCollege).code("OWN-CS")
                .name("Own Computer Science").seats(40).build());
        departments.save(Department.builder().college(otherCollege).code("OTHER-CS")
                .name("Other Computer Science").seats(30).build());

        mvc.perform(get("/api/placement/colleges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(ownCollege.getId()));
        mvc.perform(get("/api/placement/colleges/" + ownCollege.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownCollege.getId()));
        mvc.perform(get("/api/placement/colleges/" + ownCollege.getId() + "/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Own Computer Science"));
        mvc.perform(post("/api/placement/colleges/" + ownCollege.getId() + "/departments")
                        .contentType("application/json")
                        .content("{\"code\":\"OWN-EE\",\"name\":\"Own Electrical Engineering\",\"seats\":25}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.collegeId").value(ownCollege.getId()));
    }

    @Test
    @WithMockUser(username = "o1@example.com", roles = "PLACEMENT_OFFICER")
    void officerCannotReadOrCreateDepartmentsInAnotherCollege() throws Exception {
        College ownCollege = colleges.save(College.builder().code("OWN2").name("Own College").active(true).build());
        College otherCollege = colleges.save(College.builder().code("OTHER2").name("Other College").active(true).build());
        officerProfiles.save(PlacementOfficerProfile.builder().user(officerA)
                .college(ownCollege).fullName("Officer One").build());
        departments.save(Department.builder().college(otherCollege).code("OTHER-CS")
                .name("Other Computer Science").seats(30).build());

        mvc.perform(get("/api/placement/colleges/" + otherCollege.getId()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/placement/colleges/" + otherCollege.getId() + "/departments"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/placement/colleges/" + otherCollege.getId() + "/departments")
                        .contentType("application/json")
                        .content("{\"code\":\"OTHER-EE\",\"name\":\"Other Electrical Engineering\",\"seats\":25}"))
                .andExpect(status().isForbidden());

        assertEquals(1, departments.findByCollegeId(otherCollege.getId()).size());
    }

    @Test
    @WithMockUser(username = "o1@example.com", roles = "PLACEMENT_OFFICER")
    void unassignedOfficerCannotAccessCollegeScopedOperations() throws Exception {
        College college = colleges.save(College.builder().code("UNASSIGNED1")
                .name("Unassigned Test College").active(true).build());
        officerProfiles.save(PlacementOfficerProfile.builder().user(officerA)
                .fullName("Unassigned Officer").build());

        mvc.perform(get("/api/placement/colleges"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/placement/colleges/" + college.getId()))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/placement/colleges/" + college.getId() + "/departments"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/placement/colleges/" + college.getId() + "/departments")
                        .contentType("application/json")
                        .content("{\"code\":\"UNASSIGNED-CS\",\"name\":\"Computer Science\",\"seats\":25}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void adminRetainsGlobalCollegeAccessAndOfficerAssignment() throws Exception {
        College first = colleges.save(College.builder().code("ADMIN1").name("Admin College One").active(true).build());
        colleges.save(College.builder().code("ADMIN2").name("Admin College Two").active(true).build());
        PlacementOfficerProfile profile = officerProfiles.save(PlacementOfficerProfile.builder()
                .user(officerA).fullName("Officer One").build());

        mvc.perform(get("/api/admin/colleges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(patch("/api/admin/placement-officers/" + officerA.getId() + "/college")
                        .contentType("application/json")
                        .content("{\"collegeId\":" + first.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collegeId").value(first.getId()));

        assertEquals(first.getId(), officerProfiles.findById(profile.getId()).orElseThrow()
                .getCollege().getId());
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
        String rescheduledAt = Instant.now().plusSeconds(7200).toString();
        String rescheduleRequest = "{\"scheduledAt\":\"" + rescheduledAt
                + "\",\"mode\":\"ONLINE\",\"locationOrLink\":\"https://meet.example\"}";
        mvc.perform(put("/api/recruiter/applications/" + application.getId() + "/interviews/" + interview.getId())
                .contentType("application/json").content(rescheduleRequest))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESCHEDULED"));
        assertEquals(1, studentNotifications(studentA).size());
        assertEquals("Interview rescheduled", studentNotifications(studentA).get(0).getTitle());
        mvc.perform(put("/api/recruiter/applications/" + application.getId() + "/interviews/" + interview.getId())
                .contentType("application/json").content(rescheduleRequest))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESCHEDULED"));
        assertEquals(1, studentNotifications(studentA).size());
        mvc.perform(put("/api/recruiter/applications/" + application.getId() + "/interviews/" + interview.getId())
                .contentType("application/json")
                .content("{\"scheduledAt\":\"" + rescheduledAt
                        + "\",\"mode\":\"ONLINE\",\"locationOrLink\":\"https://meet.example\","
                        + "\"notes\":\"Updated interview details\"}"))
                .andExpect(status().isOk());
        assertEquals(2, studentNotifications(studentA).size());
        mvc.perform(post("/api/recruiter/applications/" + application.getId() + "/interviews/" + interview.getId() + "/cancel"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals(3, studentNotifications(studentA).size());
        assertEquals("Interview cancelled", studentNotifications(studentA).get(0).getTitle());
        mvc.perform(put("/api/recruiter/applications/" + application.getId() + "/interviews/" + interview.getId())
                .contentType("application/json").content("{\"scheduledAt\":\"" + Instant.now().plusSeconds(7200) +
                        "\",\"mode\":\"ONLINE\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/recruiter/applications/" + application.getId() + "/interviews/" + interview.getId() + "/cancel"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/recruiter/applications/" + application.getId() + "/interviews/" + interview.getId() + "/feedback")
                .contentType("application/json").content("{\"feedback\":\"Cannot submit after cancellation\"}"))
                .andExpect(status().isConflict());
        assertEquals(3, studentNotifications(studentA).size());
    }

    @Test
    @WithMockUser(username = "r1@example.com", roles = "RECRUITER")
    void rescheduleNotificationComparisonTreatsNullAndBlankOptionalFieldsEquivalently() throws Exception {
        Application application = applications.save(Application.builder().student(studentA).job(ownedJob)
                .status(Application.Status.INTERVIEW_SCHEDULED).appliedAt(Instant.now()).build());
        String scheduledAt = Instant.now().plusSeconds(10000).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();

        InterviewSchedule nullLocation = saveInterview(application, scheduledAt, null, null);
        String emptyLocationRequest = rescheduleBody(scheduledAt, "", null);
        performReschedule(application, nullLocation, emptyLocationRequest);
        assertEquals(0, studentNotifications(studentA).size());
        performReschedule(application, nullLocation, emptyLocationRequest);
        assertEquals(0, studentNotifications(studentA).size());
        assertEquals("", interviews.findById(nullLocation.getId()).orElseThrow().getLocationOrLink());

        InterviewSchedule emptyFields = saveInterview(application, scheduledAt, "", "");
        String whitespaceRequest = rescheduleBody(scheduledAt, " \t ", "\n ");
        performReschedule(application, emptyFields, whitespaceRequest);
        performReschedule(application, emptyFields, whitespaceRequest);
        assertEquals(0, studentNotifications(studentA).size());
        assertEquals(" \t ", interviews.findById(emptyFields.getId()).orElseThrow().getLocationOrLink());
        assertEquals("\n ", interviews.findById(emptyFields.getId()).orElseThrow().getNotes());

        InterviewSchedule nonblankNotes = saveInterview(application, scheduledAt, "Room 2", "Bring portfolio");
        performReschedule(application, nonblankNotes, rescheduleBody(scheduledAt, "Room 2", "   "));
        assertEquals(1, studentNotifications(studentA).size());
        assertEquals("   ", interviews.findById(nonblankNotes.getId()).orElseThrow().getNotes());

        InterviewSchedule blankNotes = saveInterview(application, scheduledAt, "Room 2", " ");
        performReschedule(application, blankNotes, rescheduleBody(scheduledAt, "Room 2", "Bring portfolio"));
        assertEquals(2, studentNotifications(studentA).size());
        assertEquals("Bring portfolio", interviews.findById(blankNotes.getId()).orElseThrow().getNotes());

        InterviewSchedule identicalNonblank = saveInterview(application, scheduledAt,
                "Room 2", "Bring portfolio");
        performReschedule(application, identicalNonblank,
                rescheduleBody(scheduledAt, "Room 2", "Bring portfolio"));
        assertEquals(2, studentNotifications(studentA).size());
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
        assertEquals(0, studentNotifications(studentA).size());
        mvc.perform(post("/api/recruiter/applications/" + application.getId() + "/offers")
                .contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ISSUED"));
        assertEquals(1, studentNotifications(studentA).size());
        assertEquals("Offer received", studentNotifications(studentA).get(0).getTitle());
        mvc.perform(post("/api/recruiter/applications/" + application.getId() + "/offers")
                .contentType("application/json").content(body)).andExpect(status().isConflict());
        assertEquals(1, studentNotifications(studentA).size());
        mvc.perform(get("/api/recruiter/offers")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ISSUED"));
        Offer issued = offers.findByApplicationId(application.getId()).orElseThrow();
        mvc.perform(patch("/api/recruiter/offers/" + issued.getId() + "/status")
                .contentType("application/json").content("{\"status\":\"WITHDRAWN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"));
        assertEquals(2, studentNotifications(studentA).size());
        assertEquals("Offer withdrawn", studentNotifications(studentA).get(0).getTitle());
        mvc.perform(patch("/api/recruiter/offers/" + issued.getId() + "/status")
                .contentType("application/json").content("{\"status\":\"WITHDRAWN\"}"))
                .andExpect(status().isConflict());
        assertEquals(2, studentNotifications(studentA).size());
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
        assertEquals(0, studentNotifications(studentA).size());
        mvc.perform(patch("/api/student/offers/" + offer.getId() + "/status")
                .contentType("application/json").content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
        assertEquals("Offer accepted", studentNotifications(studentA).get(0).getTitle());
        mvc.perform(patch("/api/student/offers/" + offer.getId() + "/status")
                .contentType("application/json").content("{\"status\":\"DECLINED\"}"))
                .andExpect(status().isConflict());

        Job secondJob = jobs.save(Job.builder().title("Platform Engineer").description("Build platforms")
                .companyName("Another Co").minCgpa(0.0).maxBacklogs(10).deadline(LocalDate.now().plusDays(10))
                .recruiterId(recruiterA.getId()).published(true).closed(false).build());
        Application secondApplication = applications.save(Application.builder().student(studentA).job(secondJob)
                .status(Application.Status.OFFERED).appliedAt(Instant.now()).build());
        Offer secondOffer = offers.save(Offer.builder().application(secondApplication).packageDetails("11 LPA")
                .packageAmount(1100000.0).joiningDate(Instant.now().plusSeconds(86400 * 30))
                .status(Offer.Status.ISSUED).createdAt(Instant.now()).updatedAt(Instant.now()).build());
        mvc.perform(patch("/api/student/offers/" + secondOffer.getId() + "/status")
                .contentType("application/json").content("{\"status\":\"DECLINED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DECLINED"));
        assertEquals(2, studentNotifications(studentA).size());
        assertEquals("Offer declined", studentNotifications(studentA).get(0).getTitle());
        assertEquals("Offer accepted", studentNotifications(studentA).get(1).getTitle());
    }

    @Test
    @WithMockUser(username = "s1@example.com", roles = "STUDENT")
    void studentCanReadOnlyOwnNotificationsAndMarkAllReadOnlyForSelf() throws Exception {
        Notification ownUnread = saveNotification(studentA, "Interview update", false);
        saveNotification(studentA, "Offer update", false);
        saveNotification(studentB, "Private update", false);

        mvc.perform(get("/api/student/notifications"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].readFlag").value(false));
        mvc.perform(patch("/api/student/notifications/" + ownUnread.getId() + "/read"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.readFlag").value(true));
        mvc.perform(patch("/api/student/notifications/" + studentNotifications(studentB).get(0).getId() + "/read"))
                .andExpect(status().isForbidden());
        assertEquals(false, studentNotifications(studentB).get(0).isReadFlag());

        mvc.perform(patch("/api/student/notifications/read-all")).andExpect(status().isNoContent());
        assertEquals(2, studentNotifications(studentA).stream().filter(Notification::isReadFlag).count());
        assertEquals(false, studentNotifications(studentB).get(0).isReadFlag());
        mvc.perform(get("/api/student/notifications"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].readFlag", everyItem(is(true))));
    }

    @Test
    @WithMockUser(username = "r1@example.com", roles = "RECRUITER")
    void recruiterCannotUseStudentNotificationReadEndpoints() throws Exception {
        mvc.perform(patch("/api/student/notifications/1/read")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/student/notifications/read-all")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "o1@example.com", roles = "PLACEMENT_OFFICER")
    void placementOfficerCannotUseStudentNotificationReadEndpoints() throws Exception {
        mvc.perform(patch("/api/student/notifications/1/read")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/student/notifications/read-all")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void administratorCannotUseStudentNotificationReadEndpoints() throws Exception {
        mvc.perform(patch("/api/student/notifications/1/read")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/student/notifications/read-all")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "o1@example.com", roles = "PLACEMENT_OFFICER")
    void placementSelectionNotifiesOnlyTheSelectedRegisteredStudent() throws Exception {
        College college = colleges.save(College.builder().code("SEL1").name("Selection College").active(true).build());
        PlacementOfficerProfile officer = officerProfiles.save(PlacementOfficerProfile.builder()
                .user(officerA).college(college).fullName("Officer One").build());
        StudentProfile studentProfile = profiles.findByUserId(studentA.getId()).orElseThrow();
        studentProfile.setCollege(college);
        profiles.save(studentProfile);
        PlacementDrive drive = drives.save(PlacementDrive.builder().college(officer.getCollege())
                .title("Campus hiring drive").companyName("Example Company").jobTitle("Engineer")
                .registrationDeadline(LocalDate.now().plusDays(10)).createdByOfficerId(officerA.getId())
                .status(PlacementDrive.DriveStatus.OPEN).build());
        driveRegistrations.save(DriveRegistration.builder().drive(drive).student(studentA)
                .status(DriveRegistration.RegistrationStatus.ELIGIBLE).registeredAt(Instant.now()).build());

        mvc.perform(post("/api/placement/drives/" + drive.getId() + "/selections")
                .contentType("application/json")
                .content("{\"studentId\":" + studentA.getId() + ",\"packageAmount\":1200000}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.studentId").value(studentA.getId()));
        assertEquals(1, studentNotifications(studentA).size());
        assertEquals("Placement selection recorded", studentNotifications(studentA).get(0).getTitle());
        org.junit.jupiter.api.Assertions.assertTrue(studentNotifications(studentA).get(0).getMessage()
                .contains("Engineer at Example Company"));
        org.junit.jupiter.api.Assertions.assertFalse(studentNotifications(studentA).get(0).getMessage()
                .contains("null"));
        assertEquals(0, studentNotifications(studentB).size());

        mvc.perform(post("/api/placement/drives/" + drive.getId() + "/selections")
                .contentType("application/json")
                .content("{\"studentId\":" + studentB.getId() + ",\"jobTitle\":\"Engineer\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, studentNotifications(studentB).size());
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

    private Notification saveNotification(User recipient, String title, boolean readFlag) {
        return notifications.save(Notification.builder().user(recipient).title(title).message(title + " message")
                .type("TEST").readFlag(readFlag).createdAt(Instant.now()).build());
    }

    private InterviewSchedule saveInterview(Application application, String scheduledAt,
            String locationOrLink, String notes) {
        return interviews.save(InterviewSchedule.builder().application(application)
                .scheduledAt(Instant.parse(scheduledAt)).mode("ONLINE").locationOrLink(locationOrLink)
                .notes(notes).createdAt(Instant.now()).build());
    }

    private String rescheduleBody(String scheduledAt, String locationOrLink, String notes) throws Exception {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("scheduledAt", scheduledAt);
        request.put("mode", "ONLINE");
        request.put("locationOrLink", locationOrLink);
        request.put("notes", notes);
        return json.writeValueAsString(request);
    }

    private void performReschedule(Application application, InterviewSchedule interview, String request)
            throws Exception {
        mvc.perform(put("/api/recruiter/applications/" + application.getId() + "/interviews/" + interview.getId())
                .contentType("application/json").content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESCHEDULED"));
    }

    private java.util.List<Notification> studentNotifications(User student) {
        return notifications.findByUserIdOrderByCreatedAtDescIdDesc(student.getId(), PageRequest.of(0, 100));
    }

    private String jobJson() throws Exception {
        return json.writeValueAsString(Map.of(
                "title", "Backend Engineer", "description", "Build APIs", "companyName", "Verified Co",
                "minCgpa", 7.0, "maxBacklogs", 2, "deadline", LocalDate.now().plusDays(30).toString()));
    }
}
