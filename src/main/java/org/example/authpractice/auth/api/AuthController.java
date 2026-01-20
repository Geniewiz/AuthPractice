package org.example.authpractice.auth.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.authpractice.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/auth")
// 인증 관련 API 엔드포인트 (회원가입, 로그인, 재발급, 로그아웃) 담당
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record SignupReq(
            @Email @NotBlank String email,
            @NotBlank @Size (min = 8, max = 64) String password
    ) {}

    public record SignupRes(Long userId, String email) {}

    public record LoginReq(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record RefreshReq(@NotBlank String refreshToken) {}

    public record TokenRes(String accessToken, String refreshToken) {}

    // 회원가입 처리
    @PostMapping("/signup")
    public ResponseEntity<SignupRes> signup(@RequestBody @Valid SignupReq req) {
        var result = authService.signup(req.email(), req.password());

        return ResponseEntity.status(201).body(new SignupRes(result.userId(), result.email()));
    }

    // 로그인 처리 및 토큰 발급
    @PostMapping("/login")
    public ResponseEntity<TokenRes> login(
            @RequestBody @Valid LoginReq req,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        var tokens = authService.login(req.email(), req.password(), deviceId, userAgent);
        return ResponseEntity.ok(new TokenRes(tokens.accessToken(), tokens.refreshToken()));
    }

    // 액세스 토큰 재발급 (Refresh Token 사용)
    @PostMapping("/refresh")
    public ResponseEntity<TokenRes> refresh(@RequestBody @Valid RefreshReq req) {
        var tokens = authService.refresh(req.refreshToken());
        return ResponseEntity.ok(new TokenRes(tokens.accessToken(), tokens.refreshToken()));
    }

    // 로그아웃 처리 (Refresh Token 무효화)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid RefreshReq req) {
        authService.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
