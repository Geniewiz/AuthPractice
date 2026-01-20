package org.example.authpractice.auth.service;

import org.example.authpractice.auth.domain.RefreshToken;
import org.example.authpractice.auth.repo.RefreshTokenRepository;
import org.example.authpractice.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repo;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        // ttlDays=7, idleSeconds=3600
        refreshTokenService = new RefreshTokenService(repo, 7, 3600, repo);
    }

    @Test
    @DisplayName("Refresh Token 발급 성공")
    void issue_success() {
        // given
        Long userId = 1L;

        // when
        var issued = refreshTokenService.issue(userId, "device", "agent");

        // then
        assertThat(issued.refreshPlain()).isNotNull();
        verify(repo).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Refresh Token 회전(Rotate) 성공")
    void rotate_success() {
        // given
        String oldPlain = "old-token";
        RefreshToken rt = new RefreshToken();
        rt.setUserId(1L);
        rt.setExpiresAt(LocalDateTime.now().plusDays(1));
        rt.setLastUsedAt(LocalDateTime.now());
        rt.setTokenHash("hashed-old-token"); // 실제 해시 로직은 서비스 내부에 있으나 여기선 mock 리턴용

        // 서비스 내부에서 해싱을 다시 하므로, anyString()으로 매칭하거나 실제 해시 로직을 감안해야 함.
        // 여기서는 서비스 메서드가 plain text를 해싱해서 repo를 찾으므로, 
        // repo.findByTokenHash...가 호출될 때 적절한 객체를 반환하도록 설정.
        given(repo.findByTokenHashAndRevokedAtIsNull(any())).willReturn(Optional.of(rt));

        // when
        var rotated = refreshTokenService.rotate(oldPlain);

        // then
        assertThat(rotated.refreshPlain()).isNotEqualTo(oldPlain);
        assertThat(rt.isRevoked()).isTrue(); // 기존 토큰은 revoke 되어야 함
        verify(repo).save(rt); // 기존 토큰 업데이트
        verify(repo).save(any(RefreshToken.class)); // 새 토큰 저장 (총 2번의 save 호출: 기존꺼 업데이트 + 새꺼 저장)
    }

    @Test
    @DisplayName("Refresh Token 회전 실패 - 만료된 토큰")
    void rotate_fail_expired() {
        // given
        RefreshToken rt = new RefreshToken();
        rt.setExpiresAt(LocalDateTime.now().minusDays(1)); // 만료됨
        given(repo.findByTokenHashAndRevokedAtIsNull(any())).willReturn(Optional.of(rt));

        // when & then
        assertThatThrownBy(() -> refreshTokenService.rotate("token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("REFRESH_EXPIRED");
        
        assertThat(rt.isRevoked()).isTrue(); // 만료된 토큰이라도 시도 시 revoke 처리
    }
}
