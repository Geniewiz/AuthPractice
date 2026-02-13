package org.example.authpractice.auth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.authpractice.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 초기화가 필요하다면 여기서 수행
        // @Transactional 덕분에 각 테스트 후 롤백됨
    }

    @Test
    @DisplayName("통합: 회원가입 성공")
    void signup_integration() throws Exception {
        // given
        var req = new AuthController.SignupReq("newuser@example.com", "password123");

        // when & then
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.userId").exists());
    }

    @Test
    @DisplayName("통합: 로그인 성공 및 토큰 발급")
    void login_integration() throws Exception {
        // given: 미리 유저 생성
        String email = "loginuser@example.com";
        String password = "password123";
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthController.SignupReq(email, password))))
                .andExpect(status().isCreated());

        var loginReq = new AuthController.LoginReq(email, password);

        // when & then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("통합: 내 정보 조회 (인증 성공)")
    void me_integration_success() throws Exception {
        AuthController.TokenRes tokenRes = signupAndLogin("meuser@example.com", "password123");

        mockMvc.perform(get("/me")
                        .header("Authorization", "Bearer " + tokenRes.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("meuser@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("통합: 인증 없이 접근 시 실패")
    void me_integration_fail_unauthorized() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("통합: 내 비밀번호 변경 성공")
    void change_password_integration_success() throws Exception {
        String oldPassword = "password123";
        String newPassword = "newPassword123";
        String email = "pass-change@example.com";

        AuthController.TokenRes tokenRes = signupAndLogin(email, oldPassword);
        var req = new org.example.authpractice.me.api.MeController.ChangePasswordReq(oldPassword, newPassword);

        mockMvc.perform(patch("/me/password")
                        .header("Authorization", "Bearer " + tokenRes.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.LoginReq(email, oldPassword))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.LoginReq(email, newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("통합: 내 이메일 변경 성공")
    void change_email_integration_success() throws Exception {
        String currentEmail = "before-change@example.com";
        String newEmail = "after-change@example.com";
        String password = "password123";

        AuthController.TokenRes tokenRes = signupAndLogin(currentEmail, password);
        var req = new org.example.authpractice.me.api.MeController.ChangeEmailReq(newEmail, password);

        mockMvc.perform(patch("/me/email")
                        .header("Authorization", "Bearer " + tokenRes.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/me")
                        .header("Authorization", "Bearer " + tokenRes.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.LoginReq(currentEmail, password))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.LoginReq(newEmail, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    private AuthController.TokenRes signupAndLogin(String email, String password) throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.SignupReq(email, password))))
                .andExpect(status().isCreated());

        String resBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.LoginReq(email, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(resBody, AuthController.TokenRes.class);
    }
}
