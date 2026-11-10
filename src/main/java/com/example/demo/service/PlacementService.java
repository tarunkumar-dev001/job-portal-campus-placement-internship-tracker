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
    private final StudentProfileRepository students;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public PlacementService(CollegeRepository colleges, DepartmentRepository departments,
            PlacementOfficerProfileRepository officerProfiles, PlacementDriveRepository drives,
            DriveRegistrationRepository registrations, PlacementSelectionRepository selections,
            StudentProfileRepository students, UserRepository users, PasswordEncoder passwordEncoder) {
        this.colleges = colleges; this.departments = departments; this.officerProfiles = officerProfiles;
        this.drives = drives; this.registrations = registrations; this.selections = selections;
        this.students = students; this.users = users; this.passwordEncoder = passwordEncoder;
    }

    public CollegeResponse createCollege(String email, CollegeRequest request) {
        College college = College.builder().code(request.code().trim().toUpperCase())
                .name(request.name().trim()).city(request.city()).state(request.state())
                .university(request.university()).active(request.active() == null || request.active()).build();
        if (colleges.findByCodeIgnoreCase(college.getCode()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "College code already exists");
        return toCollege(colleges.save(college));
    }

    public List<CollegeResponse> colleges() {
        return colleges.findAll().stream().map(this::toCollege).toList();
    }

    public CollegeResponse college(Long id) {
        return toCollege(colleges.findById(id).orElseThrow(() -> notFound("College")));
    }

    public DepartmentResponse createDepartment(Long collegeId, DepartmentRequest request) {
        College college = collegeEntity(collegeId);
        Department department = Department.builder().college(college).code(request.code().trim().toUpperCase())
                .name(request.name().trim()).seats(request.seats()).build();
        return toDepartment(departments.save(department));
    }

    public List<DepartmentResponse> departments(Long collegeId) {
        collegeEntity(collegeId);
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

    public PlacementStatsResponse stats(String email) {
        College college = assignedCollege(officer(email));
        List<PlacementSelection> allSelections = selections.findByDriveCollegeId(college.getId());
        List<PlacementDrive> collegeDrives = drives.findByCollegeIdOrderByRegistrationDeadlineDesc(college.getId());
        long active = collegeDrives.stream().filter(d -> d.getStatus() == DriveStatus.OPEN).count();
        long registered = collegeDrives.stream().mapToLong(d -> registrations.countByDriveId(d.getId())).sum();
        Map<String, Long> byDepartment = new TreeMap<>();
        for (PlacementSelection selection : allSelections) {
            StudentProfile profile = students.findByUserId(selection.getStudent().getId()).orElse(null);
            String department = profile == null ? "Unassigned" : profile.getDepartment();
            byDepartment.merge(department, 1L, Long::sum);
        }
        return new PlacementStatsResponse(active, registered, allSelections.size(), byDepartment);
    }

    private PlacementOfficerProfile officer(String email) {
        User user = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return officerProfiles.findByUserId(user.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Placement officer profile not found"));
    }
    private College assignedCollege(PlacementOfficerProfile officer) {
        if (officer.getCollege() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "No college assigned");
        return officer.getCollege();
    }
    private PlacementDrive ownedDrive(String email, Long driveId) {
        PlacementDrive drive = drives.findById(driveId).orElseThrow(() -> notFound("Placement drive"));
        if (!Objects.equals(drive.getCollege().getId(), assignedCollege(officer(email)).getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Placement drive belongs to another college");
        return drive;
    }
    private College collegeEntity(Long id) { return colleges.findById(id).orElseThrow(() -> notFound("College")); }
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
