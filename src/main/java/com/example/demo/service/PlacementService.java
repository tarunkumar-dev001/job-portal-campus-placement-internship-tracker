package com.example.demo.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.dto.placement.*;
import com.example.demo.entity.*;
import com.example.demo.entity.Application.Status;
import com.example.demo.entity.DriveRegistration.RegistrationStatus;
import com.example.demo.entity.PlacementDrive.DriveStatus;
import com.example.demo.repository.*;

@Service
public class PlacementService {
    private final CollegeRepository colleges;
    private final DepartmentRepository departments;
    private final PlacementOfficerProfileRepository officerProfiles;
    private final PlacementDriveRepository drives;
    private final DriveRegistrationRepository registrations;
    private final PlacementSelectionRepository selections;
    private final ApplicationStatusHistoryRepository applicationHistory;
    private final ApplicationRepository applications;
    private final StudentProfileRepository students;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final NotificationRepository notifications;

    public PlacementService(CollegeRepository colleges, DepartmentRepository departments,
            PlacementOfficerProfileRepository officerProfiles, PlacementDriveRepository drives,
            DriveRegistrationRepository registrations, PlacementSelectionRepository selections,
            ApplicationStatusHistoryRepository applicationHistory, ApplicationRepository applications,
            StudentProfileRepository students, UserRepository users, PasswordEncoder passwordEncoder,
            NotificationRepository notifications) {
        this.colleges = colleges; this.departments = departments; this.officerProfiles = officerProfiles;
        this.drives = drives; this.registrations = registrations; this.selections = selections;
        this.applicationHistory = applicationHistory; this.applications = applications;
        this.students = students; this.users = users; this.passwordEncoder = passwordEncoder;
        this.notifications = notifications;
    }

    public CollegeResponse createCollege(String email, CollegeRequest request) {
        College college = College.builder().code(request.code().trim().toUpperCase())
                .name(request.name().trim()).city(request.city()).state(request.state())
                .university(request.university()).active(request.active() == null || request.active()).build();
        if (colleges.findByCodeIgnoreCase(college.getCode()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "College code already exists");
        return toCollege(colleges.save(college));
    }

    @Transactional(readOnly = true)
    public List<CollegeResponse> colleges(String email) {
        return List.of(toCollege(assignedCollege(officer(email))));
    }

    @Transactional(readOnly = true)
    public CollegeResponse college(String email, Long id) {
        return toCollege(ownedCollege(email, id));
    }

    @Transactional
    public DepartmentResponse createDepartment(String email, Long collegeId, DepartmentRequest request) {
        College college = ownedCollege(email, collegeId);
        Department department = Department.builder().college(college).code(request.code().trim().toUpperCase())
                .name(request.name().trim()).seats(request.seats()).build();
        return toDepartment(departments.save(department));
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> departments(String email, Long collegeId) {
        ownedCollege(email, collegeId);
        return departments.findByCollegeId(collegeId).stream().map(this::toDepartment).toList();
    }

    @Transactional
    public DriveResponse createDrive(String email, DriveRequest request) {
        PlacementOfficerProfile officer = officer(email);
        College college = assignedCollege(officer);
        PlacementDrive drive = PlacementDrive.builder().college(college).title(request.title().trim())
                .companyName(request.companyName()).jobTitle(request.jobTitle()).driveType(request.driveType())
                .description(request.description()).minCgpa(request.minCgpa()).maxBacklogs(request.maxBacklogs())
                .registrationDeadline(request.registrationDeadline()).driveDate(request.driveDate())
                .createdByOfficerId(officer.getUser().getId()).status(DriveStatus.DRAFT).build();
        return toDrive(drives.save(drive));
    }

    @Transactional(readOnly = true)
    public List<DriveResponse> drives(String email) {
        return drives.findByCollegeIdOrderByRegistrationDeadlineDesc(assignedCollege(officer(email)).getId())
                .stream().map(this::toDrive).toList();
    }

    @Transactional
    public DriveResponse updateDriveStatus(String email, Long driveId, DriveStatus status) {
        PlacementDrive drive = ownedDrive(email, driveId);
        drive.setStatus(status);
        return toDrive(drives.save(drive));
    }

    @Transactional(readOnly = true)
    public List<DriveRegistrationResponse> registrations(String email, Long driveId) {
        ownedDrive(email, driveId);
        return registrations.findByDriveId(driveId).stream().map(this::toRegistration).toList();
    }

    @Transactional
    public DriveRegistrationResponse verifyEligibility(String email, Long driveId, Long studentId) {
        PlacementDrive drive = ownedDrive(email, driveId);
        DriveRegistration registration = registrations.findByDriveIdAndStudentId(driveId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registration not found"));
        StudentProfile profile = students.findByUserId(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student profile not found"));
        boolean eligible = (drive.getMinCgpa() == null || profile.getCgpa() >= drive.getMinCgpa())
                && (drive.getMaxBacklogs() == null || profile.getBacklogs() <= drive.getMaxBacklogs());
        registration.setStatus(eligible ? RegistrationStatus.ELIGIBLE : RegistrationStatus.NOT_ELIGIBLE);
        return toRegistration(registrations.save(registration));
    }

    @Transactional
    public SelectionResponse selectStudent(String email, Long driveId, SelectionRequest request) {
        PlacementDrive drive = ownedDrive(email, driveId);
        User student = users.findById(request.studentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        if (student.getRole() != User.Role.STUDENT)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only students can be selected");
        DriveRegistration registration = registrations.findByDriveIdAndStudentId(driveId, student.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Student is not registered"));
        if (registration.getStatus() != RegistrationStatus.ELIGIBLE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Student must be eligible before selection");
        PlacementSelection selection = PlacementSelection.builder().drive(drive).student(student)
                .jobTitle(request.jobTitle()).packageAmount(request.packageAmount()).selectedAt(Instant.now()).build();
        registration.setStatus(RegistrationStatus.SELECTED);
        registrations.save(registration);
        PlacementSelection saved = selections.save(selection);
        String companyName = drive.getCompanyName() == null || drive.getCompanyName().isBlank()
                ? drive.getTitle() : drive.getCompanyName();
        String selectedJobTitle = saved.getJobTitle() == null || saved.getJobTitle().isBlank()
                ? (drive.getJobTitle() == null || drive.getJobTitle().isBlank()
                        ? drive.getTitle() : drive.getJobTitle())
                : saved.getJobTitle();
        notifications.save(Notification.builder().user(student).title("Placement selection recorded")
                .message("You were selected for " + selectedJobTitle + " at " + companyName + ".")
                .type("PLACEMENT_SELECTION").readFlag(false).createdAt(saved.getSelectedAt()).build());
        return new SelectionResponse(saved.getId(), drive.getId(), student.getId(), student.getEmail(),
                saved.getJobTitle(), saved.getPackageAmount(), saved.getSelectedAt());
    }

    @Transactional
    public StudentImportResponse importStudents(String email, MultipartFile file) {
        College college = assignedCollege(officer(email));
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        if (file == null || file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file is required");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file is empty");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",", -1);
                if (values.length < 7) { skipped++; errors.add("Invalid row: " + line); continue; }
                String emailAddress = values[0].trim().toLowerCase();
                if (users.findByEmail(emailAddress).isPresent()) { skipped++; continue; }
                try {
                    User user = users.save(User.builder().email(emailAddress)
                            .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .role(User.Role.STUDENT).build());
                    StudentProfile profile = StudentProfile.builder().user(user).fullName(values[1].trim())
                            .rollNumber(values[2].trim()).department(values[3].trim())
                            .cgpa(Double.parseDouble(values[4])).backlogs(Integer.parseInt(values[5]))
                            .college(college).resumeUrl(values.length > 6 ? blankToNull(values[6]) : null).build();
                    students.save(profile);
                    imported++;
                } catch (RuntimeException exception) {
                    skipped++;
                    errors.add(emailAddress + ": " + exception.getMessage());
                }
            }
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to import CSV: " + exception.getMessage());
        }
        return new StudentImportResponse(imported, skipped, errors);
    }

    @Transactional(readOnly = true)
    public PlacementStatsResponse stats(String email) {
        College college = assignedCollege(officer(email));
        long active = drives.countByCollegeIdAndStatus(college.getId(), DriveStatus.OPEN);
        long registered = registrations.countByCollegeId(college.getId());
        Map<String, Long> selectionsByDepartment = DashboardStatsMapper.countMap(
                selections.countSelectionsByDepartment(college.getId()));
        long selectedStudents = selections.countByDriveCollegeId(college.getId());
        long totalStudents = students.countByCollegeId(college.getId());
        long uniquePlaced = selections.countDistinctCollegeStudentsPlaced(college.getId());
        long unplaced = Math.max(0, totalStudents - uniquePlaced);
        Map<String, Long> rejectionReasons = DashboardStatsMapper.countMap(
                applicationHistory.countRejectionReasonsByCollege(college.getId(), Status.REJECTED));
        return new PlacementStatsResponse(active, registered, selectedStudents, selectionsByDepartment,
                totalStudents, DashboardStatsMapper.countMap(students.countStudentsByDepartment(college.getId())),
                drives.countByCollegeId(college.getId()), applications.countByStudentCollegeId(college.getId()),
                registrations.countByCollegeId(college.getId()),
                applicationHistory.countDistinctShortlistedStudentsByCollege(college.getId(), Status.SHORTLISTED),
                uniquePlaced, unplaced, DashboardStatsMapper.percentage(uniquePlaced, totalStudents),
                applications.countByStudentCollegeIdAndStatus(college.getId(), Status.REJECTED),
                rejectionReasons, DashboardStatsMapper.completeCounts(Status.class,
                        applications.countByStatusForCollege(college.getId())));
    }

    private PlacementOfficerProfile officer(String email) {
        User user = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return officerProfiles.findByUserId(user.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Placement officer profile not found"));
    }
    private College assignedCollege(PlacementOfficerProfile officer) {
        if (officer.getCollege() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "No college assigned");
        return officer.getCollege();
    }
    private College ownedCollege(String email, Long collegeId) {
        College college = assignedCollege(officer(email));
        if (!Objects.equals(college.getId(), collegeId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "College belongs to another placement officer");
        return college;
    }
    private PlacementDrive ownedDrive(String email, Long driveId) {
        PlacementDrive drive = drives.findById(driveId).orElseThrow(() -> notFound("Placement drive"));
        if (!Objects.equals(drive.getCollege().getId(), assignedCollege(officer(email)).getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Placement drive belongs to another college");
        return drive;
    }
    private ResponseStatusException notFound(String name) { return new ResponseStatusException(HttpStatus.NOT_FOUND, name + " not found"); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private DriveRegistrationResponse toRegistration(DriveRegistration registration) {
        StudentProfile profile = students.findByUserId(registration.getStudent().getId())
                .orElseThrow(() -> notFound("Student profile"));
        return new DriveRegistrationResponse(registration.getId(), registration.getStudent().getId(),
                registration.getStudent().getEmail(), profile.getFullName(), profile.getDepartment(),
                profile.getCgpa(), profile.getBacklogs(), registration.getStatus().name(),
                registration.getRegisteredAt(), registration.getOfferedPackage());
    }
    private CollegeResponse toCollege(College c) { return new CollegeResponse(c.getId(), c.getCode(), c.getName(), c.getCity(), c.getState(), c.getUniversity(), c.isActive()); }
    private DepartmentResponse toDepartment(Department d) { return new DepartmentResponse(d.getId(), d.getCollege().getId(), d.getCode(), d.getName(), d.getSeats()); }
    private DriveResponse toDrive(PlacementDrive d) { return new DriveResponse(d.getId(), d.getCollege().getId(), d.getCollege().getName(), d.getTitle(),
            d.getCompanyName(), d.getJobTitle(), d.getDriveType(), d.getDescription(), d.getMinCgpa(), d.getMaxBacklogs(),
            d.getRegistrationDeadline(), d.getDriveDate(), d.getStatus(), registrations.countByDriveId(d.getId()), selections.countByDriveId(d.getId())); }
}
