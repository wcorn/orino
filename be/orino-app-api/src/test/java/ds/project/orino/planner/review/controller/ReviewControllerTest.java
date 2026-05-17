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
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        LocalDate today = LocalDate.now(clock);
        ReviewSchedule current = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 1, today.minusDays(1), 1,
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
                .andExpect(jsonPath("$.data.nextReview.scheduledDate").value(today.plusDays(6).toString()))
                .andExpect(jsonPath("$.data.nextReview.intervalDays").value(6))
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
        LocalDate today = LocalDate.now(clock);
        ReviewSchedule current = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 2, today, 6,
                new BigDecimal("2.50")));

        mockMvc.perform(post("/api/planner/reviews/{id}/complete", current.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"AGAIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextReview.intervalDays").value(1))
                .andExpect(jsonPath("$.data.nextReview.scheduledDate").value(today.plusDays(1).toString()))
                .andExpect(jsonPath("$.data.nextReview.easeFactor").value(2.30));
    }

    @Test
    @DisplayName("POST complete - EASY 평가 시 ease +0.10")
    void complete_easy() throws Exception {
        LocalDate today = LocalDate.now(clock);
        ReviewSchedule current = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 2, today, 6,
                new BigDecimal("2.50")));

        mockMvc.perform(post("/api/planner/reviews/{id}/complete", current.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"EASY"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextReview.easeFactor").value(2.60));
    }

    @Test
    @DisplayName("POST complete - 이미 COMPLETED 인 경우 409 SP-ERR-003")
    void complete_already_completed_returns_409() throws Exception {
        LocalDate today = LocalDate.now(clock);
        ReviewSchedule current = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 1, today.minusDays(1), 1,
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
        LocalDate today = LocalDate.now(clock);
        ReviewSchedule otherReview = reviewScheduleRepository.save(new ReviewSchedule(
                otherMember.getId(), otherCard.getId(), 1, today, 1,
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
        LocalDate today = LocalDate.now(clock);
        ReviewSchedule current = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 1, today, 1,
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
        LocalDate today = LocalDate.now(clock);
        ReviewSchedule earlyReview = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 1, today.plusDays(2), 1,
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
        LocalDate today = LocalDate.now(clock);
        ReviewSchedule firstReview = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 1, today, 1,
                new BigDecimal("2.50")));

        Long currentId = firstReview.getId();
        int[] expectedIntervals = {6, 15, 38, 95};

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
                    .andExpect(jsonPath("$.data.nextReview.scheduledDate")
                            .value(today.plusDays(expectedInterval).toString()))
                    .andReturn().getResponse().getContentAsString();
            Number nextId = com.jayway.jsonpath.JsonPath.read(body, "$.data.nextReview.id");
            currentId = nextId.longValue();
        }
    }
}
