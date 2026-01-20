package org.example.authpractice.me.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
// 인증된 사용자 정보 조회 API
public class MeController {

    // 현재 로그인한 사용자의 ID 반환
    @GetMapping("/me")
    public String me(Authentication authentication) {
        return "userId=" + authentication.getName();
    }
}