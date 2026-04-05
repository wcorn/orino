package ds.project.orino.planner.reflection.controller;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.preference.repository.PriorityRuleRepository;
import ds.project.orino.domain.preference.repository.UserPreferenceRepository;
import ds.project.orino.domain.reflection.repository.DailyReflectionRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReflectionControllerTest extends ApiTestSupport {

    @Autowired private MemberRepository memberRepository;
    @Autowired private DailyReflectionRepository reflectionRepository;
    @Autowired private PriorityRuleRepository priorityRuleRepository;
    @Autowired private UserPreferenceRepository userPreferenceRepository;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        reflectionRepository.deleteAll();
        priorityRuleRepository.deleteAll();
        userPreferenceRepository.deleteAll();
        memberRepository.deleteAll();
        memberRepository.save(MemberFixture.create());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "%s", "password": "%s"}
                                """.formatted(
                                MemberFixture.DEFAULT_LOGIN_ID,
                                MemberFixture.DEFAULT_PASSWORD)))
                .andReturn();

        accessToken = com.jayway.jsonpath.JsonPath.read(
                loginResult.getResponse().getContentAsString(),
                "$.data.accessToken");
    }

    @Test
    @DisplayName("POST /api/reflections - 회고 작성 성공")
    void create_success() throws Exception {
        mockMvc.perform(post("/api/reflections")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date": "2026-04-06", "mood": 4,
                                 "memo": "오늘 좋았다"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-04-06"))
                .andExpect(jsonPath("$.data.mood").value(4))
                .andExpect(jsonPath("$.data.memo").value("오늘 좋았다"));
    }

    @Test
    @DisplayName("POST /api/reflections - mood 범위 벗어나면 400")
    void create_invalidMood() throws Exception {
        mockMvc.perform(post("/api/reflections")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date": "2026-04-06", "mood": 6}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/reflections - 같은 날짜 중복 작성 시 INVALID_STATE")
    void create_duplicateDate() throws Exception {
        mockMvc.perform(post("/api/reflections")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date": "2026-04-06", "mood": 3}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/reflections")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date": "2026-04-06", "mood": 5}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/reflections?date= - 회고 조회 성공")
    void getByDate_success() throws Exception {
        mockMvc.perform(post("/api/reflections")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date": "2026-04-06", "mood": 3,
                                 "memo": "평범"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/reflections")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", "2026-04-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mood").value(3))
                .andExpect(jsonPath("$.data.memo").value("평범"));
    }

    @Test
    @DisplayName("GET /api/reflections?date= - 없으면 404")
    void getByDate_notFound() throws Exception {
        mockMvc.perform(get("/api/reflections")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", "2026-04-06"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/reflections/{id} - 회고 수정 성공")
    void update_success() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/reflections")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date": "2026-04-06", "mood": 2,
                                 "memo": "기분 별로"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Integer id = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(put("/api/reflections/" + id)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mood": 5, "memo": "다시 보니 좋음"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mood").value(5))
                .andExpect(jsonPath("$.data.memo").value("다시 보니 좋음"));
    }

    @Test
    @DisplayName("PUT /api/reflections/{id} - 존재하지 않는 회고는 404")
    void update_notFound() throws Exception {
        mockMvc.perform(put("/api/reflections/99999")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mood": 3}
                                """))
                .andExpect(status().isNotFound());
    }
}
