package org.example.authpractice.me.service;

import org.example.authpractice.auth.domain.User;
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
class MeServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MeService meService;

    @Test
    @DisplayName("내 정보 조회 성공")
    void get_profile_success() {
        User user = user(1L, "user@example.com", "hashed");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        var profile = meService.getProfile(1L);

        assertThat(profile.id()).isEqualTo(1L);
        assertThat(profile.email()).isEqualTo("user@example.com");
        assertThat(profile.status()).isEqualTo(User.Status.ACTIVE);
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 현재 비밀번호 불일치")
    void change_password_fail_bad_credentials() {
        User user = user(1L, "user@example.com", "hashed");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "hashed")).willReturn(false);

        assertThatThrownBy(() -> meService.changePassword(1L, "wrong", "newPassword123"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("BAD_CREDENTIALS");
    }

    @Test
    @DisplayName("이메일 변경 실패 - 이미 존재하는 이메일")
    void change_email_fail_duplicate() {
        User me = user(1L, "user@example.com", "hashed");
        User other = user(2L, "other@example.com", "hashed2");

        given(userRepository.findById(1L)).willReturn(Optional.of(me));
        given(passwordEncoder.matches("password123", "hashed")).willReturn(true);
        given(userRepository.findByEmailNormalized("other@example.com")).willReturn(Optional.of(other));

        assertThatThrownBy(() -> meService.changeEmail(1L, "password123", "other@example.com"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("EMAIL_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("이메일 변경 성공")
    void change_email_success() {
        User me = user(1L, "before@example.com", "hashed");

        given(userRepository.findById(1L)).willReturn(Optional.of(me));
        given(passwordEncoder.matches("password123", "hashed")).willReturn(true);
        given(userRepository.findByEmailNormalized("after@example.com")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        meService.changeEmail(1L, "password123", "after@example.com");

        assertThat(me.getEmail()).isEqualTo("after@example.com");
        assertThat(me.getEmailNormalized()).isEqualTo("after@example.com");
        verify(userRepository).save(me);
    }

    private User user(Long id, String email, String passwordHash) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setEmailNormalized(email);
        user.setPasswordHash(passwordHash);
        user.setStatus(User.Status.ACTIVE);
        return user;
    }
}
