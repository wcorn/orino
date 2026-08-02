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
        LocalDate today = testToday(clock);

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.today").value(today.toString()))
                .andExpect(jsonPath("$.data.reviews", hasSize(0)));
    }

    @Test
    @DisplayName("GET today - scheduledDate <= today AND status=PENDING 만 반환, 정렬 ASC")
    void returns_pending_due_today_or_overdue_sorted() throws Exception {
        LocalDate today = testToday(clock);
        ReviewSchedule overdue = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 2, atTestZone(today.minusDays(2).atTime(4, 0)), 6,
                new BigDecimal("2.50")));
        ReviewSchedule dueToday = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 3, atTestZone(today.atStartOfDay()), 15,
                new BigDecimal("2.50")));
        reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 4, atTestZone(today.plusDays(3).atTime(4, 0)), 3,
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
        LocalDate today = testToday(clock);
        reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 1, atTestZone(today.atStartOfDay()), 1,
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
    @DisplayName("GET today - preview 4지가 등급별로 갈린다 (직전 6일·ease 2.50, 제때)")
    void preview_differs_per_rating() throws Exception {
        LocalDate today = testToday(clock);
        reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 2, atTestZone(today.atStartOfDay()), 6,
                new BigDecimal("2.50")));

        // #1001 회귀: 예전엔 hard/good/easy가 모두 15로 같아 채점이 무의미했다.
        // hard=max(round(6×1.2),7)=7 · good=max(round(6×2.5),8)=15 · easy=max(round(6×2.5×1.3),16)=20
        mockMvc.perform(get("/api/planner/reviews/today")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews[0].preview.again").value(1))
                .andExpect(jsonPath("$.data.reviews[0].preview.hard").value(7))
                .andExpect(jsonPath("$.data.reviews[0].preview.good").value(15))
                .andExpect(jsonPath("$.data.reviews[0].preview.easy").value(20));
    }

    @Test
    @DisplayName("GET today - 밀린 복습은 preview에 밀린 일수 보너스가 반영된다")
    void preview_includes_days_late_bonus() throws Exception {
        LocalDate today = testToday(clock);
        // 10일 간격으로 잡혔는데 8일 밀렸다 → good=(10+8/2)×2.5=35 · easy=(10+8)×2.5×1.3=58.5→59
        reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 3, atTestZone(today.minusDays(8).atTime(4, 0)), 10,
                new BigDecimal("2.50")));

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews[0].delayDays").value(8))
                .andExpect(jsonPath("$.data.reviews[0].preview.hard").value(12))
                .andExpect(jsonPath("$.data.reviews[0].preview.good").value(35))
                .andExpect(jsonPath("$.data.reviews[0].preview.easy").value(59));
    }

    @Test
    @DisplayName("GET today - 타인 review는 제외된다")
    void excludes_other_members_reviews() throws Exception {
        LocalDate today = testToday(clock);
        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));
        Flashcard otherCard = flashcardRepository.save(
                new Flashcard(otherMember.getId(), otherMaterial.getId(), "Q2", "A2"));
        reviewScheduleRepository.save(new ReviewSchedule(
                otherMember.getId(), otherCard.getId(), 1, atTestZone(today.atStartOfDay()), 1,
                new BigDecimal("2.50")));
        reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 1, atTestZone(today.atStartOfDay()), 1,
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
        LocalDate today = testToday(clock);
        ReviewSchedule pending = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 2, atTestZone(today.atStartOfDay()), 6,
                new BigDecimal("2.50")));
        ReviewSchedule completed = new ReviewSchedule(
                member.getId(), card.getId(), 1, atTestZone(today.minusDays(5).atTime(4, 0)), 1,
                new BigDecimal("2.50"));
        completed.complete(ds.project.orino.domain.planner.review.entity.Rating.GOOD, clock.instant(), TEST_ZONE);
        reviewScheduleRepository.save(completed);

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews", hasSize(1)))
                .andExpect(jsonPath("$.data.reviews[0].id").value(pending.getId()));
    }

    @Test
    @DisplayName("GET today - 순서 카드는 type/items를 포함하고 back은 생략된다")
    void embeds_ordering_card_with_items() throws Exception {
        LocalDate today = testToday(clock);
        Flashcard ordering = flashcardRepository.save(Flashcard.ordering(
                member.getId(), material.getId(), "순서대로 배열",
                "[{\"id\":\"a\",\"text\":\"1단계\"},{\"id\":\"b\",\"text\":\"2단계\"},{\"id\":\"c\",\"text\":\"3단계\"}]"));
        reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), ordering.getId(), 1, atTestZone(today.atStartOfDay()), 1,
                new BigDecimal("2.50")));

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews[0].flashcard.type").value("ORDERING"))
                .andExpect(jsonPath("$.data.reviews[0].flashcard.front").value("순서대로 배열"))
                .andExpect(jsonPath("$.data.reviews[0].flashcard.back").doesNotExist())
                .andExpect(jsonPath("$.data.reviews[0].flashcard.items", hasSize(3)))
                .andExpect(jsonPath("$.data.reviews[0].flashcard.items[0].text").value("1단계"));
    }
}
