package com.example.demo.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.example.demo.entity.PlacementOfficerProfile;
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

@Service
@Profile("demo")
public class DemoDataSeedService {

    private static final String EMAIL_DOMAIN = "@careerconnect-demo.test";
    private static final String ADMIN_EMAIL = "admin" + EMAIL_DOMAIN;
    private static final String WITHDRAWAL_NOTE = "Application withdrawn";
    private static final int PASSWORD_MIN_LENGTH = 12;
    private static final int PASSWORD_MAX_LENGTH = 72;
    private static final int STUDENT_COUNT = 30;
    private static final int JOB_COUNT = 8;
    private static final int INTERNSHIP_COUNT = 6;
    private static final int APPLICATION_COUNT = 60;

    private static final String[] DEPARTMENTS = {"Computer Science", "Electronics", "Mechanical", "Business"};
    private static final String[] JOB_TITLES = {
            "Backend Engineer", "Product Analyst", "Cloud Engineer", "Data Engineer",
            "Security Associate", "UX Researcher", "QA Automation Engineer", "Operations Analyst",
            "Software Engineering Intern", "Data Science Intern", "Cloud Operations Intern",
            "Product Design Intern", "Cybersecurity Intern", "Business Analyst Intern"
    };
    private static final String[] COMPANIES = {
            "Northstar Labs", "Juniper Works", "Bluejay Systems", "Copperfield Analytics", "Lumen Grove"
    };

    private final UserRepository users;
    private final StudentProfileRepository studentProfiles;
    private final PlacementOfficerProfileRepository officerProfiles;
    private final CollegeRepository colleges;
    private final DepartmentRepository departments;
    private final CompanyRepository companies;
    private final JobRepository jobs;
    private final ApplicationRepository applications;
    private final ApplicationStatusHistoryRepository histories;
    private final InterviewScheduleRepository interviews;
    private final OfferRepository offers;
    private final PlacementDriveRepository drives;
    private final DriveRegistrationRepository registrations;
    private final PlacementSelectionRepository selections;
    private final NotificationRepository notifications;
    private final PasswordEncoder passwordEncoder;
    private final String rawPassword;
    private final boolean seedEnabled;

    public DemoDataSeedService(UserRepository users, StudentProfileRepository studentProfiles,
            PlacementOfficerProfileRepository officerProfiles, CollegeRepository colleges,
            DepartmentRepository departments, CompanyRepository companies, JobRepository jobs,
            ApplicationRepository applications, ApplicationStatusHistoryRepository histories,
            InterviewScheduleRepository interviews, OfferRepository offers,
            PlacementDriveRepository drives, DriveRegistrationRepository registrations,
            PlacementSelectionRepository selections, NotificationRepository notifications,
            PasswordEncoder passwordEncoder,
            @Value("${app.demo.password:}") String rawPassword,
            @Value("${app.demo.seed-enabled:false}") boolean seedEnabled) {
        this.users = users;
        this.studentProfiles = studentProfiles;
        this.officerProfiles = officerProfiles;
        this.colleges = colleges;
        this.departments = departments;
        this.companies = companies;
        this.jobs = jobs;
        this.applications = applications;
        this.histories = histories;
        this.interviews = interviews;
        this.offers = offers;
        this.drives = drives;
        this.registrations = registrations;
        this.selections = selections;
        this.notifications = notifications;
        this.passwordEncoder = passwordEncoder;
        this.rawPassword = rawPassword;
        this.seedEnabled = seedEnabled;
    }

    @Transactional
    public void seed() {
        if (!seedEnabled) {
            throw new IllegalStateException("Demo seeding requires app.demo.seed-enabled=true.");
        }
        if (users.findByEmail(ADMIN_EMAIL).isPresent()) {
            verifyExistingSeed();
            return;
        }
        validatePassword(rawPassword);

        assertReservedIdentitiesAvailable();
        College[] demoColleges = createColleges();
        List<Department> demoDepartments = createDepartments(demoColleges);
        createUser(ADMIN_EMAIL, User.Role.ADMIN);
        List<User> recruiters = createRecruiters();
        List<User> officers = createOfficers(demoColleges);
        List<User> students = createStudents(demoColleges, demoDepartments);
        List<Company> demoCompanies = createCompanies(recruiters);
        List<Job> demoJobs = createJobs(recruiters, demoCompanies);
        List<Application> demoApplications = createApplications(students, demoJobs, recruiters);
        createInterviewsAndOffers(demoApplications);
        createPlacementData(demoColleges, demoJobs, officers, students);
    }

    private void assertReservedIdentitiesAvailable() {
        List<String> reservedEmails = new ArrayList<>();
        reservedEmails.add(ADMIN_EMAIL);
        for (int i = 1; i <= 2; i++) reservedEmails.add("officer" + i + EMAIL_DOMAIN);
        for (int i = 1; i <= 5; i++) reservedEmails.add("recruiter" + i + EMAIL_DOMAIN);
        for (int i = 1; i <= STUDENT_COUNT; i++) reservedEmails.add(studentEmail(i));
        if (reservedEmails.stream().anyMatch(email -> users.findByEmail(email).isPresent())) {
            throw new IllegalStateException("Reserved demo account already exists without the complete demo dataset.");
        }
        if (companies.findAll().stream().anyMatch(company -> List.of(COMPANIES).contains(company.getName()))) {
            throw new IllegalStateException("A reserved demo company name is already in use.");
        }
        if (colleges.findByCodeIgnoreCase("CCDEMO-NORTH").isPresent()
                || colleges.findByCodeIgnoreCase("CCDEMO-SOUTH").isPresent()) {
            throw new IllegalStateException("A reserved demo college code is already in use.");
        }
    }

    private void verifyExistingSeed() {
        List<User> seedUsers = new ArrayList<>();
        seedUsers.add(users.findByEmail(ADMIN_EMAIL).orElseThrow(
                () -> incompleteSeed("The reserved admin account is missing.")));
        seedUsers.addAll(findReservedUsers("officer", 2));
        seedUsers.addAll(findReservedUsers("recruiter", 5));
        seedUsers.addAll(findReservedUsers("student", STUDENT_COUNT));

        requireCanonical(seedUsers.size() == 38);
        Map<String, User> usersByEmail = new HashMap<>();
        seedUsers.forEach(user -> usersByEmail.put(user.getEmail(), user));
        requireCanonical(users.findAll().stream()
                .filter(user -> user.getEmail().endsWith(EMAIL_DOMAIN)).count() == 38);
        verifyAccount(usersByEmail.get(ADMIN_EMAIL), User.Role.ADMIN);
        for (int i = 1; i <= 2; i++) {
            verifyAccount(usersByEmail.get("officer" + i + EMAIL_DOMAIN), User.Role.PLACEMENT_OFFICER);
        }
        for (int i = 1; i <= 5; i++) {
            verifyAccount(usersByEmail.get("recruiter" + i + EMAIL_DOMAIN), User.Role.RECRUITER);
        }
        for (int i = 1; i <= STUDENT_COUNT; i++) {
            verifyAccount(usersByEmail.get(studentEmail(i)), User.Role.STUDENT);
        }

        Map<String, College> demoColleges = verifyCollegesAndDepartments();
        verifyOfficerProfiles(demoColleges, usersByEmail);
        Map<Long, Company> demoCompanies = verifyCompanies(usersByEmail);
        Map<String, Job> demoJobs = verifyJobs(usersByEmail, demoCompanies);
        Map<String, Application> demoApplications = verifyApplications(usersByEmail, demoJobs);
        verifyHistories(demoApplications, usersByEmail);
        verifyInterviewsAndOffers(demoApplications);
        verifyPlacementData(demoColleges, usersByEmail, demoApplications);
        verifyNotifications(demoApplications, usersByEmail);
    }

    private List<User> findReservedUsers(String prefix, int count) {
        List<User> result = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            result.add(users.findByEmail(prefix + i + EMAIL_DOMAIN).orElseThrow(
                    () -> incompleteSeed("A reserved demo account is missing.")));
        }
        return result;
    }

    private void verifyAccount(User user, User.Role role) {
        requireCanonical(user != null && user.getRole() == role && user.isEnabled() && user.isEmailVerified());
    }

    private Map<String, College> verifyCollegesAndDepartments() {
        Map<String, College> result = new HashMap<>();
        result.put("CCDEMO-NORTH", colleges.findByCodeIgnoreCase("CCDEMO-NORTH")
                .orElseThrow(() -> incompleteSeed("The north demo college is missing.")));
        result.put("CCDEMO-SOUTH", colleges.findByCodeIgnoreCase("CCDEMO-SOUTH")
                .orElseThrow(() -> incompleteSeed("The south demo college is missing.")));
        requireCanonical(colleges.findAll().stream().filter(college -> college.getCode().startsWith("CCDEMO-"))
                .count() == 2);
        requireCanonical(collegeMatches(result.get("CCDEMO-NORTH"), "CareerConnect North Campus", "Northport"));
        requireCanonical(collegeMatches(result.get("CCDEMO-SOUTH"), "CareerConnect South Campus", "Southport"));

        List<Department> demoDepartments = departments.findAll().stream()
                .filter(department -> result.containsValue(department.getCollege())).toList();
        requireCanonical(demoDepartments.size() == result.size() * DEPARTMENTS.length);
        for (College college : result.values()) {
            for (int i = 0; i < DEPARTMENTS.length; i++) {
                String code = "DEMO-" + (i + 1);
                String name = DEPARTMENTS[i];
                requireCanonical(demoDepartments.stream().anyMatch(department ->
                        department.getCollege().getId().equals(college.getId())
                                && department.getCode().equals(code)
                                && department.getName().equals(name)
                                && department.getSeats() == 120));
            }
        }
        return result;
    }

    private boolean collegeMatches(College college, String name, String city) {
        return name.equals(college.getName()) && city.equals(college.getCity())
                && "Demo State".equals(college.getState())
                && "CareerConnect Demo University".equals(college.getUniversity())
                && college.isActive();
    }

    private void verifyOfficerProfiles(Map<String, College> demoColleges, Map<String, User> usersByEmail) {
        List<PlacementOfficerProfile> profiles = officerProfiles.findAll().stream()
                .filter(profile -> usersByEmail.values().stream()
                        .filter(user -> user.getRole() == User.Role.PLACEMENT_OFFICER)
                        .anyMatch(user -> user.getId().equals(profile.getUser().getId())))
                .toList();
        requireCanonical(profiles.size() == 2);
        for (int i = 1; i <= 2; i++) {
            User officer = usersByEmail.get("officer" + i + EMAIL_DOMAIN);
            String collegeCode = i == 1 ? "CCDEMO-NORTH" : "CCDEMO-SOUTH";
            String fullName = "Demo Placement Officer " + i;
            requireCanonical(profiles.stream().anyMatch(profile ->
                    profile.getUser().getId().equals(officer.getId())
                            && profile.getCollege().getId().equals(demoColleges.get(collegeCode).getId())
                            && profile.getFullName().equals(fullName)));
        }
    }

    private Map<Long, Company> verifyCompanies(Map<String, User> usersByEmail) {
        Map<Long, Company> result = new HashMap<>();
        List<Company> ownedCompanies = companies.findAll().stream()
                .filter(company -> usersByEmail.values().stream()
                        .filter(user -> user.getRole() == User.Role.RECRUITER)
                        .anyMatch(user -> user.getId().equals(company.getOwnerUserId())))
                .toList();
        requireCanonical(ownedCompanies.size() == COMPANIES.length);
        for (int i = 0; i < COMPANIES.length; i++) {
            User recruiter = usersByEmail.get("recruiter" + (i + 1) + EMAIL_DOMAIN);
            Company company = ownedCompanies.stream()
                    .filter(candidate -> recruiter.getId().equals(candidate.getOwnerUserId()))
                    .findFirst().orElseThrow(() -> incompleteSeed("A reserved demo company is missing."));
            requireCanonical(company.getName().equals(COMPANIES[i])
                    && company.isVerified()
                    && company.getIndustry().equals(List.of(
                            "Technology", "Analytics", "Cloud Services", "Software", "Design").get(i))
                    && company.getWebsite().equals("https://company" + (i + 1) + ".example.test")
                    && company.getLocation().equals(
                            List.of("Northport", "Southport", "Remote", "Northport", "Southport").get(i))
                    && company.getDescription().equals(
                            "A fictional organization created for the CareerConnect demo."));
            result.put(recruiter.getId(), company);
        }
        return result;
    }

    private Map<String, Job> verifyJobs(Map<String, User> usersByEmail, Map<Long, Company> demoCompanies) {
        Set<Long> recruiterIds = new HashSet<>(demoCompanies.keySet());
        List<Job> demoJobs = jobs.findAll().stream()
                .filter(job -> recruiterIds.contains(job.getRecruiterId())).toList();
        requireCanonical(demoJobs.size() == JOB_TITLES.length);
        Map<String, Job> result = new HashMap<>();
        for (int i = 0; i < JOB_TITLES.length; i++) {
            int ownerIndex = i % 5;
            User recruiter = usersByEmail.get("recruiter" + (ownerIndex + 1) + EMAIL_DOMAIN);
            String title = JOB_TITLES[i];
            Job job = demoJobs.stream().filter(candidate -> candidate.getTitle().equals(title))
                    .findFirst().orElseThrow(() -> incompleteSeed("A reserved demo job is missing."));
            Company company = demoCompanies.get(recruiter.getId());
            boolean internship = i >= JOB_COUNT;
            requireCanonical(Objects.equals(job.getRecruiterId(), recruiter.getId())
                    && job.getCompanyName().equals(company.getName())
                    && Objects.equals(job.getInternship(), internship)
                    && job.isPublished() && !job.isClosed()
                    && job.getDescription().startsWith("Fictional demo opportunity")
                    && Objects.equals(job.getMinCgpa(), internship ? 6.5 : 7.0)
                    && Objects.equals(job.getMaxBacklogs(), 1)
                    && Objects.equals(job.getJobType(), internship ? "INTERNSHIP" : "FULL_TIME")
                    && Objects.equals(job.getLocation(), i % 2 == 0 ? "Northport / Hybrid" : "Remote")
                    && Objects.equals(job.getRequiredSkills(), "Communication, analytical thinking")
                    && Objects.equals(job.getSalaryMin(), internship ? 18000.0 : 65000.0)
                    && Objects.equals(job.getSalaryMax(), internship ? 35000.0 : 110000.0));
            result.put(job.getTitle(), job);
        }
        return result;
    }

    private Map<String, Application> verifyApplications(
            Map<String, User> usersByEmail, Map<String, Job> demoJobs) {
        Set<Long> studentIds = new HashSet<>();
        for (int i = 1; i <= STUDENT_COUNT; i++) {
            User student = usersByEmail.get(studentEmail(i));
            StudentProfile profile = studentProfiles.findByUserId(student.getId())
                    .orElseThrow(() -> incompleteSeed("A reserved demo student profile is missing."));
            int profileIndex = i - 1;
            requireCanonical(profile.getFullName().equals("Demo Student " + String.format("%02d", i))
                    && profile.getRollNumber().equals("CCDEMO-" + String.format("%03d", i))
                    && profile.getDepartment().equals(DEPARTMENTS[profileIndex % DEPARTMENTS.length])
                    && profile.getDepartmentEntity() != null
                    && profile.getDepartmentEntity().getName().equals(profile.getDepartment())
                    && profile.getDepartmentEntity().getCollege().getId().equals(profile.getCollege().getId())
                    && profile.getCollege().getCode().equals(profileIndex % 2 == 0
                            ? "CCDEMO-NORTH" : "CCDEMO-SOUTH")
                    && Objects.equals(profile.getCgpa(), 7.0 + (profileIndex % 16) * 0.1)
                    && Objects.equals(profile.getBacklogs(), profileIndex % 7 == 0 ? 1 : 0));
            studentIds.add(student.getId());
        }

        Map<String, Application> result = new HashMap<>();
        List<Application> demoApplications = applications.findAll().stream()
                .filter(application -> studentIds.contains(application.getStudent().getId())
                        || demoJobs.values().stream().anyMatch(job -> job.getId().equals(application.getJob().getId())))
                .toList();
        requireCanonical(demoApplications.size() == APPLICATION_COUNT);
        for (int i = 0; i < APPLICATION_COUNT; i++) {
            int studentIndex = i / 2;
            int jobIndex = i % 2 == 0 ? studentIndex % demoJobs.size() : (studentIndex + 5) % demoJobs.size();
            User student = usersByEmail.get(studentEmail(studentIndex + 1));
            Job job = demoJobs.get(JOB_TITLES[jobIndex]);
            Application application = demoApplications.stream()
                    .filter(candidate -> candidate.getStudent().getId().equals(student.getId())
                            && candidate.getJob().getId().equals(job.getId()))
                    .findFirst().orElseThrow(() -> incompleteSeed("A reserved demo application is missing."));
            Instant withdrawnAt = i >= 40 && i < 45
                    ? application.getAppliedAt().plus(Duration.ofDays(2)) : null;
            requireCanonical(application.getStatus() == applicationStatus(i)
                    && application.getAppliedAt() != null
                    && Objects.equals(application.getWithdrawnAt(), withdrawnAt)
                    && Objects.equals(application.getRecruiterFeedback(),
                            application.getStatus() == Application.Status.REJECTED
                                    ? "The role requirements did not align closely enough at this time." : null));
            result.put(Integer.toString(i), application);
        }
        return result;
    }

    private void verifyHistories(Map<String, Application> demoApplications, Map<String, User> usersByEmail) {
        List<ApplicationStatusHistory> demoHistories = histories.findAll().stream()
                .filter(history -> demoApplications.values().stream()
                        .anyMatch(application -> application.getId().equals(history.getApplication().getId())))
                .toList();
        requireCanonical(demoHistories.size() == 128);
        for (int i = 0; i < APPLICATION_COUNT; i++) {
            Application application = demoApplications.get(Integer.toString(i));
            User recruiter = recruiterFor(application.getJob(), List.of(
                    usersByEmail.get("recruiter1" + EMAIL_DOMAIN),
                    usersByEmail.get("recruiter2" + EMAIL_DOMAIN),
                    usersByEmail.get("recruiter3" + EMAIL_DOMAIN),
                    usersByEmail.get("recruiter4" + EMAIL_DOMAIN),
                    usersByEmail.get("recruiter5" + EMAIL_DOMAIN)));
            List<ExpectedHistory> expected = expectedHistory(application, recruiter);
            List<ApplicationStatusHistory> actual = demoHistories.stream()
                    .filter(history -> history.getApplication().getId().equals(application.getId()))
                    .sorted(Comparator.comparing(ApplicationStatusHistory::getChangedAt)
                            .thenComparing(ApplicationStatusHistory::getId))
                    .toList();
            requireCanonical(actual.size() == expected.size());
            for (int historyIndex = 0; historyIndex < expected.size(); historyIndex++) {
                ApplicationStatusHistory row = actual.get(historyIndex);
                ExpectedHistory record = expected.get(historyIndex);
                requireCanonical(row.getStatus() == record.status()
                        && row.getChangedBy().equals(record.changedBy())
                        && Objects.equals(row.getNote(), record.note())
                        && row.getChangedAt().equals(record.changedAt()));
            }
        }
    }

    private void verifyInterviewsAndOffers(Map<String, Application> demoApplications) {
        Set<Long> applicationIds = demoApplications.values().stream()
                .map(Application::getId).collect(java.util.stream.Collectors.toSet());
        List<InterviewSchedule> demoInterviews = interviews.findAll().stream()
                .filter(interview -> applicationIds.contains(interview.getApplication().getId())).toList();
        requireCanonical(demoInterviews.size() == 15);
        for (int i = 0; i < 15; i++) {
            Application application = demoApplications.get(Integer.toString(i));
            InterviewSchedule interview = demoInterviews.stream()
                    .filter(candidate -> candidate.getApplication().getId().equals(application.getId()))
                    .findFirst().orElseThrow(() -> incompleteSeed("A reserved demo interview is missing."));
            boolean completed = i < 8;
            requireCanonical(interview.getStatus() == (completed
                            ? InterviewSchedule.Status.COMPLETED : InterviewSchedule.Status.SCHEDULED)
                    && interview.getScheduledAt() != null
                    && (!completed || interview.getScheduledAt()
                            .equals(application.getAppliedAt().plus(Duration.ofDays(3))))
                    && interview.getMode().equals(i % 2 == 0 ? "ONLINE" : "IN_PERSON")
                    && interview.getLocationOrLink().equals(i % 2 == 0
                            ? "https://meet.example.test/demo" : "North Campus, Room 12")
                    && interview.getNotes().equals("Demo interview for " + application.getJob().getTitle())
                    && Objects.equals(interview.getFeedback(),
                            completed ? "Interview completed; feedback recorded." : null)
                    && interview.getCreatedAt().equals(interview.getScheduledAt().minus(Duration.ofDays(2))));
        }

        List<Offer> demoOffers = offers.findAll().stream()
                .filter(offer -> applicationIds.contains(offer.getApplication().getId())).toList();
        requireCanonical(demoOffers.size() == 8);
        for (int i = 0; i < 8; i++) {
            Application application = demoApplications.get(Integer.toString(i));
            Offer offer = demoOffers.stream()
                    .filter(candidate -> candidate.getApplication().getId().equals(application.getId()))
                    .findFirst().orElseThrow(() -> incompleteSeed("A reserved demo offer is missing."));
            Instant createdAt = application.getAppliedAt().plus(Duration.ofDays(5));
            requireCanonical(application.getStatus() == Application.Status.OFFERED
                    && offer.getStatus() == Offer.Status.values()[i % Offer.Status.values().length]
                    && offer.getPackageDetails().equals("Demo compensation package")
                    && Objects.equals(offer.getPackageAmount(), 85000.0 + i * 2500.0)
                    && offer.getJoiningDate() != null
                    && offer.getCreatedAt().equals(createdAt)
                    && offer.getUpdatedAt().equals(createdAt.plus(Duration.ofDays(1))));
        }
    }

    private void verifyPlacementData(
            Map<String, College> demoColleges,
            Map<String, User> usersByEmail,
            Map<String, Application> demoApplications) {
        List<PlacementDrive> demoDrives = drives.findAll().stream()
                .filter(drive -> drive.getTitle().startsWith("CAREERCONNECT_DEMO_CAMPUS_DRIVE_")).toList();
        requireCanonical(demoDrives.size() == 2);
        List<DriveRegistration> demoRegistrations = registrations.findAll().stream()
                .filter(registration -> demoDrives.stream()
                        .anyMatch(drive -> drive.getId().equals(registration.getDrive().getId())))
                .toList();
        requireCanonical(demoRegistrations.size() == 20);
        List<PlacementSelection> demoSelections = selections.findAll().stream()
                .filter(selection -> demoDrives.stream()
                        .anyMatch(drive -> drive.getId().equals(selection.getDrive().getId())))
                .toList();
        requireCanonical(demoSelections.size() == 4);

        Set<Long> selectedStudents = new HashSet<>();
        for (int driveIndex = 0; driveIndex < 2; driveIndex++) {
            int currentDriveIndex = driveIndex;
            String driveTitle = "CAREERCONNECT_DEMO_CAMPUS_DRIVE_" + (driveIndex + 1);
            PlacementDrive drive = demoDrives.stream().filter(candidate -> candidate.getTitle().equals(driveTitle))
                    .findFirst().orElseThrow(() -> incompleteSeed("A reserved demo placement drive is missing."));
            College college = demoColleges.get(driveIndex == 0 ? "CCDEMO-NORTH" : "CCDEMO-SOUTH");
            String expectedJobTitle = JOB_TITLES[driveIndex];
            Job driveJob = jobs.findAll().stream()
                    .filter(job -> job.getTitle().equals(expectedJobTitle))
                    .findFirst().orElseThrow(() -> incompleteSeed("A reserved demo drive job is missing."));
            User officer = usersByEmail.get("officer" + (driveIndex + 1) + EMAIL_DOMAIN);
            requireCanonical(drive.getCollege().getId().equals(college.getId())
                    && drive.getCompanyName().equals(driveJob.getCompanyName())
                    && drive.getJobTitle().equals(driveJob.getTitle())
                    && drive.getDriveType().equals("FULL_TIME")
                    && drive.getStatus() == PlacementDrive.DriveStatus.OPEN
                    && Objects.equals(drive.getCreatedByOfficerId(), officer.getId())
                    && Objects.equals(drive.getMinCgpa(), 7.5)
                    && Objects.equals(drive.getMaxBacklogs(), 1)
                    && drive.getRegistrationDeadline() != null && drive.getDriveDate() != null);

            List<User> cohort = new ArrayList<>();
            for (int i = 1; i <= STUDENT_COUNT; i++) {
                User student = usersByEmail.get(studentEmail(i));
                StudentProfile profile = studentProfiles.findByUserId(student.getId()).orElseThrow();
                if (profile.getCollege().getId().equals(college.getId()) && cohort.size() < 10) {
                    cohort.add(student);
                }
            }
            int selectedCount = 0;
            for (User student : cohort) {
                StudentProfile profile = studentProfiles.findByUserId(student.getId()).orElseThrow();
                boolean eligible = profile.getCgpa() >= drive.getMinCgpa()
                        && profile.getBacklogs() <= drive.getMaxBacklogs();
                boolean selected = eligible && selectedCount < 2;
                if (selected) selectedCount++;
                DriveRegistration registration = demoRegistrations.stream()
                        .filter(candidate -> candidate.getDrive().getId().equals(drive.getId())
                                && candidate.getStudent().getId().equals(student.getId()))
                        .findFirst().orElseThrow(() -> incompleteSeed("A reserved drive registration is missing."));
                requireCanonical(registration.getStatus() == (selected
                                ? DriveRegistration.RegistrationStatus.SELECTED
                                : eligible ? DriveRegistration.RegistrationStatus.ELIGIBLE
                                        : DriveRegistration.RegistrationStatus.NOT_ELIGIBLE)
                        && registration.getRegisteredAt() != null
                        && Objects.equals(registration.getOfferedPackage(),
                                selected ? 90000.0 + cohort.indexOf(student) * 5000.0 : null)
                        && Objects.equals(registration.getFeedback(),
                                selected ? "Selected for the demo placement opportunity." : null));
                List<PlacementSelection> matchingSelections = demoSelections.stream()
                        .filter(selection -> selection.getDrive().getId().equals(drive.getId())
                                && selection.getStudent().getId().equals(student.getId())).toList();
                requireCanonical(matchingSelections.size() == (selected ? 1 : 0));
                if (selected) {
                    PlacementSelection selection = matchingSelections.get(0);
                    requireCanonical(selection.getJobTitle().equals(drive.getJobTitle())
                            && Objects.equals(selection.getPackageAmount(), registration.getOfferedPackage())
                            && selection.getSelectedAt().equals(registration.getRegisteredAt()
                                    .plus(Duration.ofDays(2))));
                    selectedStudents.add(student.getId());
                }
            }
        }
        Set<Long> demoApplicationIds = demoApplications.values().stream()
                .map(Application::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> offeredStudents = offers.findAll().stream()
                .filter(offer -> demoApplicationIds.contains(offer.getApplication().getId()))
                .map(offer -> offer.getApplication().getStudent().getId())
                .collect(java.util.stream.Collectors.toSet());
        requireCanonical(java.util.Collections.disjoint(selectedStudents, offeredStudents));
    }

    private void verifyNotifications(Map<String, Application> demoApplications, Map<String, User> usersByEmail) {
        Map<NotificationKey, Integer> expected = new HashMap<>();
        for (int i = 0; i < APPLICATION_COUNT; i++) {
            Application application = demoApplications.get(Integer.toString(i));
            addExpectedNotification(expected, application.getStudent(), "Application update",
                    application.getJob().getTitle() + ": your application is "
                            + (application.getWithdrawnAt() == null
                                    ? application.getStatus().name() : "WITHDRAWN") + ".",
                    "APPLICATION_STATUS");
        }
        List<InterviewSchedule> demoInterviews = interviews.findAll();
        List<Notification> allNotifications = notifications.findAll();
        for (int i = 0; i < 15; i++) {
            Application application = demoApplications.get(Integer.toString(i));
            InterviewSchedule interview = demoInterviews.stream()
                    .filter(candidate -> candidate.getApplication().getId().equals(application.getId()))
                    .findFirst().orElseThrow();
            boolean completed = i < 8;
            String message = completed
                    ? application.getJob().getTitle() + " interview was completed."
                    : scheduledInterviewNotificationMessage(application, interview, allNotifications);
            addExpectedNotification(expected, application.getStudent(),
                    completed ? "Interview completed" : "Interview scheduled",
                    message,
                    "INTERVIEW");
        }
        List<Offer> demoOffers = offers.findAll();
        for (int i = 0; i < 8; i++) {
            Application application = demoApplications.get(Integer.toString(i));
            Offer offer = demoOffers.stream()
                    .filter(candidate -> candidate.getApplication().getId().equals(application.getId()))
                    .findFirst().orElseThrow();
            Offer.Status status = Offer.Status.values()[i % Offer.Status.values().length];
            addExpectedNotification(expected, application.getStudent(),
                    status == Offer.Status.WITHDRAWN ? "Offer withdrawn" : "Offer update",
                    application.getJob().getTitle() + " at " + application.getJob().getCompanyName()
                            + ": demo offer status is " + status.name() + ".",
                    "OFFER");
        }
        for (PlacementSelection selection : selections.findAll()) {
            if (selection.getDrive().getTitle().startsWith("CAREERCONNECT_DEMO_CAMPUS_DRIVE_")) {
                addExpectedNotification(expected, selection.getStudent(), "Placement selection recorded",
                        "You were selected for " + selection.getJobTitle() + " at "
                                + selection.getDrive().getCompanyName() + ".",
                        "PLACEMENT_SELECTION");
            }
        }
        Set<Long> demoUserIds = usersByEmail.values().stream()
                .map(User::getId).collect(java.util.stream.Collectors.toSet());
        Map<NotificationKey, Integer> actual = new HashMap<>();
        notifications.findAll().stream()
                .filter(notification -> demoUserIds.contains(notification.getUser().getId()))
                .forEach(notification -> actual.merge(new NotificationKey(notification.getUser().getId(),
                        notification.getTitle(), notification.getMessage(), notification.getType()), 1, Integer::sum));
        requireCanonical(actual.equals(expected));
    }

    private String scheduledInterviewNotificationMessage(
            Application application, InterviewSchedule interview, List<Notification> allNotifications) {
        String title = "Interview scheduled";
        String prefix = application.getJob().getTitle() + " interview is scheduled for ";
        List<Notification> matching = allNotifications.stream()
                .filter(notification -> notification.getUser().getId().equals(application.getStudent().getId())
                        && notification.getTitle().equals(title)
                        && "INTERVIEW".equals(notification.getType())
                        && notification.getMessage().startsWith(prefix))
                .toList();
        requireCanonical(matching.size() == 1);
        String message = matching.get(0).getMessage();
        String timestamp = message.substring(prefix.length(), message.length() - 1);
        Instant notifiedAt = Instant.parse(timestamp);
        requireCanonical(message.equals(prefix + notifiedAt + "."));
        requireCanonical(Duration.between(notifiedAt, interview.getScheduledAt()).abs()
                .compareTo(Duration.ofNanos(1_000)) <= 0);
        return message;
    }

    private List<ExpectedHistory> expectedHistory(Application application, User recruiter) {
        int index = Integer.parseInt(applicationIndex(application));
        Instant appliedAt = application.getAppliedAt();
        List<ExpectedHistory> result = new ArrayList<>();
        result.add(new ExpectedHistory(Application.Status.APPLIED, application.getStudent().getEmail(),
                appliedAt, "Application submitted"));
        Application.Status status = applicationStatus(index);
        if (status == Application.Status.SHORTLISTED || status == Application.Status.INTERVIEW_SCHEDULED
                || status == Application.Status.OFFERED) {
            result.add(new ExpectedHistory(Application.Status.SHORTLISTED, recruiter.getEmail(),
                    appliedAt.plus(Duration.ofDays(1)), "Profile meets the role's initial criteria."));
        }
        if (status == Application.Status.INTERVIEW_SCHEDULED || status == Application.Status.OFFERED) {
            result.add(new ExpectedHistory(Application.Status.INTERVIEW_SCHEDULED, recruiter.getEmail(),
                    appliedAt.plus(Duration.ofDays(2)), "Interview scheduled"));
        }
        if (status == Application.Status.OFFERED) {
            result.add(new ExpectedHistory(Application.Status.OFFERED, recruiter.getEmail(),
                    appliedAt.plus(Duration.ofDays(4)), "Offer issued"));
        } else if (status == Application.Status.REJECTED) {
            result.add(new ExpectedHistory(Application.Status.REJECTED, recruiter.getEmail(),
                    appliedAt.plus(Duration.ofDays(3)),
                    "The experience requirements do not align closely enough for this role."));
        }
        if (application.getWithdrawnAt() != null) {
            result.add(new ExpectedHistory(Application.Status.APPLIED, application.getStudent().getEmail(),
                    application.getWithdrawnAt(), WITHDRAWAL_NOTE));
        }
        return result;
    }

    private String applicationIndex(Application target) {
        String email = target.getStudent().getEmail();
        int studentNumber = Integer.parseInt(email.substring("student".length(), email.indexOf(EMAIL_DOMAIN)));
        int studentIndex = studentNumber - 1;
        int jobIndex = List.of(JOB_TITLES).indexOf(target.getJob().getTitle());
        if (jobIndex == studentIndex % JOB_TITLES.length) {
            return Integer.toString(studentIndex * 2);
        }
        if (jobIndex == (studentIndex + 5) % JOB_TITLES.length) {
            return Integer.toString(studentIndex * 2 + 1);
        }
        throw incompleteSeed("A demo application has no canonical index.");
    }

    private void addExpectedNotification(
            Map<NotificationKey, Integer> expected, User recipient, String title, String message, String type) {
        expected.merge(new NotificationKey(recipient.getId(), title, message, type), 1, Integer::sum);
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()
                || password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            throw new IllegalStateException(
                    "Demo seeding requires a password between 12 and 72 characters from the ignored local .env file or environment.");
        }
    }

    private static IllegalStateException incompleteSeed(String reason) {
        return new IllegalStateException("Reserved demo seed marker exists, but its dataset is incomplete. " + reason);
    }

    private static void requireCanonical(boolean valid) {
        if (!valid) {
            throw incompleteSeed("A reserved demo record or relationship does not match the canonical dataset.");
        }
    }

    private record ExpectedHistory(
            Application.Status status, String changedBy, Instant changedAt, String note) {}

    private record NotificationKey(Long userId, String title, String message, String type) {}

    private College[] createColleges() {
        College north = colleges.save(College.builder().code("CCDEMO-NORTH")
                .name("CareerConnect North Campus").city("Northport").state("Demo State")
                .university("CareerConnect Demo University").active(true).build());
        College south = colleges.save(College.builder().code("CCDEMO-SOUTH")
                .name("CareerConnect South Campus").city("Southport").state("Demo State")
                .university("CareerConnect Demo University").active(true).build());
        return new College[] {north, south};
    }

    private List<Department> createDepartments(College[] demoColleges) {
        List<Department> result = new ArrayList<>();
        for (College college : demoColleges) {
            for (int i = 0; i < DEPARTMENTS.length; i++) {
                result.add(departments.save(Department.builder().college(college)
                        .code("DEMO-" + (i + 1)).name(DEPARTMENTS[i]).seats(120).build()));
            }
        }
        return result;
    }

    private List<User> createRecruiters() {
        List<User> result = new ArrayList<>();
        for (int i = 1; i <= 5; i++) result.add(createUser("recruiter" + i + EMAIL_DOMAIN, User.Role.RECRUITER));
        return result;
    }

    private List<User> createOfficers(College[] demoColleges) {
        List<User> result = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            User officer = createUser("officer" + i + EMAIL_DOMAIN, User.Role.PLACEMENT_OFFICER);
            officerProfiles.save(PlacementOfficerProfile.builder().user(officer)
                    .college(demoColleges[i - 1]).fullName("Demo Placement Officer " + i).build());
            result.add(officer);
        }
        return result;
    }

    private List<User> createStudents(College[] demoColleges, List<Department> demoDepartments) {
        List<User> result = new ArrayList<>();
        for (int i = 0; i < STUDENT_COUNT; i++) {
            int collegeIndex = i % demoColleges.length;
            User student = createUser(studentEmail(i + 1), User.Role.STUDENT);
            double cgpa = 7.0 + (i % 16) * 0.1;
            int backlogs = i % 7 == 0 ? 1 : 0;
            studentProfiles.save(StudentProfile.builder().user(student)
                    .fullName("Demo Student " + String.format("%02d", i + 1))
                    .rollNumber("CCDEMO-" + String.format("%03d", i + 1))
                    .department(DEPARTMENTS[i % DEPARTMENTS.length]).cgpa(cgpa).backlogs(backlogs)
                    .graduationYear(LocalDate.now().getYear() + 1).currentSemester(7)
                    .college(demoColleges[collegeIndex])
                    .departmentEntity(demoDepartments.get(collegeIndex * DEPARTMENTS.length
                            + i % DEPARTMENTS.length))
                    .skills("Java, SQL, problem solving").build());
            result.add(student);
        }
        return result;
    }

    private List<Company> createCompanies(List<User> recruiters) {
        List<Company> result = new ArrayList<>();
        for (int i = 0; i < recruiters.size(); i++) {
            result.add(companies.save(Company.builder().name(COMPANIES[i])
                    .industry(List.of("Technology", "Analytics", "Cloud Services", "Software", "Design").get(i))
                    .website("https://company" + (i + 1) + ".example.test")
                    .location(List.of("Northport", "Southport", "Remote", "Northport", "Southport").get(i))
                    .description("A fictional organization created for the CareerConnect demo.")
                    .verified(true).ownerUserId(recruiters.get(i).getId()).build()));
        }
        return result;
    }

    private List<Job> createJobs(List<User> recruiters, List<Company> demoCompanies) {
        List<Job> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < JOB_TITLES.length; i++) {
            int ownerIndex = i % recruiters.size();
            boolean internship = i >= JOB_COUNT;
            result.add(jobs.save(Job.builder().title(JOB_TITLES[i])
                    .description("Fictional demo opportunity focused on collaborative, practical work.")
                    .companyName(demoCompanies.get(ownerIndex).getName())
                    .minCgpa(internship ? 6.5 : 7.0).maxBacklogs(1)
                    .deadline(today.plusDays(60 + i)).recruiterId(recruiters.get(ownerIndex).getId())
                    .location(i % 2 == 0 ? "Northport / Hybrid" : "Remote")
                    .jobType(internship ? "INTERNSHIP" : "FULL_TIME")
                    .requiredSkills("Communication, analytical thinking")
                    .salaryMin(internship ? 18000.0 : 65000.0)
                    .salaryMax(internship ? 35000.0 : 110000.0)
                    .internship(internship).published(true).closed(false).build()));
        }
        return result;
    }

    private List<Application> createApplications(List<User> students, List<Job> demoJobs, List<User> recruiters) {
        List<Application> result = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < APPLICATION_COUNT; i++) {
            int studentIndex = i / 2;
            int jobIndex = i % 2 == 0 ? studentIndex % demoJobs.size() : (studentIndex + 5) % demoJobs.size();
            User student = students.get(studentIndex);
            Job job = demoJobs.get(jobIndex);
            Application.Status status = applicationStatus(i);
            Instant appliedAt = now.minus(Duration.ofDays(45L + (i % 20)));
            Instant withdrawnAt = i >= 40 && i < 45 ? appliedAt.plus(Duration.ofDays(2)) : null;
            Application application = applications.save(Application.builder().student(student).job(job)
                    .status(status).appliedAt(appliedAt).withdrawnAt(withdrawnAt)
                    .recruiterFeedback(status == Application.Status.REJECTED
                            ? "The role requirements did not align closely enough at this time." : null)
                    .build());
            result.add(application);
            User recruiter = recruiterFor(job, recruiters);
            history(application, Application.Status.APPLIED, student.getEmail(), appliedAt, "Application submitted");
            if (status == Application.Status.SHORTLISTED
                    || status == Application.Status.INTERVIEW_SCHEDULED
                    || status == Application.Status.OFFERED) {
                history(application, Application.Status.SHORTLISTED, recruiter.getEmail(),
                        appliedAt.plus(Duration.ofDays(1)), "Profile meets the role's initial criteria.");
            }
            if (status == Application.Status.INTERVIEW_SCHEDULED || status == Application.Status.OFFERED) {
                history(application, Application.Status.INTERVIEW_SCHEDULED, recruiter.getEmail(),
                        appliedAt.plus(Duration.ofDays(2)), "Interview scheduled");
            }
            if (status == Application.Status.OFFERED) {
                history(application, Application.Status.OFFERED, recruiter.getEmail(),
                        appliedAt.plus(Duration.ofDays(4)), "Offer issued");
            } else if (status == Application.Status.REJECTED) {
                history(application, Application.Status.REJECTED, recruiter.getEmail(),
                        appliedAt.plus(Duration.ofDays(3)),
                        "The experience requirements do not align closely enough for this role.");
            }
            if (withdrawnAt != null) {
                history(application, Application.Status.APPLIED, student.getEmail(),
                        withdrawnAt, WITHDRAWAL_NOTE);
            }
            notifyStudent(student, "Application update", job.getTitle()
                    + ": your application is " + (withdrawnAt == null ? status.name() : "WITHDRAWN") + ".",
                    "APPLICATION_STATUS", appliedAt.plus(Duration.ofDays(5)));
        }
        return result;
    }

    private Application.Status applicationStatus(int index) {
        if (index < 8) return Application.Status.OFFERED;
        if (index < 15) return Application.Status.INTERVIEW_SCHEDULED;
        if (index < 30) return Application.Status.REJECTED;
        if (index < 40) return Application.Status.SHORTLISTED;
        return Application.Status.APPLIED;
    }

    private void createInterviewsAndOffers(List<Application> demoApplications) {
        Instant now = Instant.now();
        for (int i = 0; i < 15; i++) {
            Application application = demoApplications.get(i);
            boolean completed = i < 8;
            Instant scheduledAt = completed
                    ? application.getAppliedAt().plus(Duration.ofDays(3))
                    : now.plus(Duration.ofDays(i - 7L));
            InterviewSchedule interview = interviews.save(InterviewSchedule.builder()
                    .application(application).scheduledAt(scheduledAt).mode(i % 2 == 0 ? "ONLINE" : "IN_PERSON")
                    .locationOrLink(i % 2 == 0 ? "https://meet.example.test/demo" : "North Campus, Room 12")
                    .notes("Demo interview for " + application.getJob().getTitle())
                    .feedback(completed ? "Interview completed; feedback recorded." : null)
                    .createdAt(scheduledAt.minus(Duration.ofDays(2)))
                    .status(completed ? InterviewSchedule.Status.COMPLETED : InterviewSchedule.Status.SCHEDULED)
                    .build());
            notifyStudent(application.getStudent(), completed ? "Interview completed" : "Interview scheduled",
                    application.getJob().getTitle() + " interview "
                            + (completed ? "was completed." : "is scheduled for " + scheduledAt + "."),
                    "INTERVIEW", now.minus(Duration.ofDays(1)).plus(Duration.ofMinutes(i)));
        }

        for (int i = 0; i < 8; i++) {
            Application application = demoApplications.get(i);
            Offer.Status status = Offer.Status.values()[i % Offer.Status.values().length];
            Instant createdAt = application.getAppliedAt().plus(Duration.ofDays(5));
            Offer offer = offers.save(Offer.builder().application(application)
                    .packageDetails("Demo compensation package")
                    .packageAmount(85000.0 + i * 2500.0)
                    .joiningDate(now.plus(Duration.ofDays(45 + i)))
                    .status(status).createdAt(createdAt).updatedAt(createdAt.plus(Duration.ofDays(1)))
                    .build());
            notifyStudent(application.getStudent(), status == Offer.Status.WITHDRAWN
                            ? "Offer withdrawn" : "Offer update",
                    application.getJob().getTitle() + " at " + application.getJob().getCompanyName()
                            + ": demo offer status is " + status.name() + ".",
                    "OFFER", offer.getUpdatedAt());
        }
    }

    private void createPlacementData(College[] demoColleges, List<Job> demoJobs,
            List<User> officers, List<User> students) {
        LocalDate today = LocalDate.now();
        PlacementDrive[] demoDrives = new PlacementDrive[2];
        for (int i = 0; i < demoDrives.length; i++) {
            Job job = demoJobs.get(i);
            demoDrives[i] = drives.save(PlacementDrive.builder().college(demoColleges[i])
                    .title("CAREERCONNECT_DEMO_CAMPUS_DRIVE_" + (i + 1))
                    .companyName(job.getCompanyName()).jobTitle(job.getTitle())
                    .driveType("FULL_TIME").description("Fictional campus recruitment drive.")
                    .minCgpa(7.5).maxBacklogs(1).registrationDeadline(today.plusDays(20 + i))
                    .driveDate(today.plusDays(35 + i)).status(PlacementDrive.DriveStatus.OPEN)
                    .createdByOfficerId(officers.get(i).getId()).build());
        }

        for (int driveIndex = 0; driveIndex < demoDrives.length; driveIndex++) {
            PlacementDrive drive = demoDrives[driveIndex];
            List<User> driveStudents = new ArrayList<>();
            for (User student : students) {
                StudentProfile profile = studentProfiles.findByUserId(student.getId()).orElseThrow();
                if (profile.getCollege().getId().equals(drive.getCollege().getId())
                        && driveStudents.size() < 10) {
                    driveStudents.add(student);
                }
            }
            if (driveStudents.size() < 10) {
                throw new IllegalStateException("Demo drive requires ten registered students.");
            }
            int selectedCount = 0;
            for (int i = 0; i < driveStudents.size(); i++) {
                User student = driveStudents.get(i);
                StudentProfile profile = studentProfiles.findByUserId(student.getId()).orElseThrow();
                boolean eligible = profile.getCgpa() >= drive.getMinCgpa()
                        && profile.getBacklogs() <= drive.getMaxBacklogs();
                boolean selected = eligible && selectedCount < 2;
                if (selected) selectedCount++;
                DriveRegistration registration = registrations.save(DriveRegistration.builder()
                        .drive(drive).student(student)
                        .status(eligible ? DriveRegistration.RegistrationStatus.ELIGIBLE
                                : DriveRegistration.RegistrationStatus.NOT_ELIGIBLE)
                        .registeredAt(Instant.now().minus(Duration.ofDays(12 - i)))
                        .offeredPackage(selected ? 90000.0 + i * 5000.0 : null)
                        .feedback(selected ? "Selected for the demo placement opportunity." : null)
                        .build());
                if (selected) {
                    registration.setStatus(DriveRegistration.RegistrationStatus.SELECTED);
                    registrations.save(registration);
                    Instant selectedAt = registration.getRegisteredAt().plus(Duration.ofDays(2));
                    selections.save(PlacementSelection.builder().drive(drive).student(student)
                            .jobTitle(drive.getJobTitle()).packageAmount(registration.getOfferedPackage())
                            .selectedAt(selectedAt).build());
                    notifyStudent(student, "Placement selection recorded",
                            "You were selected for " + drive.getJobTitle() + " at "
                                    + drive.getCompanyName() + ".",
                            "PLACEMENT_SELECTION", selectedAt);
                }
            }
        }
    }

    private User createUser(String email, User.Role role) {
        return users.save(User.builder().email(email).password(passwordEncoder.encode(rawPassword))
                .role(role).enabled(true).emailVerified(true).build());
    }

    private User recruiterFor(Job job, List<User> recruiters) {
        return recruiters.stream().filter(recruiter -> recruiter.getId().equals(job.getRecruiterId()))
                .findFirst().orElseThrow();
    }

    private void history(Application application, Application.Status status,
            String changedBy, Instant changedAt, String note) {
        histories.save(ApplicationStatusHistory.builder().application(application).status(status)
                .changedBy(changedBy).changedAt(changedAt).note(note).build());
    }

    private void notifyStudent(User student, String title, String message, String type, Instant createdAt) {
        notifications.save(Notification.builder().user(student).title(title).message(message)
                .type(type).readFlag(false).createdAt(createdAt).build());
    }

    private static String studentEmail(int number) {
        return "student" + number + EMAIL_DOMAIN;
    }
}
