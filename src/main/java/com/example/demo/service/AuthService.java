package com.example.demo.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.AuthFlowDtos;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.dto.AuthFlowDtos.EmailRequest;
import com.example.demo.dto.AuthFlowDtos.MessageResponse;
import com.example.demo.entity.StudentProfile;
import com.example.demo.entity.User;
import com.example.demo.entity.User.Role;
import com.example.demo.repository.StudentProfileRepository;
import com.example.demo.repository.PlacementOfficerProfileRepository;
import com.example.demo.entity.PlacementOfficerProfile;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtils;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final PlacementOfficerProfileRepository placementOfficerProfileRepository;
    private final AuthSecurityService securityService;
    private final boolean requireEmailVerification;

    public AuthService(UserRepository userRepository,
            StudentProfileRepository studentProfileRepository,
            PasswordEncoder passwordEncoder, JwtUtils jwtUtils,
            PlacementOfficerProfileRepository placementOfficerProfileRepository,
            AuthSecurityService securityService,
            @Value("${app.security.require-email-verification:true}") boolean requireEmailVerification) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.placementOfficerProfileRepository = placementOfficerProfileRepository;
        this.securityService = securityService;
        this.requireEmailVerification = requireEmailVerification;
    }

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin accounts cannot be self-registered");
        }
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();
        userRepository.save(user);

        if (request.role() == Role.STUDENT) {
            validateStudentProfile(request);
            studentProfileRepository.save(StudentProfile.builder()
                    .user(user)
                    .fullName(request.fullName().trim())
                    .rollNumber(request.rollNumber().trim())
                    .department(request.department().trim())
                    .cgpa(request.cgpa())
                    .backlogs(request.backlogs())
                    .resumeUrl(request.resumeUrl())
                    .build());
        }
        if (request.role() == Role.PLACEMENT_OFFICER) {
            placementOfficerProfileRepository.save(PlacementOfficerProfile.builder()
                    .user(user).fullName(request.fullName()).build());
        }

        securityService.requestVerification(new EmailRequest(user.getEmail()));
        return new MessageResponse("Account created. Email verification is required before sign-in.");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        user = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is deactivated");
        }
        if (requireEmailVerification && !user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email verification is required");
        }
        return createAuthResponse(user);
    }

    private AuthResponse createAuthResponse(User user) {
        AuthFlowDtos.TokenResponse session = securityService.issueSession(user);
        return new AuthResponse(session.token(), session.refreshToken(), session.email(), session.role(), "Bearer");
    }

    private void validateStudentProfile(RegisterRequest request) {
        if (isBlank(request.fullName()) || isBlank(request.rollNumber())
                || isBlank(request.department()) || request.cgpa() == null || request.backlogs() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Student profile fields are required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
