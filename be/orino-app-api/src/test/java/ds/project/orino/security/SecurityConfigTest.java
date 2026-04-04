package ds.project.orino.security;

import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.fixedschedule.repository.FixedScheduleRepository;
import ds.project.orino.domain.goal.repository.GoalRepository;
import ds.project.orino.domain.goal.repository.MilestoneRepository;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.routine.repository.RoutineCheckRepository;
import ds.project.orino.domain.routine.repository.RoutineExceptionRepository;
import ds.project.orino.domain.routine.repository.RoutineRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityConfigTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private FixedScheduleRepository fixedScheduleRepository;

    @Autowired
    private RoutineExceptionRepository routineExceptionRepository;

    @Autowired
    private RoutineCheckRepository routineCheckRepository;

    @Autowired
    private RoutineRepository routineRepository;

    @BeforeEach
    void setUp() {
        routineExceptionRepository.deleteAll();
        routineCheckRepository.deleteAll();
        routineRepository.deleteAll();
        fixedScheduleRepository.deleteAll();
        milestoneRepository.deleteAll();
        goalRepository.deleteAll();
        categoryRepository.deleteAll();
        memberRepository.deleteAll();
        memberRepository.save(MemberFixture.create());
    }

    @Test
    @DisplayName("/api/auth/** 경로는 인증 없이 접근할 수 있다")
    void authEndpoints_permitAll() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "%s", "password": "%s"}
                                """.formatted(MemberFixture.DEFAULT_LOGIN_ID, MemberFixture.DEFAULT_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/swagger-ui/** 경로는 인증 없이 접근할 수 있다")
    void swaggerUi_permitAll() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/v3/api-docs 경로는 인증 없이 접근할 수 있다")
    void apiDocs_permitAll() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("보호된 경로는 인증 없이 접근하면 403을 반환한다")
    void protectedEndpoint_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/protected"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("유효한 JWT로 보호된 경로에 접근하면 Security에서 차단하지 않는다")
    void protectedEndpoint_withValidJwt_passedSecurity() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "%s", "password": "%s"}
                                """.formatted(MemberFixture.DEFAULT_LOGIN_ID, MemberFixture.DEFAULT_PASSWORD)))
                .andReturn();

        String body = loginResult.getResponse().getContentAsString();
        String accessToken = com.jayway.jsonpath.JsonPath.read(body, "$.data.accessToken");

        int status = mockMvc.perform(get("/api/protected")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse().getStatus();

        assertThat(status).isNotIn(401, 403);
    }
}
