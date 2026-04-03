package ds.project.orino.auth.controller;

import ds.project.orino.config.TestRedisConfig;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        memberRepository.deleteAll();
        memberRepository.save(new Member("admin", passwordEncoder.encode("password")));
    }

    @Test
    @DisplayName("POST /api/auth/login - 로그인 성공 시 AT와 RT 쿠키를 반환한다")
    void login_success() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "admin", "password": "password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().secure("refreshToken", true));
    }

    @Test
    @DisplayName("POST /api/auth/login - 잘못된 비밀번호 시 401을 반환한다")
    void login_invalidPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "admin", "password": "wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-ERR-001"));
    }

    @Test
    @DisplayName("POST /api/auth/login - 존재하지 않는 아이디 시 401을 반환한다")
    void login_invalidLoginId() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "unknown", "password": "password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-ERR-001"));
    }

    @Test
    @DisplayName("POST /api/auth/login - 빈 아이디 시 400을 반환한다")
    void login_blankLoginId() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "", "password": "password"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/reissue - RT 쿠키로 토큰 갱신에 성공한다")
    void reissue_success() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "admin", "password": "password"}
                                """))
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");

        mockMvc.perform(post("/api/auth/reissue")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(cookie().exists("refreshToken"));
    }

    @Test
    @DisplayName("POST /api/auth/reissue - RT 쿠키 없으면 401을 반환한다")
    void reissue_noCookie() throws Exception {
        mockMvc.perform(post("/api/auth/reissue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-ERR-002"));
    }

    @Test
    @DisplayName("POST /api/auth/logout - 로그아웃 후 RT 쿠키를 제거한다")
    void logout_success() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "admin", "password": "password"}
                                """))
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refreshToken", 0));
    }

    @Test
    @DisplayName("POST /api/auth/logout - RT 쿠키 없이도 200을 반환한다")
    void logout_noCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk());
    }
}
