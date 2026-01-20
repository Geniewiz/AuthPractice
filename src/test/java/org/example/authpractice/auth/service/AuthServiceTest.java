package org.example.authpractice.auth.service;

import org.example.authpractice.auth.domain.User;
import org.example.authpractice.auth.jwt.JwtProvider;
import org.example.authpractice.auth.repo.UserRepository;
import org.example.authpractice.common.exception.ConflictException;
import org.example.authpractice.common.exception.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepo;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("회원가입 성공")
    void signup_success() {
        // given
        String email = "test@example.com";
        String password = "password123";
        String encodedPassword = "encodedPassword";

        given(userRepo.existsByEmailNormalized(email)).willReturn(false);
        given(passwordEncoder.encode(password)).willReturn(encodedPassword);

        // when
        var result = authService.signup(email, password);

        // then
        assertThat(result.email()).isEqualTo(email);
        verify(userRepo).save(any(User.class));
    }

    @Test
    @DisplayName("회원가입 실패 - 이미 존재하는 이메일")
    void signup_fail_duplicate_email() {
        // given
        String email = "test@example.com";
        given(userRepo.existsByEmailNormalized(email)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signup(email, "password"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("EMAIL_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("로그인 성공")
    void login_success() {
        // given
        String email = "test@example.com";
        String password = "password";
        String encoded = "encoded";
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setPasswordHash(encoded);
        user.setStatus(User.Status.ACTIVE);

        given(userRepo.findByEmailNormalized(email)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(password, encoded)).willReturn(true);
        given(jwtProvider.issueAccessToken(1L)).willReturn("access-token");
        given(refreshTokenService.issue(1L, "device", "agent"))
                .willReturn(new RefreshTokenService.Issued("refresh-token", null));

        // when
        var tokens = authService.login(email, password, "device", "agent");

        // then
        assertThat(tokens.accessToken()).isEqualTo("access-token");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치")
    void login_fail_password_mismatch() {
        // given
        String email = "test@example.com";
        String password = "wrong";
        String encoded = "encoded";
        User user = new User();
        user.setPasswordHash(encoded);
        user.setStatus(User.Status.ACTIVE);

        given(userRepo.findByEmailNormalized(email)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(password, encoded)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(email, password, null, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("BAD_CREDENTIALS");
    }
}
