package ds.project.orino.planner.review.controller;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.repository.FlashcardRepository;
import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.Rating;
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
class ReviewCompletedControllerTest extends ApiTestSupport {

    private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);
    private static final BigDecimal EF = new BigDecimal("2.50");

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
        return flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "앞면", "뒷면"));
    }

    private ReviewSchedule completed(Flashcard c, int sequence, Rating rating, LocalDateTime completedLocal) {
        ReviewSchedule r = new ReviewSchedule(member.getId(), c.getId(), sequence,
                atTestZone(TODAY.minusDays(sequence).atTime(4, 0)), 1, EF);
        r.complete(rating, atTestZone(completedLocal), TEST_ZONE);
        return reviewScheduleRepository.save(r);
    }

    @Test
    @DisplayName("GET completed - completed_at DESC 정렬 + rating/sequence/cardType/flashcard 동봉")
    void descending_with_fields() throws Exception {
        Flashcard a = basicCard(materialA);
        ReviewSchedule oldest = completed(a, 1, Rating.HARD, TODAY.atTime(8, 0));
        ReviewSchedule newest = completed(a, 2, Rating.GOOD, TODAY.atTime(10, 0));

        mockMvc.perform(get("/api/planner/reviews/completed")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.items[0].id").value(newest.getId()))
                .andExpect(jsonPath("$.data.items[0].rating").value("GOOD"))
                .andExpect(jsonPath("$.data.items[0].sequence").value(2))
                .andExpect(jsonPath("$.data.items[0].cardType").value("BASIC"))
                .andExpect(jsonPath("$.data.items[0].flashcard.front").value("앞면"))
                .andExpect(jsonPath("$.data.items[0].flashcard.material.id").value(materialA.getId()))
                .andExpect(jsonPath("$.data.items[1].id").value(oldest.getId()))
                .andExpect(jsonPath("$.data.items[1].rating").value("HARD"));
    }

    @Test
    @DisplayName("GET completed - grade 필터")
    void grade_filter() throws Exception {
        Flashcard a = basicCard(materialA);
        completed(a, 1, Rating.AGAIN, TODAY.atTime(8, 0));
        ReviewSchedule good = completed(a, 2, Rating.GOOD, TODAY.atTime(10, 0));

        mockMvc.perform(get("/api/planner/reviews/completed")
                        .param("grade", "GOOD")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(good.getId()))
                .andExpect(jsonPath("$.data.items[0].rating").value("GOOD"));
    }

    @Test
    @DisplayName("GET completed - materialId 필터")
    void material_filter() throws Exception {
        completed(basicCard(materialA), 1, Rating.GOOD, TODAY.atTime(8, 0));
        ReviewSchedule b = completed(basicCard(materialB), 1, Rating.GOOD, TODAY.atTime(9, 0));

        mockMvc.perform(get("/api/planner/reviews/completed")
                        .param("materialId", String.valueOf(materialB.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(b.getId()));
    }

    @Test
    @DisplayName("GET completed - 커서 페이징: 동일 completed_at은 id DESC로 tie-break, 경계 무중복")
    void keyset_pagination() throws Exception {
        Flashcard a = basicCard(materialA);
        // 동일 completed_at → id DESC tie-break. r3(최대 id)가 먼저.
        ReviewSchedule r1 = completed(a, 1, Rating.GOOD, TODAY.atTime(9, 0));
        ReviewSchedule r2 = completed(a, 2, Rating.GOOD, TODAY.atTime(9, 0));
        ReviewSchedule r3 = completed(a, 3, Rating.GOOD, TODAY.atTime(9, 0));

        MvcResult first = mockMvc.perform(get("/api/planner/reviews/completed")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value(r3.getId()))
                .andExpect(jsonPath("$.data.items[1].id").value(r2.getId()))
                .andReturn();
        String cursor = JsonPath.read(first.getResponse().getContentAsString(), "$.data.nextCursor");

        mockMvc.perform(get("/api/planner/reviews/completed")
                        .param("size", "2")
                        .param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].id").value(r1.getId()));
    }

    @Test
    @DisplayName("GET completed - totalCount는 필터 기준 총 건수이며 첫 페이지에만 실린다")
    void total_count_is_filtered_total_on_first_page() throws Exception {
        Flashcard a = basicCard(materialA);
        completed(a, 1, Rating.GOOD, TODAY.atTime(8, 0));
        completed(a, 2, Rating.GOOD, TODAY.atTime(9, 0));
        completed(a, 3, Rating.AGAIN, TODAY.atTime(10, 0));
        completed(basicCard(materialB), 1, Rating.GOOD, TODAY.atTime(11, 0));

        MvcResult first = mockMvc.perform(get("/api/planner/reviews/completed")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.totalCount").value(4))
                .andReturn();
        String cursor = JsonPath.read(first.getResponse().getContentAsString(), "$.data.nextCursor");

        mockMvc.perform(get("/api/planner/reviews/completed")
                        .param("size", "2")
                        .param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").doesNotExist());

        mockMvc.perform(get("/api/planner/reviews/completed")
                        .param("grade", "GOOD")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3));

        mockMvc.perform(get("/api/planner/reviews/completed")
                        .param("materialId", String.valueOf(materialB.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1));
    }

    @Test
    @DisplayName("GET completed - PENDING/타인은 제외된다")
    void excludes_pending_and_others() throws Exception {
        Flashcard a = basicCard(materialA);
        completed(a, 1, Rating.GOOD, TODAY.atTime(9, 0));
        reviewScheduleRepository.save(new ReviewSchedule(   // PENDING
                member.getId(), a.getId(), 2, atTestZone(TODAY.atTime(4, 0)), 1, EF));

        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));
        Flashcard otherCard = flashcardRepository.save(
                new Flashcard(otherMember.getId(), otherMaterial.getId(), "Q2", "A2"));
        ReviewSchedule otherDone = new ReviewSchedule(otherMember.getId(), otherCard.getId(), 1,
                atTestZone(TODAY.minusDays(1).atTime(4, 0)), 1, EF);
        otherDone.complete(Rating.GOOD, atTestZone(TODAY.atTime(9, 0)), TEST_ZONE);
        reviewScheduleRepository.save(otherDone);

        mockMvc.perform(get("/api/planner/reviews/completed")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].flashcard.front").value("앞면"));
    }
}
