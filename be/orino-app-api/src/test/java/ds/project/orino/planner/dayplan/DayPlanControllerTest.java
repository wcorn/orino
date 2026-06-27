package ds.project.orino.planner.dayplan;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.dayplan.repository.DayPlanRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DayPlanControllerTest extends ApiTestSupport {

    private static final String WEEKDAY_PLAN = """
            {
              "name": "평일 공부",
              "color": "violet",
              "recurrence": {
                "freq": "WEEKLY", "interval": 1,
                "byDay": ["MO","TU","WE","TH","FR"], "byMonthDay": null,
                "startsOn": "2026-06-22", "until": null
              },
              "blocks": [
                { "startTime": "10:00", "endTime": "12:00", "label": "개인 프로젝트", "chime": false },
                { "startTime": "08:00", "endTime": null,    "label": "기상",        "chime": true }
              ]
            }""";

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DayPlanRepository dayPlanRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    @Test
    @DisplayName("POST - 플랜을 생성하고 201 + 블록(시작시각 정렬)·recurrenceText를 반환한다")
    void create() throws Exception {
        mockMvc.perform(createPlan(WEEKDAY_PLAN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name").value("평일 공부"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.recurrenceText").value("매주 월·화·수·목·금"))
                .andExpect(jsonPath("$.data.blocks", hasSize(2)))
                // 시작시각 순으로 정렬된다(08:00이 먼저)
                .andExpect(jsonPath("$.data.blocks[0].startTime").value("08:00"))
                .andExpect(jsonPath("$.data.blocks[0].endTime").doesNotExist())
                .andExpect(jsonPath("$.data.blocks[0].label").value("기상"))
                .andExpect(jsonPath("$.data.blocks[1].startTime").value("10:00"))
                .andExpect(jsonPath("$.data.blocks[1].endTime").value("12:00"));
    }

    @Test
    @DisplayName("GET - 플랜 목록을 반환한다")
    void list() throws Exception {
        mockMvc.perform(createPlan(WEEKDAY_PLAN)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/planner/plans").header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plans", hasSize(1)))
                .andExpect(jsonPath("$.data.plans[0].name").value("평일 공부"));
    }

    @Test
    @DisplayName("GET instances - 활성 플랜을 구간 펼침해 날짜별 블록을 준다(주말 생략)")
    void instances() throws Exception {
        mockMvc.perform(createPlan(WEEKDAY_PLAN)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/planner/plans/instances")
                        .param("from", "2026-06-22").param("to", "2026-06-28")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                // 월~금 5일 (토·일 생략)
                .andExpect(jsonPath("$.data.days", hasSize(5)))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-06-22"))
                .andExpect(jsonPath("$.data.days[0].blocks", hasSize(2)))
                .andExpect(jsonPath("$.data.days[0].blocks[0].planName").value("평일 공부"))
                .andExpect(jsonPath("$.data.days[0].blocks[0].startTime").value("08:00"))
                .andExpect(jsonPath("$.data.days[4].date").value("2026-06-26"));
    }

    @Test
    @DisplayName("PATCH - declarative 블록 교체(수정+신규, 누락 삭제)하고 id를 보존한다")
    void update() throws Exception {
        String body = mockMvc.perform(createPlan(WEEKDAY_PLAN))
                .andReturn().getResponse().getContentAsString();
        long planId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        // blocks[0]=기상(08:00), blocks[1]=개인 프로젝트(10:00)
        long wakeBlockId = ((Number) JsonPath.read(body, "$.data.blocks[0].id")).longValue();

        String patch = """
                {
                  "name": "평일 공부",
                  "color": "violet",
                  "recurrence": {
                    "freq": "WEEKLY", "byDay": ["MO","WE","FR"],
                    "startsOn": "2026-06-22", "until": "2026-12-31"
                  },
                  "blocks": [
                    { "id": %d, "startTime": "07:30", "endTime": null, "label": "기상(수정)", "chime": true },
                    {           "startTime": "09:00", "endTime": "12:00", "label": "운동+공부", "chime": false }
                  ]
                }""".formatted(wakeBlockId);

        mockMvc.perform(patch("/api/planner/plans/{id}", planId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON).content(patch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recurrenceText").value("매주 월·수·금"))
                .andExpect(jsonPath("$.data.recurrence.until").value("2026-12-31"))
                .andExpect(jsonPath("$.data.blocks", hasSize(2)))
                // 수정된 기상 블록은 id 보존 + 새 시각/라벨
                .andExpect(jsonPath("$.data.blocks[0].id").value((int) wakeBlockId))
                .andExpect(jsonPath("$.data.blocks[0].startTime").value("07:30"))
                .andExpect(jsonPath("$.data.blocks[0].label").value("기상(수정)"))
                .andExpect(jsonPath("$.data.blocks[1].label").value("운동+공부"));
    }

    @Test
    @DisplayName("DELETE - 플랜을 삭제한다")
    void deletePlan() throws Exception {
        String body = mockMvc.perform(createPlan(WEEKDAY_PLAN))
                .andReturn().getResponse().getContentAsString();
        long planId = ((Number) JsonPath.read(body, "$.data.id")).longValue();

        mockMvc.perform(delete("/api/planner/plans/{id}", planId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());

        assertThat(dayPlanRepository.findById(planId)).isEmpty();
    }

    @Test
    @DisplayName("POST - 블록 시간 역전(end<=start)이면 400 PLN-ERR-002")
    void create_blockTimeReversed_400() throws Exception {
        String invalid = """
                { "name": "x", "recurrence": { "freq": "DAILY", "startsOn": "2026-06-22" },
                  "blocks": [ { "startTime": "12:00", "endTime": "10:00", "label": "역전", "chime": false } ] }""";

        mockMvc.perform(createPlan(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLN-ERR-002"));
    }

    @Test
    @DisplayName("POST - 범위 블록이 겹치면 400 PLN-ERR-002")
    void create_blocksOverlap_400() throws Exception {
        String invalid = """
                { "name": "x", "recurrence": { "freq": "DAILY", "startsOn": "2026-06-22" },
                  "blocks": [
                    { "startTime": "10:00", "endTime": "12:00", "label": "a", "chime": false },
                    { "startTime": "11:00", "endTime": "13:00", "label": "b", "chime": false }
                  ] }""";

        mockMvc.perform(createPlan(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLN-ERR-002"));
    }

    @Test
    @DisplayName("POST - 잘못된 freq면 400 PLN-ERR-002")
    void create_invalidFreq_400() throws Exception {
        String invalid = """
                { "name": "x", "recurrence": { "freq": "YEARLY", "startsOn": "2026-06-22" }, "blocks": [] }""";

        mockMvc.perform(createPlan(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLN-ERR-002"));
    }

    @Test
    @DisplayName("PATCH - 존재하지 않는 플랜이면 404")
    void update_notFound_404() throws Exception {
        mockMvc.perform(patch("/api/planner/plans/{id}", 999999)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON).content(WEEKDAY_PLAN))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE - 타인 플랜이면 404")
    void delete_otherMember_404() throws Exception {
        Member other = memberRepository.save(MemberFixture.create("other", "password"));
        var plan = dayPlanRepository.save(new ds.project.orino.domain.planner.dayplan.entity.DayPlan(
                other.getId(), "남의 플랜", null,
                ds.project.orino.domain.planner.dayplan.entity.DayPlanFreq.DAILY, 1,
                null, null, java.time.LocalDate.of(2026, 6, 22), null));

        mockMvc.perform(delete("/api/planner/plans/{id}", plan.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createPlan(String body) {
        return post("/api/planner/plans")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
