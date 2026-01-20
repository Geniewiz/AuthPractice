package org.example.authpractice.auth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.authpractice.TestcontainersConfiguration;
import org.example.authpractice.auth.domain.User;
import org.example.authpractice.auth.repo.UserRepository;
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

    @Autowired
    private UserRepository userRepo;

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
        // given: 회원가입 -> 로그인 -> 토큰 획득
        String email = "meuser@example.com";
        String password = "password123";

        // 1. Signup
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthController.SignupReq(email, password))));

        // 2. Login
        String resBody = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthController.LoginReq(email, password))))
                .andReturn().getResponse().getContentAsString();

        AuthController.TokenRes tokenRes = objectMapper.readValue(resBody, AuthController.TokenRes.class);

        // when & then
        mockMvc.perform(get("/me")
                        .header("Authorization", "Bearer " + tokenRes.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.containsString("userId=")));
    }

    @Test
    @DisplayName("통합: 인증 없이 접근 시 실패")
    void me_integration_fail_unauthorized() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().isForbidden()); // Spring Security 기본 설정상 403 Forbidden
    }
}
