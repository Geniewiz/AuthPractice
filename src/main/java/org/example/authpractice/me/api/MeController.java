package org.example.authpractice.me.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.authpractice.common.exception.UnauthorizedException;
import org.example.authpractice.me.service.MeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
// 인증된 사용자 정보 조회 API
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = meService;
    }

    public record MeRes(Long id, String email, String status, LocalDateTime lastLoginAt) {}

    public record ChangePasswordReq(
            @NotBlank @Size(min = 8, max = 64) String currentPassword,
            @NotBlank @Size(min = 8, max = 64) String newPassword
    ) {}

    public record ChangeEmailReq(
            @Email @NotBlank String newEmail,
            @NotBlank String currentPassword
    ) {}

    @GetMapping("/me")
    public ResponseEntity<MeRes> me(Authentication authentication) {
        Long userId = currentUserId(authentication);
        var profile = meService.getProfile(userId);

        return ResponseEntity.ok(new MeRes(
                profile.id(),
                profile.email(),
                profile.status().name(),
                profile.lastLoginAt()
        ));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @RequestBody @Valid ChangePasswordReq req
    ) {
        Long userId = currentUserId(authentication);
        meService.changePassword(userId, req.currentPassword(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/email")
    public ResponseEntity<Void> changeEmail(
            Authentication authentication,
            @RequestBody @Valid ChangeEmailReq req
    ) {
        Long userId = currentUserId(authentication);
        meService.changeEmail(userId, req.currentPassword(), req.newEmail());
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new UnauthorizedException("AUTHENTICATION_REQUIRED");
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("INVALID_PRINCIPAL");
        }
    }
}
