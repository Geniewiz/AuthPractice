package org.example.authpractice.auth.repo;

import org.example.authpractice.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long>{
    Optional<RefreshToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);
}
