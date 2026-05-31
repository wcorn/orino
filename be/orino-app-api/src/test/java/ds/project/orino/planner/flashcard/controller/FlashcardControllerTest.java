package ds.project.orino.planner.flashcard.controller;

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
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FlashcardControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudyMaterialRepository studyMaterialRepository;

    @Autowired
    private FlashcardRepository flashcardRepository;

    @Autowired
    private ReviewScheduleRepository reviewScheduleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DbCleaner dbCleaner;

    @Autowired
    private Clock clock;

    private Member member;
    private Member otherMember;
    private StudyMaterial material;
    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        member = memberRepository.save(MemberFixture.create());
        otherMember = memberRepository.save(MemberFixture.create("other", "password"));
        material = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "자료", MaterialType.BOOK));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    @Test
    @DisplayName("POST - 카드 생성 + 첫 복습(sequence=1, today+1, 1일, 2.50) 자동 생성")
    void create_with_first_review() throws Exception {
        LocalDate today = LocalDate.now(clock);

        mockMvc.perform(post("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"front":"Q","back":"A"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.flashcard.front").value("Q"))
                .andExpect(jsonPath("$.data.flashcard.back").value("A"))
                .andExpect(jsonPath("$.data.flashcard.materialId").value(material.getId()))
                .andExpect(jsonPath("$.data.firstReview.sequence").value(1))
                .andExpect(jsonPath("$.data.firstReview.intervalDays").value(1))
                .andExpect(jsonPath("$.data.firstReview.easeFactor").value(2.50))
                .andExpect(jsonPath("$.data.firstReview.status").value("PENDING"))
                .andExpect(jsonPath("$.data.firstReview.scheduledAt",
                        startsWith(today.plusDays(1) + "T04:00")));
    }

    @Test
    @DisplayName("POST - front 빈 문자열이면 400")
    void create_blank_front() throws Exception {
        mockMvc.perform(post("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"front":"","back":"A"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST - front 1001자면 400")
    void create_too_long_front() throws Exception {
        String tooLong = "a".repeat(1001);
        mockMvc.perform(post("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"front\":\"" + tooLong + "\",\"back\":\"A\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST - 타인 자료에 카드 생성 시 404")
    void create_on_other_material_returns_404() throws Exception {
        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));

        mockMvc.perform(post("/api/planner/materials/{id}/flashcards", otherMaterial.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"front":"Q","back":"A"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET - 카드 목록은 생성순으로 정렬되고 nextReview 포함")
    void list_returns_cards_in_creation_order_with_next_review() throws Exception {
        Flashcard card1 = flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q1", "A1"));
        Flashcard card2 = flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q2", "A2"));
        LocalDate today = LocalDate.now(clock);
        reviewScheduleRepository.save(
                new ReviewSchedule(member.getId(), card1.getId(), 1, today.plusDays(1).atTime(4, 0), 1,
                        new BigDecimal("2.50")));
        reviewScheduleRepository.save(
                new ReviewSchedule(member.getId(), card2.getId(), 1, today.plusDays(3).atTime(4, 0), 1,
                        new BigDecimal("2.50")));

        mockMvc.perform(get("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flashcards", hasSize(2)))
                .andExpect(jsonPath("$.data.flashcards[0].id").value(card1.getId()))
                .andExpect(jsonPath("$.data.flashcards[0].nextReview.sequence").value(1))
                .andExpect(jsonPath("$.data.flashcards[0].nextReview.scheduledAt",
                        startsWith(today.plusDays(1) + "T04:00")))
                .andExpect(jsonPath("$.data.flashcards[1].id").value(card2.getId()))
                .andExpect(jsonPath("$.data.flashcards[1].nextReview.scheduledAt",
                        startsWith(today.plusDays(3) + "T04:00")));
    }

    @Test
    @DisplayName("GET - 가장 가까운 PENDING이 nextReview로 선택된다 (COMPLETED 무시)")
    void list_next_review_picks_earliest_pending() throws Exception {
        Flashcard card = flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q", "A"));
        LocalDate today = LocalDate.now(clock);
        jdbcTemplate.update("""
                INSERT INTO review_schedule
                  (member_id, flashcard_id, sequence, scheduled_at, interval_days,
                   ease_factor, status, completed_at, created_at, updated_at)
                VALUES (?, ?, 1, ?, 1, 2.50, 'COMPLETED', NOW(6), NOW(6), NOW(6))
                """, member.getId(), card.getId(), today.minusDays(5).atStartOfDay());
        reviewScheduleRepository.save(
                new ReviewSchedule(member.getId(), card.getId(), 3, today.plusDays(10).atTime(4, 0), 10,
                        new BigDecimal("2.50")));
        reviewScheduleRepository.save(
                new ReviewSchedule(member.getId(), card.getId(), 2, today.plusDays(2).atTime(4, 0), 2,
                        new BigDecimal("2.50")));

        mockMvc.perform(get("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flashcards[0].nextReview.sequence").value(2))
                .andExpect(jsonPath("$.data.flashcards[0].nextReview.scheduledAt",
                        startsWith(today.plusDays(2) + "T04:00")));
    }

    @Test
    @DisplayName("GET - PENDING 복습이 없는 카드는 nextReview가 null/omit")
    void list_card_without_pending_review() throws Exception {
        flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q", "A"));

        mockMvc.perform(get("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flashcards[0].nextReview").doesNotExist());
    }

    @Test
    @DisplayName("GET - 타인 자료 카드 목록 조회 시 404")
    void list_other_material_returns_404() throws Exception {
        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));

        mockMvc.perform(get("/api/planner/materials/{id}/flashcards", otherMaterial.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH - front/back 부분 수정, 복습 일정에 영향 없음")
    void update_does_not_affect_reviews() throws Exception {
        Flashcard card = flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q", "A"));
        LocalDate today = LocalDate.now(clock);
        ReviewSchedule review = reviewScheduleRepository.save(
                new ReviewSchedule(member.getId(), card.getId(), 1, today.plusDays(1).atTime(4, 0), 1,
                        new BigDecimal("2.50")));

        mockMvc.perform(patch("/api/planner/flashcards/{id}", card.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"front":"Q2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.front").value("Q2"))
                .andExpect(jsonPath("$.data.back").value("A"));

        ReviewSchedule unchanged = reviewScheduleRepository.findById(review.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(unchanged.getScheduledAt())
                .isEqualTo(today.plusDays(1).atTime(4, 0));
        org.assertj.core.api.Assertions.assertThat(unchanged.getSequence()).isEqualTo(1);
    }

    @Test
    @DisplayName("PATCH - 본문이 모두 null이면 400")
    void update_empty_body_returns_400() throws Exception {
        Flashcard card = flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q", "A"));

        mockMvc.perform(patch("/api/planner/flashcards/{id}", card.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH - 타인 카드 수정 시 404")
    void update_other_card_returns_404() throws Exception {
        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));
        Flashcard otherCard = flashcardRepository.save(
                new Flashcard(otherMember.getId(), otherMaterial.getId(), "Q", "A"));

        mockMvc.perform(patch("/api/planner/flashcards/{id}", otherCard.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"front":"X"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE - 카드 삭제 시 관련 review_schedule이 cascade 삭제된다")
    void delete_cascades_reviews() throws Exception {
        Flashcard card = flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q", "A"));
        LocalDate today = LocalDate.now(clock);
        reviewScheduleRepository.save(
                new ReviewSchedule(member.getId(), card.getId(), 1, today.plusDays(1).atTime(4, 0), 1,
                        new BigDecimal("2.50")));
        reviewScheduleRepository.save(
                new ReviewSchedule(member.getId(), card.getId(), 2, today.plusDays(7).atTime(4, 0), 7,
                        new BigDecimal("2.50")));

        mockMvc.perform(delete("/api/planner/flashcards/{id}", card.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNoContent());

        Integer cardCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flashcard WHERE id = ?", Integer.class, card.getId());
        Integer reviewCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_schedule WHERE flashcard_id = ?", Integer.class, card.getId());

        org.assertj.core.api.Assertions.assertThat(cardCount).isZero();
        org.assertj.core.api.Assertions.assertThat(reviewCount).isZero();
    }

    @Test
    @DisplayName("DELETE - 타인 카드 삭제 시 404")
    void delete_other_card_returns_404() throws Exception {
        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));
        Flashcard otherCard = flashcardRepository.save(
                new Flashcard(otherMember.getId(), otherMaterial.getId(), "Q", "A"));

        mockMvc.perform(delete("/api/planner/flashcards/{id}", otherCard.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound());
    }
}
