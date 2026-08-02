package ds.project.orino.planner.review.controller;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.repository.FlashcardRepository;
import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.FixedClockConfig;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(FixedClockConfig.class)
class ReviewControllerTest extends ApiTestSupport {

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

    @Autowired
    private Clock clock;

    private Member member;
    private Member otherMember;
    private Flashcard card;
    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        member = memberRepository.save(MemberFixture.create());
        otherMember = memberRepository.save(MemberFixture.create("other", "password"));
        StudyMaterial material = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "자료", MaterialType.BOOK));
        card = flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q", "A"));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    @Test
    @DisplayName("POST complete - GOOD 평가 시 현재 review COMPLETED + 다음 review 생성")
    void complete_good_creates_next_review() throws Exception {
        LocalDate today = testToday(clock);
        ReviewSchedule current = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 1, atTestZone(today.minusDays(1).atTime(4, 0)), 1,
                new BigDecimal("2.50")));

        mockMvc.perform(post("/api/planner/reviews/{id}/complete", current.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"GOOD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed.id").value(current.getId()))
                .andExpect(jsonPath("$.data.completed.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completed.rating").value("GOOD"))
                .andExpect(jsonPath("$.data.completed.elapsedDays").value(1))
                .andExpect(jsonPath("$.data.completed.completedAt").exists())
                .andExpect(jsonPath("$.data.nextReview.flashcardId").value(card.getId()))
                .andExpect(jsonPath("$.data.nextReview.sequence").value(2))
                // 하루 밀려서 봤다 → good = max(round((1 + 1/2) × 2.50), hard + 1) = 4일
                .andExpect(jsonPath("$.data.nextReview.scheduledAt",
                        startsWith(today.plusDays(4) + "T04:00")))
                .andExpect(jsonPath("$.data.nextReview.intervalDays").value(4))
                .andExpect(jsonPath("$.data.nextReview.easeFactor").value(2.50))
                .andExpect(jsonPath("$.data.nextReview.status").value("PENDING"));

        List<ReviewSchedule> all = reviewScheduleRepository.findAll();
        assertThat(all).hasSize(2);
        ReviewSchedule completed = all.stream()
                .filter(r -> r.getId().equals(current.getId()))
                .findFirst().orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(ReviewStatus.COMPLETED);
    }

    @Test
    @DisplayName("POST complete - AGAIN 평가 시 interval=1, ease -0.20")
    void complete_again() throws Exception {
        LocalDate today = testToday(clock);
        ReviewSchedule current = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 2, atTestZone(today.atTime(4, 0)), 6,
                new BigDecimal("2.50")));

        mockMvc.perform(post("/api/planner/reviews/{id}/complete", current.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"AGAIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextReview.intervalDays").value(1))
                // AGAIN은 당일 10분 뒤(분 단위) 재복습 → 오늘 날짜의 datetime
                .andExpect(jsonPath("$.data.nextReview.scheduledAt",
                        startsWith(today.toString() + "T")))
                .andExpect(jsonPath("$.data.nextReview.easeFactor").value(2.30));
    }

    @Test
    @DisplayName("POST complete - EASY 평가 시 ease +0.15, 간격은 직전 × ease × 1.3")
    void complete_easy() throws Exception {
        LocalDate today = testToday(clock);
        ReviewSchedule current = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 2, atTestZone(today.atTime(4, 0)), 6,
                new BigDecimal("2.50")));

        // 제때 복습 → easy = max(round(6 × 2.50 × 1.3), good + 1) = 20일. ease는 다음 회차부터 2.65.
        mockMvc.perform(post("/api/planner/reviews/{id}/complete", current.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"EASY"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextReview.intervalDays").value(20))
                .andExpect(jsonPath("$.data.nextReview.easeFactor").value(2.65));
    }

    @Test
    @DisplayName("POST complete - HARD 평가 시 ease -0.15, 간격은 직전 × 1.2 (#1001)")
    void complete_hard() throws Exception {
        LocalDate today = testToday(clock);
        ReviewSchedule current = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 2, atTestZone(today.atTime(4, 0)), 6,
                new BigDecimal("2.50")));

        // 예전엔 GOOD과 똑같은 15일이 나왔다 — 이제 round(6 × 1.2) = 7일로 갈린다.
        mockMvc.perform(post("/api/planner/reviews/{id}/complete", current.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"HARD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextReview.intervalDays").value(7))
                .andExpect(jsonPath("$.data.nextReview.scheduledAt",
                        startsWith(today.plusDays(7) + "T04:00")))
                .andExpect(jsonPath("$.data.nextReview.easeFactor").value(2.35));
    }

    @Test
    @DisplayName("POST complete - 이미 COMPLETED 인 경우 409 SP-ERR-003")
    void complete_already_completed_returns_409() throws Exception {
        LocalDate today = testToday(clock);
        ReviewSchedule current = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 1, atTestZone(today.minusDays(1).atTime(4, 0)), 1,
                new BigDecimal("2.50")));

        mockMvc.perform(post("/api/planner/reviews/{id}/complete", current.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"GOOD"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/planner/reviews/{id}/complete", current.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"GOOD"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SP-ERR-003"));
    }

    @Test
    @DisplayName("POST complete - 타인 review 평가 시 404")
    void complete_other_member_returns_404() throws Exception {
        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));
        Flashcard otherCard = flashcardRepository.save(
                new Flashcard(otherMember.getId(), otherMaterial.getId(), "Q", "A"));
        LocalDate today = testToday(clock);
        ReviewSchedule otherReview = reviewScheduleRepository.save(new ReviewSchedule(
                otherMember.getId(), otherCard.getId(), 1, atTestZone(today.atTime(4, 0)), 1,
                new BigDecimal("2.50")));

        mockMvc.perform(post("/api/planner/reviews/{id}/complete", otherReview.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"GOOD"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST complete - rating 누락 시 400")
    void complete_missing_rating_returns_400() throws Exception {
        LocalDate today = testToday(clock);
        ReviewSchedule current = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 1, atTestZone(today.atTime(4, 0)), 1,
                new BigDecimal("2.50")));

        mockMvc.perform(post("/api/planner/reviews/{id}/complete", current.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST complete - elapsedDays는 (today - scheduledDate)로 음수 가능")
    void complete_elapsed_days_can_be_negative_for_early_review() throws Exception {
        LocalDate today = testToday(clock);
        ReviewSchedule earlyReview = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 1, atTestZone(today.plusDays(2).atTime(4, 0)), 1,
                new BigDecimal("2.50")));

        mockMvc.perform(post("/api/planner/reviews/{id}/complete", earlyReview.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"GOOD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed.elapsedDays").value(-2));
    }

    @Test
    @DisplayName("4번 연속 GOOD 평가 - sequence 1→2→3→4→5, scheduledDate가 모두 today+interval로 산정")
    void complete_four_consecutive_good_evaluations() throws Exception {
        LocalDate today = testToday(clock);
        ReviewSchedule firstReview = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 1, atTestZone(today.atTime(4, 0)), 1,
                new BigDecimal("2.50")));

        Long currentId = firstReview.getId();
        // 1 → 3 → 8 → 20 → 50 (직전 × ease 2.50, hard+1 하한). 밀린 일수 없음
        int[] expectedIntervals = {3, 8, 20, 50};

        for (int i = 0; i < 4; i++) {
            int expectedInterval = expectedIntervals[i];
            int expectedSequence = i + 2;

            String body = mockMvc.perform(post("/api/planner/reviews/{id}/complete", currentId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"rating":"GOOD"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nextReview.sequence").value(expectedSequence))
                    .andExpect(jsonPath("$.data.nextReview.intervalDays").value(expectedInterval))
                    .andExpect(jsonPath("$.data.nextReview.scheduledAt",
                            startsWith(today.plusDays(expectedInterval) + "T04:00")))
                    .andReturn().getResponse().getContentAsString();
            Number nextId = com.jayway.jsonpath.JsonPath.read(body, "$.data.nextReview.id");
            currentId = nextId.longValue();
        }
    }

    @Test
    @DisplayName("POST complete - 단방향 카드는 buriedReviewIds가 빈 배열")
    void complete_solo_card_returns_empty_buried() throws Exception {
        LocalDate today = testToday(clock);
        ReviewSchedule current = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 1, atTestZone(today.atTime(4, 0)), 1,
                new BigDecimal("2.50")));

        mockMvc.perform(post("/api/planner/reviews/{id}/complete", current.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"GOOD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.buriedReviewIds", hasSize(0)));
    }

    @Test
    @DisplayName("POST complete - 짝 카드 완료 시 오늘 due인 형제 review를 내일로 밀어내고 buriedReviewIds로 반환")
    void complete_buries_due_sibling() throws Exception {
        StudyMaterial material = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "자료2", MaterialType.BOOK));
        Flashcard a = flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "정의", "설명"));
        a.assignSiblingGroup(a.getId());
        flashcardRepository.save(a);
        Flashcard b = new Flashcard(member.getId(), material.getId(), "설명", "정의");
        b.assignSiblingGroup(a.getId());
        b = flashcardRepository.save(b);

        LocalDate today = testToday(clock);
        ReviewSchedule reviewA = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), a.getId(), 1, atTestZone(today.atTime(4, 0)), 1, new BigDecimal("2.50")));
        ReviewSchedule reviewB = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), b.getId(), 1, atTestZone(today.atTime(4, 0)), 1, new BigDecimal("2.50")));

        mockMvc.perform(post("/api/planner/reviews/{id}/complete", reviewA.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"GOOD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.buriedReviewIds", hasSize(1)))
                .andExpect(jsonPath("$.data.buriedReviewIds",
                        contains(reviewB.getId().intValue())));

        // 형제 review는 내일 04:00으로 이동 (SM-2 간격/ease/status는 불변)
        ReviewSchedule buried = reviewScheduleRepository.findById(reviewB.getId()).orElseThrow();
        assertThat(buried.getScheduledAt()).isEqualTo(atTestZone(today.plusDays(1).atTime(4, 0)));
        assertThat(buried.getIntervalDays()).isEqualTo(1);
        assertThat(buried.getEaseFactor()).isEqualByComparingTo("2.50");
        assertThat(buried.getStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(buried.getSequence()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST complete - 형제 review가 미래(내일)면 밀어내지 않는다")
    void complete_does_not_bury_future_sibling() throws Exception {
        StudyMaterial material = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "자료3", MaterialType.BOOK));
        Flashcard a = flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "정의", "설명"));
        a.assignSiblingGroup(a.getId());
        flashcardRepository.save(a);
        Flashcard b = new Flashcard(member.getId(), material.getId(), "설명", "정의");
        b.assignSiblingGroup(a.getId());
        b = flashcardRepository.save(b);

        LocalDate today = testToday(clock);
        ReviewSchedule reviewA = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), a.getId(), 1, atTestZone(today.atTime(4, 0)), 1, new BigDecimal("2.50")));
        ReviewSchedule futureB = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), b.getId(), 1, atTestZone(today.plusDays(1).atTime(4, 0)), 1,
                new BigDecimal("2.50")));

        mockMvc.perform(post("/api/planner/reviews/{id}/complete", reviewA.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"GOOD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.buriedReviewIds", hasSize(0)));

        ReviewSchedule unchanged = reviewScheduleRepository.findById(futureB.getId()).orElseThrow();
        assertThat(unchanged.getScheduledAt()).isEqualTo(atTestZone(today.plusDays(1).atTime(4, 0)));
    }
}
