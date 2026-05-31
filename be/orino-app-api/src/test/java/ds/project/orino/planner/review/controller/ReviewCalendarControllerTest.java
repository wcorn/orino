package ds.project.orino.planner.review.controller;

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
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewCalendarControllerTest extends ApiTestSupport {

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
    private JdbcTemplate jdbcTemplate;

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
                new StudyMaterial(member.getId(), "이펙티브 자바", MaterialType.BOOK));
        card = flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q", "A"));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    private ReviewSchedule pending(LocalDate date, int sequence) {
        return reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), sequence, date.atTime(4, 0), 6, new BigDecimal("2.50")));
    }

    private ReviewSchedule completed(LocalDate date, int sequence, Rating rating) {
        ReviewSchedule r = new ReviewSchedule(
                member.getId(), card.getId(), sequence, date.atTime(4, 0), 6, new BigDecimal("2.50"));
        r.complete(rating, java.time.LocalDateTime.of(date, java.time.LocalTime.NOON));
        return reviewScheduleRepository.save(r);
    }

    @Test
    @DisplayName("GET calendar - 기간 내 복습이 없으면 빈 배열, from/to는 채워짐")
    void empty() throws Exception {
        mockMvc.perform(get("/api/planner/reviews/calendar")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value("2026-05-01"))
                .andExpect(jsonPath("$.data.to").value("2026-05-31"))
                .andExpect(jsonPath("$.data.reviews", hasSize(0)));
    }

    @Test
    @DisplayName("GET calendar - PENDING + COMPLETED 모두 포함, scheduledDate asc 정렬")
    void includes_pending_and_completed_sorted() throws Exception {
        completed(LocalDate.parse("2026-05-10"), 1, Rating.GOOD);
        pending(LocalDate.parse("2026-05-20"), 2);

        mockMvc.perform(get("/api/planner/reviews/calendar")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews", hasSize(2)))
                .andExpect(jsonPath("$.data.reviews[0].scheduledAt", startsWith("2026-05-10T")))
                .andExpect(jsonPath("$.data.reviews[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.reviews[0].rating").value("GOOD"))
                .andExpect(jsonPath("$.data.reviews[1].scheduledAt", startsWith("2026-05-20T")))
                .andExpect(jsonPath("$.data.reviews[1].status").value("PENDING"))
                .andExpect(jsonPath("$.data.reviews[1].rating").doesNotExist());
    }

    @Test
    @DisplayName("GET calendar - 범위 밖 항목은 제외 (경계 inclusive)")
    void range_boundary_inclusive() throws Exception {
        pending(LocalDate.parse("2026-04-30"), 1); // 범위 밖
        pending(LocalDate.parse("2026-05-01"), 1); // from 경계 포함
        pending(LocalDate.parse("2026-05-31"), 1); // to 경계 포함
        pending(LocalDate.parse("2026-06-01"), 1); // 범위 밖

        mockMvc.perform(get("/api/planner/reviews/calendar")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews", hasSize(2)))
                .andExpect(jsonPath("$.data.reviews[0].scheduledAt", startsWith("2026-05-01T")))
                .andExpect(jsonPath("$.data.reviews[1].scheduledAt", startsWith("2026-05-31T")));
    }

    @Test
    @DisplayName("GET calendar - flashcard + material 동봉, back은 제외")
    void embeds_flashcard_material_without_back() throws Exception {
        pending(LocalDate.parse("2026-05-15"), 2);

        mockMvc.perform(get("/api/planner/reviews/calendar")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews[0].flashcard.id").value(card.getId()))
                .andExpect(jsonPath("$.data.reviews[0].flashcard.front").value("Q"))
                .andExpect(jsonPath("$.data.reviews[0].flashcard.back").doesNotExist())
                .andExpect(jsonPath("$.data.reviews[0].flashcard.material.title").value("이펙티브 자바"))
                .andExpect(jsonPath("$.data.reviews[0].sequence").value(2));
    }

    @Test
    @DisplayName("GET calendar - 타인 review는 제외")
    void excludes_other_members() throws Exception {
        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));
        Flashcard otherCard = flashcardRepository.save(
                new Flashcard(otherMember.getId(), otherMaterial.getId(), "Q2", "A2"));
        reviewScheduleRepository.save(new ReviewSchedule(
                otherMember.getId(), otherCard.getId(), 1, LocalDate.parse("2026-05-15").atTime(4, 0), 6,
                new BigDecimal("2.50")));
        pending(LocalDate.parse("2026-05-16"), 1);

        mockMvc.perform(get("/api/planner/reviews/calendar")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews", hasSize(1)))
                .andExpect(jsonPath("$.data.reviews[0].scheduledAt", startsWith("2026-05-16T")));
    }

    @Test
    @DisplayName("GET calendar - to < from 이면 400")
    void reversed_range_400() throws Exception {
        mockMvc.perform(get("/api/planner/reviews/calendar")
                        .queryParam("from", "2026-05-31")
                        .queryParam("to", "2026-05-01")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }

    @Test
    @DisplayName("GET calendar - 범위가 100일 초과면 400")
    void too_wide_range_400() throws Exception {
        mockMvc.perform(get("/api/planner/reviews/calendar")
                        .queryParam("from", "2026-01-01")
                        .queryParam("to", "2026-12-31")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }

    @Test
    @DisplayName("GET calendar - from 누락 시 400")
    void missing_from_400() throws Exception {
        mockMvc.perform(get("/api/planner/reviews/calendar")
                        .queryParam("to", "2026-05-31")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isBadRequest());
    }
}
