package org.example.authpractice.auth.service;

import org.example.authpractice.auth.domain.User;
import org.example.authpractice.auth.jwt.JwtProvider;
import org.example.authpractice.auth.repo.UserRepository;
import org.example.authpractice.common.exception.ConflictException;
import org.example.authpractice.common.exception.UnauthorizedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Service
// 인증 관련 핵심 비즈니스 로직
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepo,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider,
                       RefreshTokenService refreshTokenService) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
    }

    public record Tokens(String accessToken, String refreshToken){}
    public record SignupResult(Long userId, String email){}

    // 이메일 중복 체크 및 사용자 생성
    @Transactional
    public SignupResult signup(String email, String rawPassword) {
        String norm = normalizeEmail(email);

        if(userRepo.existsByEmailNormalized(norm)){
            throw new ConflictException("EMAIL_ALREADY_EXISTS");
        }
        User user = new User();
        user.setEmail(email.trim());
        user.setEmailNormalized(norm);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setStatus(User.Status.ACTIVE);

        try {
            userRepo.save(user);
        }
        catch(DataIntegrityViolationException e){
            // UNIQUE(email_normalized) 동시성 충돌 대비
            throw new ConflictException("EMAIL_ALREADY_EXISTS");
        }

        return new SignupResult(user.getId(), user.getEmail());
    }

    // 사용자 검증 및 토큰 발급 (Access + Refresh)
    @Transactional
    public Tokens login(String email, String rawPassword, String deviceId, String userAgent) {
        String norm = normalizeEmail(email);

        User user = userRepo.findByEmailNormalized(norm)
                .orElseThrow(() -> new UnauthorizedException("BAD_CREDENTIALS"));

        if (user.getStatus() != User.Status.ACTIVE) throw new UnauthorizedException("USER_NOT_ACTIVE");
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) throw new UnauthorizedException("BAD_CREDENTIALS");

        String access = jwtProvider.issueAccessToken(user.getId());
        String refresh = refreshTokenService.issue(user.getId(), deviceId, userAgent).refreshPlain();

        user.setLastLoginAt(LocalDateTime.now());
        return new Tokens(access, refresh);
    }

    // Refresh Token 검증 및 교체 (RTR), 새 Access Token 발급
    @Transactional
    public Tokens refresh(String refreshPlain){
        var rotated = refreshTokenService.rotate(refreshPlain);
        String access = jwtProvider.issueAccessToken(rotated.userId());
        return new Tokens(access, rotated.refreshPlain());
    }

    // Refresh Token 무효화
    @Transactional
    public void logout(String refreshPlain){
        refreshTokenService.revoke(refreshPlain);
    }

    private String normalizeEmail(String email) {
        if(email == null) return null;
        return email.trim().toLowerCase();
    }

}
