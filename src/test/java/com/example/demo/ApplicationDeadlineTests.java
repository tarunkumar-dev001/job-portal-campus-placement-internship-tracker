package com.example.demo;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.entity.Job;
import com.example.demo.entity.StudentProfile;
import com.example.demo.entity.User;
import com.example.demo.repository.ApplicationRepository;
import com.example.demo.repository.JobRepository;
import com.example.demo.repository.StudentProfileRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ApplicationService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationDeadlineTests {
    private UserRepository users;
    private JobRepository jobs;
    private StudentProfileRepository profiles;
    private ApplicationRepository applications;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        jobs = mock(JobRepository.class);
        profiles = mock(StudentProfileRepository.class);
        applications = mock(ApplicationRepository.class);
        service = new ApplicationService(applications, users, profiles, jobs);
    }

    @Test
    void rejectsExpiredJobAndDoesNotCreateApplication() {
        User student = User.builder().id(1L).email("student@example.com").role(User.Role.STUDENT).enabled(true).build();
        Job job = Job.builder().id(2L).deadline(LocalDate.now().minusDays(1)).minCgpa(0.0)
                .maxBacklogs(5).published(true).closed(false).build();
        when(users.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(jobs.findById(2L)).thenReturn(Optional.of(job));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.apply(2L, student.getEmail()));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(applications, never()).save(any());
    }

    @Test
    void rejectsDuplicateApplication() {
        User student = User.builder().id(1L).email("student@example.com").role(User.Role.STUDENT).enabled(true).build();
        Job job = Job.builder().id(2L).deadline(LocalDate.now().plusDays(1)).minCgpa(0.0)
                .maxBacklogs(5).published(true).closed(false).build();
        StudentProfile profile = StudentProfile.builder().user(student).cgpa(8.0).backlogs(0).build();
        when(users.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(jobs.findById(2L)).thenReturn(Optional.of(job));
        when(profiles.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(applications.findByStudentIdAndJobId(1L, 2L)).thenReturn(Optional.of(mock(com.example.demo.entity.Application.class)));

        assertThrows(ResponseStatusException.class, () -> service.apply(2L, student.getEmail()));
        verify(applications, never()).save(any());
    }
}
