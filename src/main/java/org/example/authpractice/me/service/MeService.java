package org.example.authpractice.me.service;

import org.example.authpractice.auth.domain.User;
import org.example.authpractice.auth.repo.UserRepository;
import org.example.authpractice.common.exception.ConflictException;
import org.example.authpractice.common.exception.UnauthorizedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class MeService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public MeService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record MeProfile(Long id, String email, User.Status status, LocalDateTime lastLoginAt) {}

    @Transactional(readOnly = true)
    public MeProfile getProfile(Long userId) {
        User user = getActiveUser(userId);
        return new MeProfile(user.getId(), user.getEmail(), user.getStatus(), user.getLastLoginAt());
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = getActiveUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("BAD_CREDENTIALS");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    @Transactional
    public void changeEmail(Long userId, String currentPassword, String newEmail) {
        User user = getActiveUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("BAD_CREDENTIALS");
        }

        String normalizedEmail = normalizeEmail(newEmail);
        userRepository.findByEmailNormalized(normalizedEmail)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new ConflictException("EMAIL_ALREADY_EXISTS");
                });

        user.setEmail(newEmail.trim());
        user.setEmailNormalized(normalizedEmail);

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS");
        }
    }

    private User getActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND"));
        if (user.getStatus() != User.Status.ACTIVE) {
            throw new UnauthorizedException("USER_NOT_ACTIVE");
        }
        return user;
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}
