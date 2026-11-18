package com.example.demo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.security.Principal;

import com.example.demo.entity.SecurityToken;
import com.example.demo.entity.PlacementOfficerProfile;
import com.example.demo.entity.User;
import com.example.demo.repository.PlacementOfficerProfileRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuthTokenSink;

@Component
@Profile("e2e")
class E2eAdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final PlacementOfficerProfileRepository officers;
    private final PasswordEncoder passwords;

    E2eAdminBootstrap(UserRepository users, PlacementOfficerProfileRepository officers,
            PasswordEncoder passwords) {
        this.users = users;
        this.officers = officers;
        this.passwords = passwords;
    }

    @Override
    public void run(ApplicationArguments args) {
        users.findByEmail("admin@e2e.test").orElseGet(() -> users.save(User.builder()
                .email("admin@e2e.test")
                .password(passwords.encode("E2e-Admin-Only-Password!"))
                .role(User.Role.ADMIN)
                .enabled(true)
                .emailVerified(true)
                .build()));

        User officer = users.findByEmail("officer@e2e.test").orElseGet(() -> users.save(User.builder()
                .email("officer@e2e.test")
                .password(passwords.encode("E2e-Officer-Only-Password!"))
                .role(User.Role.PLACEMENT_OFFICER)
                .enabled(true)
                .emailVerified(false)
                .build()));
        officers.findByUserId(officer.getId()).orElseGet(() -> officers.save(PlacementOfficerProfile.builder()
                .user(officer)
                .fullName("E2E Officer")
                .build()));
    }
}

@Component
@Profile("e2e")
class E2eTokenMailbox implements AuthTokenSink {
    private final Map<String, Mail> messages = new ConcurrentHashMap<>();

    @Override
    public void deliver(String email, SecurityToken.Type type, String rawToken) {
        messages.put(email.toLowerCase(), new Mail(type.name(), rawToken));
    }

    Mail read(String email) {
        return messages.get(email.toLowerCase());
    }

    record Mail(String type, String token) {}
}

@RestController
@Profile("e2e")
@RequestMapping("/api/e2e/mailbox")
class E2eMailboxController {
    private final E2eTokenMailbox mailbox;

    E2eMailboxController(E2eTokenMailbox mailbox) {
        this.mailbox = mailbox;
    }

    @GetMapping("/{email}")
    E2eTokenMailbox.Mail read(@PathVariable String email) {
        E2eTokenMailbox.Mail mail = mailbox.read(email);
        if (mail == null)
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "No test message is available");
        return mail;
    }
}
