package com.example.demo.security;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.demo.entity.User;
import com.example.demo.repository.RefreshTokenRepository;
import com.example.demo.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokens;
    private final boolean requireEmailVerification;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, UserRepository userRepository,
            RefreshTokenRepository refreshTokens,
            @Value("${app.security.require-email-verification:true}") boolean requireEmailVerification) {
        this.jwtUtils = jwtUtils;
        this.userRepository = userRepository;
        this.refreshTokens = refreshTokens;
        this.requireEmailVerification = requireEmailVerification;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");

        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            if (jwtUtils.validateToken(token)) {
                String email = jwtUtils.extractEmail(token);
                String sessionId = jwtUtils.extractSessionId(token);
                if (sessionId != null
                        && refreshTokens.existsBySessionIdAndRevokedAtIsNullAndExpiresAtAfter(sessionId, Instant.now())) {
                    userRepository.findByEmail(email).ifPresent(user -> authenticate(user, request));
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(User user, HttpServletRequest request) {
        if (!user.isEnabled() || (requireEmailVerification && !user.isEmailVerified())) {
            return;
        }
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of(authority));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
