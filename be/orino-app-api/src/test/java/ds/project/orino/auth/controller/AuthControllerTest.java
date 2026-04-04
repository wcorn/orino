package ds.project.orino.auth.controller;

import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.goal.repository.GoalRepository;
import ds.project.orino.domain.goal.repository.MilestoneRepository;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.MemberFixture;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private GoalRepository goalRepository;

    @BeforeEach
    void setUp() {
        milestoneRepository.deleteAll();
        goalRepository.deleteAll();
        categoryRepository.deleteAll();
        memberRepository.deleteAll();
        memberRepository.save(MemberFixture.create());
    }

    @Test
    @DisplayName("POST /api/auth/login - 로그인 성공 시 AT와 RT 쿠키를 반환한다")
    void login_success() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "%s", "password": "%s"}
                                """.formatted(MemberFixture.DEFAULT_LOGIN_ID, MemberFixture.DEFAULT_PASSWORD)))
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
                                {"loginId": "%s", "password": "wrong"}
                                """.formatted(MemberFixture.DEFAULT_LOGIN_ID)))
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
                                {"loginId": "%s", "password": "%s"}
                                """.formatted(MemberFixture.DEFAULT_LOGIN_ID, MemberFixture.DEFAULT_PASSWORD)))
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
                                {"loginId": "%s", "password": "%s"}
                                """.formatted(MemberFixture.DEFAULT_LOGIN_ID, MemberFixture.DEFAULT_PASSWORD)))
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
