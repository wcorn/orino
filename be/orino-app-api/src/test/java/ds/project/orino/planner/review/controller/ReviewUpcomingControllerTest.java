package ds.project.orino.planner.review.controller;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.repository.FlashcardRepository;
import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.FixedClock;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@FixedClock
class ReviewUpcomingControllerTest extends ApiTestSupport {

    private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);
    private static final BigDecimal EF = new BigDecimal("2.50");
    private static final String ITEMS = "[{\"id\":\"a\",\"text\":\"1단계\"},{\"id\":\"b\",\"text\":\"2단계\"}]";

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private StudyMaterialRepository studyMaterialRepository;
    @Autowired
    private FlashcardRepository flashcardRepository;
    @Autowired
    private ReviewScheduleRepository reviewScheduleRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private Member member;
    private Member otherMember;
    private StudyMaterial materialA;
    private StudyMaterial materialB;
    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        member = memberRepository.save(MemberFixture.create());
        otherMember = memberRepository.save(MemberFixture.create("other", "password"));
        materialA = studyMaterialRepository.save(new StudyMaterial(member.getId(), "이펙티브 자바", MaterialType.BOOK));
        materialB = studyMaterialRepository.save(new StudyMaterial(member.getId(), "모던 자바", MaterialType.LECTURE));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    private Flashcard basicCard(StudyMaterial material) {
        return flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "기본 앞면", "뒷면"));
    }

    private Flashcard orderingCard() {
        return flashcardRepository.save(
                Flashcard.ordering(member.getId(), materialA.getId(), "순서 앞면", ITEMS));
    }

    private Flashcard pairCard() {
        Flashcard c = new Flashcard(member.getId(), materialA.getId(), "짝 앞면", "짝 뒷면");
        c.assignSiblingGroup(777L);
        return flashcardRepository.save(c);
    }

    private ReviewSchedule pending(Flashcard c, LocalDateTime scheduledLocal) {
        return reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), c.getId(), 1, atTestZone(scheduledLocal), 1, EF));
    }

    @Test
    @DisplayName("GET upcoming - scheduled_at ASC 정렬 + whenKind/overdue/cardType/flashcard 동봉")
    void ascending_with_whenkind_and_flags() throws Exception {
        Flashcard a = basicCard(materialA);
        pending(a, TODAY.minusDays(2).atTime(4, 0));  // now, overdue
        pending(a, TODAY.atTime(4, 0));               // now
        pending(a, TODAY.atTime(20, 0));              // today
        pending(a, TODAY.plusDays(3).atTime(4, 0));   // future

        mockMvc.perform(get("/api/planner/reviews/upcoming")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.today").value("2026-01-15"))
                .andExpect(jsonPath("$.data.items", hasSize(4)))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.items[0].whenKind").value("now"))
                .andExpect(jsonPath("$.data.items[0].overdue").value(true))
                .andExpect(jsonPath("$.data.items[0].cardType").value("BASIC"))
                .andExpect(jsonPath("$.data.items[0].flashcard.front").value("기본 앞면"))
                .andExpect(jsonPath("$.data.items[0].flashcard.type").value("BASIC"))
                .andExpect(jsonPath("$.data.items[0].flashcard.material.id").value(materialA.getId()))
                .andExpect(jsonPath("$.data.items[1].whenKind").value("now"))
                .andExpect(jsonPath("$.data.items[1].overdue").value(false))
                .andExpect(jsonPath("$.data.items[2].whenKind").value("today"))
                .andExpect(jsonPath("$.data.items[3].whenKind").value("future"))
                .andExpect(jsonPath("$.data.items[3].overdue").value(false));
    }

    @Test
    @DisplayName("GET upcoming - scope=overdue는 오늘 이전(밀림)만")
    void scope_overdue() throws Exception {
        Flashcard a = basicCard(materialA);
        ReviewSchedule overdue = pending(a, TODAY.minusDays(2).atTime(4, 0));
        pending(a, TODAY.atTime(4, 0));
        pending(a, TODAY.plusDays(3).atTime(4, 0));

        mockMvc.perform(get("/api/planner/reviews/upcoming")
                        .param("scope", "overdue")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(overdue.getId()));
    }

    @Test
    @DisplayName("GET upcoming - when=today는 오늘 끝까지, when=3d는 3일 내")
    void when_window() throws Exception {
        Flashcard a = basicCard(materialA);
        pending(a, TODAY.atTime(4, 0));               // 오늘
        pending(a, TODAY.plusDays(2).atTime(4, 0));   // +2일
        pending(a, TODAY.plusDays(5).atTime(4, 0));   // +5일

        mockMvc.perform(get("/api/planner/reviews/upcoming")
                        .param("when", "today")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)));

        mockMvc.perform(get("/api/planner/reviews/upcoming")
                        .param("when", "3d")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)));
    }

    @Test
    @DisplayName("GET upcoming - type 필터: basic/order/pair(파생)")
    void type_filter() throws Exception {
        pending(basicCard(materialA), TODAY.atTime(4, 0));
        pending(orderingCard(), TODAY.atTime(4, 0));
        pending(pairCard(), TODAY.atTime(4, 0));

        mockMvc.perform(get("/api/planner/reviews/upcoming")
                        .param("type", "basic")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].cardType").value("BASIC"));

        mockMvc.perform(get("/api/planner/reviews/upcoming")
                        .param("type", "order")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].cardType").value("ORDERING"));

        mockMvc.perform(get("/api/planner/reviews/upcoming")
                        .param("type", "pair")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].cardType").value("PAIR"))
                .andExpect(jsonPath("$.data.items[0].flashcard.type").value("BASIC"))
                .andExpect(jsonPath("$.data.items[0].flashcard.siblingGroupId").value(777));
    }

    @Test
    @DisplayName("GET upcoming - materialId 필터")
    void material_filter() throws Exception {
        pending(basicCard(materialA), TODAY.atTime(4, 0));
        Flashcard b = basicCard(materialB);
        pending(b, TODAY.atTime(4, 0));

        mockMvc.perform(get("/api/planner/reviews/upcoming")
                        .param("materialId", String.valueOf(materialB.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].flashcard.material.id").value(materialB.getId()));
    }

    @Test
    @DisplayName("GET upcoming - 커서 페이징: 동일 scheduled_at은 id ASC로 tie-break, 경계 무중복")
    void keyset_pagination() throws Exception {
        Flashcard a = basicCard(materialA);
        ReviewSchedule r1 = pending(a, TODAY.atTime(4, 0));
        ReviewSchedule r2 = pending(a, TODAY.atTime(4, 0));
        ReviewSchedule r3 = pending(a, TODAY.atTime(4, 0));

        MvcResult first = mockMvc.perform(get("/api/planner/reviews/upcoming")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value(r1.getId()))
                .andExpect(jsonPath("$.data.items[1].id").value(r2.getId()))
                .andReturn();
        String cursor = JsonPath.read(first.getResponse().getContentAsString(), "$.data.nextCursor");

        mockMvc.perform(get("/api/planner/reviews/upcoming")
                        .param("size", "2")
                        .param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].id").value(r3.getId()));
    }

    @Test
    @DisplayName("GET upcoming - 타인/COMPLETED는 제외된다")
    void excludes_others_and_completed() throws Exception {
        Flashcard a = basicCard(materialA);
        pending(a, TODAY.atTime(4, 0));
        ReviewSchedule done = new ReviewSchedule(member.getId(), a.getId(), 1,
                atTestZone(TODAY.minusDays(1).atTime(4, 0)), 1, EF);
        done.complete(ds.project.orino.domain.planner.review.entity.Rating.GOOD,
                atTestZone(TODAY.atTime(9, 0)), TEST_ZONE);
        reviewScheduleRepository.save(done);

        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));
        Flashcard otherCard = flashcardRepository.save(
                new Flashcard(otherMember.getId(), otherMaterial.getId(), "Q2", "A2"));
        reviewScheduleRepository.save(new ReviewSchedule(
                otherMember.getId(), otherCard.getId(), 1, atTestZone(TODAY.atTime(4, 0)), 1, EF));

        mockMvc.perform(get("/api/planner/reviews/upcoming")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].flashcard.front").value("기본 앞면"));
    }

    @Test
    @DisplayName("GET upcoming - totalCount는 페이지 크기와 무관한 필터 기준 총 건수")
    void total_count_is_filtered_total() throws Exception {
        Flashcard a = basicCard(materialA);
        pending(a, TODAY.atTime(4, 0));
        pending(a, TODAY.atTime(5, 0));
        pending(a, TODAY.atTime(6, 0));
        pending(basicCard(materialB), TODAY.atTime(4, 0));

        mockMvc.perform(get("/api/planner/reviews/upcoming")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.totalCount").value(4));

        mockMvc.perform(get("/api/planner/reviews/upcoming")
                        .param("materialId", String.valueOf(materialB.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1));
    }

    @Test
    @DisplayName("GET upcoming - totalCount는 when/type 필터도 반영한다")
    void total_count_reflects_when_and_type() throws Exception {
        pending(basicCard(materialA), TODAY.atTime(4, 0));
        pending(orderingCard(), TODAY.atTime(4, 0));
        pending(pairCard(), TODAY.plusDays(5).atTime(4, 0));

        mockMvc.perform(get("/api/planner/reviews/upcoming")
                        .param("when", "today")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2));

        mockMvc.perform(get("/api/planner/reviews/upcoming")
                        .param("type", "pair")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1));
    }

    @Test
    @DisplayName("GET upcoming - totalCount는 첫 페이지에만 실린다")
    void total_count_only_on_first_page() throws Exception {
        Flashcard a = basicCard(materialA);
        pending(a, TODAY.atTime(4, 0));
        pending(a, TODAY.atTime(5, 0));
        pending(a, TODAY.atTime(6, 0));

        MvcResult first = mockMvc.perform(get("/api/planner/reviews/upcoming")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andReturn();
        String cursor = JsonPath.read(first.getResponse().getContentAsString(), "$.data.nextCursor");

        mockMvc.perform(get("/api/planner/reviews/upcoming")
                        .param("size", "2")
                        .param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.totalCount").doesNotExist());
    }

    @Test
    @DisplayName("GET upcoming - 잘못된 scope는 400")
    void invalid_scope_returns_400() throws Exception {
        mockMvc.perform(get("/api/planner/reviews/upcoming")
                        .param("scope", "bogus")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isBadRequest());
    }
}
