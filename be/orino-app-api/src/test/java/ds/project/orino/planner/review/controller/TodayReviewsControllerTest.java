package ds.project.orino.planner.review.controller;

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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TodayReviewsControllerTest extends ApiTestSupport {

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
    private StudyMaterial material;
    private Flashcard card;
    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        member = memberRepository.save(MemberFixture.create());
        otherMember = memberRepository.save(MemberFixture.create("other", "password"));
        material = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "이펙티브 자바", MaterialType.BOOK));
        card = flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q", "A"));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    @Test
    @DisplayName("GET today - PENDING이 없으면 빈 배열, today 필드는 채워짐")
    void empty_returns_today_and_empty_list() throws Exception {
        LocalDate today = LocalDate.now(clock);

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.today").value(today.toString()))
                .andExpect(jsonPath("$.data.reviews", hasSize(0)));
    }

    @Test
    @DisplayName("GET today - scheduledDate <= today AND status=PENDING 만 반환, 정렬 ASC")
    void returns_pending_due_today_or_overdue_sorted() throws Exception {
        LocalDate today = LocalDate.now(clock);
        ReviewSchedule overdue = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 2, today.minusDays(2).atTime(4, 0), 6,
                new BigDecimal("2.50")));
        ReviewSchedule dueToday = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 3, today.atStartOfDay(), 15,
                new BigDecimal("2.50")));
        reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 4, today.plusDays(3).atTime(4, 0), 3,
                new BigDecimal("2.50")));

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews", hasSize(2)))
                .andExpect(jsonPath("$.data.reviews[0].id").value(overdue.getId()))
                .andExpect(jsonPath("$.data.reviews[0].delayDays").value(2))
                .andExpect(jsonPath("$.data.reviews[1].id").value(dueToday.getId()))
                .andExpect(jsonPath("$.data.reviews[1].delayDays").value(0));
    }

    @Test
    @DisplayName("GET today - flashcard + material을 한 응답에 동봉")
    void embeds_flashcard_and_material() throws Exception {
        LocalDate today = LocalDate.now(clock);
        reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 1, today.atStartOfDay(), 1,
                new BigDecimal("2.50")));

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews[0].flashcard.id").value(card.getId()))
                .andExpect(jsonPath("$.data.reviews[0].flashcard.front").value("Q"))
                .andExpect(jsonPath("$.data.reviews[0].flashcard.back").value("A"))
                .andExpect(jsonPath("$.data.reviews[0].flashcard.material.id").value(material.getId()))
                .andExpect(jsonPath("$.data.reviews[0].flashcard.material.title").value("이펙티브 자바"))
                .andExpect(jsonPath("$.data.reviews[0].flashcard.material.type").value("BOOK"));
    }

    @Test
    @DisplayName("GET today - preview 4지가 SM-2와 일치 (sequence=2 기준)")
    void preview_matches_sm2() throws Exception {
        LocalDate today = LocalDate.now(clock);
        reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 2, today.atStartOfDay(), 6,
                new BigDecimal("2.50")));

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews[0].preview.again").value(1))
                .andExpect(jsonPath("$.data.reviews[0].preview.hard").value(15))
                .andExpect(jsonPath("$.data.reviews[0].preview.good").value(15))
                .andExpect(jsonPath("$.data.reviews[0].preview.easy").value(15));
    }

    @Test
    @DisplayName("GET today - 타인 review는 제외된다")
    void excludes_other_members_reviews() throws Exception {
        LocalDate today = LocalDate.now(clock);
        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));
        Flashcard otherCard = flashcardRepository.save(
                new Flashcard(otherMember.getId(), otherMaterial.getId(), "Q2", "A2"));
        reviewScheduleRepository.save(new ReviewSchedule(
                otherMember.getId(), otherCard.getId(), 1, today.atStartOfDay(), 1,
                new BigDecimal("2.50")));
        reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 1, today.atStartOfDay(), 1,
                new BigDecimal("2.50")));

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews", hasSize(1)))
                .andExpect(jsonPath("$.data.reviews[0].flashcard.front").value("Q"));
    }

    @Test
    @DisplayName("GET today - COMPLETED 는 제외된다")
    void excludes_completed_reviews() throws Exception {
        LocalDate today = LocalDate.now(clock);
        ReviewSchedule pending = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 2, today.atStartOfDay(), 6,
                new BigDecimal("2.50")));
        ReviewSchedule completed = new ReviewSchedule(
                member.getId(), card.getId(), 1, today.minusDays(5).atTime(4, 0), 1,
                new BigDecimal("2.50"));
        completed.complete(ds.project.orino.domain.planner.review.entity.Rating.GOOD,
                java.time.LocalDateTime.now(clock));
        reviewScheduleRepository.save(completed);

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews", hasSize(1)))
                .andExpect(jsonPath("$.data.reviews[0].id").value(pending.getId()));
    }
}
