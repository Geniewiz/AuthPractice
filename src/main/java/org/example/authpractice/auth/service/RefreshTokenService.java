package org.example.authpractice.auth.service;


import org.example.authpractice.auth.domain.RefreshToken;
import org.example.authpractice.auth.repo.RefreshTokenRepository;
import org.example.authpractice.common.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
// Refresh Token 관리 (생성, 저장, 회전, 무효화)
public class RefreshTokenService {

    private final RefreshTokenRepository repo;
    private final int ttlDays;
    private final long idleSeconds;

    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository repo,
            @Value("${app.refresh.ttl-days}") int ttlDays,
            @Value("${app.refresh.idle-seconds:0}") long idleSeconds,
            RefreshTokenRepository refreshTokenRepository){
        this.repo = repo;
        this.ttlDays = ttlDays;
        this.idleSeconds = idleSeconds;
    }

    public record Issued(String refreshPlain, LocalDateTime expiresAt) {}
    public record Rotated(Long userId, String refreshPlain, LocalDateTime expiresAt) {}

    // 불투명한(Opaque) Refresh Token 생성 및 해시 저장
    @Transactional
    public Issued issue(Long userId, String deviceId, String userAgent){
        String plain = generateOpaqueToken();
        String hash = sha256Hex(plain);

        LocalDateTime now = LocalDateTime.now();

        RefreshToken rt = new RefreshToken();
        rt.setUserId(userId);
        rt.setTokenHash(hash);
        rt.setExpiresAt(now.plusDays(ttlDays));
        rt.setLastUsedAt(now);
        rt.setDeviceId(deviceId);
        rt.setUserAgent(userAgent);

        repo.save(rt);
        return new Issued(plain, rt.getExpiresAt());
    }

    // Refresh Token 교체 (기존 토큰 무효화 -> 새 토큰 발급) - 보안 강화(RTR)
    @Transactional
    public Rotated rotate(String refreshPlain){
        String hash = sha256Hex(refreshPlain);
        RefreshToken rt = repo.findByTokenHashAndRevokedAtIsNull(hash)
                .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH"));

        LocalDateTime now = LocalDateTime.now();

        if(rt.getExpiresAt().isBefore(now)){
            rt.setRevokedAt(now);
            repo.save(rt);
            throw new UnauthorizedException("REFRESH_EXPIRED");
        }

        if(idleSeconds > 0 && rt.getLastUsedAt().plusSeconds(idleSeconds).isBefore(now)){
            rt.setRevokedAt(now);
            repo.save(rt);
            throw new UnauthorizedException("REFRESH_IDLE_EXPIRED");
        }

        rt.setRevokedAt(now);
        repo.save(rt);

        Issued newly = issue(rt.getUserId(), rt.getDeviceId(), rt.getUserAgent());
        return new Rotated(rt.getUserId(), newly.refreshPlain(), newly.expiresAt());
    }

    // Refresh Token 무효화
    @Transactional
    public void revoke(String refreshPlain){
        String hash = sha256Hex(refreshPlain);
        repo.findByTokenHashAndRevokedAtIsNull(hash).ifPresent(rt -> {
            rt.setRevokedAt(LocalDateTime.now());
            repo.save(rt);
        });
    }

    private String generateOpaqueToken(){
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String input){
        try{
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch(Exception e){
            throw new IllegalStateException(e);
        }
    }


}
