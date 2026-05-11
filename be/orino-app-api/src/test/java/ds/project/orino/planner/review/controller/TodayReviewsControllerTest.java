package ds.project.orino.planner.review.controller;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.domain.planner.unit.entity.StudyUnit;
import ds.project.orino.domain.planner.unit.repository.StudyUnitRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.StudyMaterialFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TodayReviewsControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudyMaterialRepository studyMaterialRepository;

    @Autowired
    private StudyUnitRepository studyUnitRepository;

    @Autowired
    private ReviewScheduleRepository reviewScheduleRepository;

    private Member member;
    private String accessToken;
    private StudyMaterial material;
    private StudyUnit unit;

    @BeforeEach
    void setUp() throws Exception {
        reviewScheduleRepository.deleteAll();
        studyUnitRepository.deleteAll();
        studyMaterialRepository.deleteAll();
        memberRepository.deleteAll();

        member = memberRepository.save(MemberFixture.create());
        accessToken = AuthFixture.loginAndGetAccessToken(mockMvc);
        material = studyMaterialRepository.save(StudyMaterialFixture.create(member.getId()));
        unit = studyUnitRepository.save(StudyMaterialFixture.createUnit(member.getId(), material.getId(), 1));
    }

    @Test
    @DisplayName("GET /api/planner/reviews/today - 복습이 없으면 빈 배열 + today 반환")
    void today_empty() throws Exception {
        mockMvc.perform(get("/api/planner/reviews/today")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.today").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.data.reviews.length()").value(0));
    }

    @Test
    @DisplayName("미래 예정 복습은 응답에 포함되지 않는다")
    void today_excludesFutureReviews() throws Exception {
        reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), unit.getId(), 2,
                LocalDate.now().plusDays(5), 6, new BigDecimal("2.50")));

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews.length()").value(0));
    }

    @Test
    @DisplayName("오늘 예정 복습은 delayDays=0으로 응답에 포함된다")
    void today_includesToday() throws Exception {
        reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), unit.getId(), 2,
                LocalDate.now(), 6, new BigDecimal("2.50")));

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews.length()").value(1))
                .andExpect(jsonPath("$.data.reviews[0].delayDays").value(0))
                .andExpect(jsonPath("$.data.reviews[0].sequence").value(2))
                .andExpect(jsonPath("$.data.reviews[0].unit.id").value(unit.getId()))
                .andExpect(jsonPath("$.data.reviews[0].unit.title").value(unit.getTitle()))
                .andExpect(jsonPath("$.data.reviews[0].unit.material.id").value(material.getId()))
                .andExpect(jsonPath("$.data.reviews[0].unit.material.title").value(material.getTitle()))
                .andExpect(jsonPath("$.data.reviews[0].unit.material.type").value(material.getType().name()));
    }

    @Test
    @DisplayName("밀린 복습(scheduled_date < today)도 포함, delayDays는 (today - scheduled_date)")
    void today_includesOverdueWithDelay() throws Exception {
        reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), unit.getId(), 2,
                LocalDate.now().minusDays(3), 6, new BigDecimal("2.50")));

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews[0].delayDays").value(3));
    }

    @Test
    @DisplayName("COMPLETED 상태 복습은 응답에 포함되지 않는다")
    void today_excludesCompleted() throws Exception {
        ReviewSchedule done = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), unit.getId(), 1,
                LocalDate.now(), 1, new BigDecimal("2.50")));
        markCompleted(done);

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews.length()").value(0));
    }

    @Test
    @DisplayName("정렬: scheduled_date ASC, id ASC")
    void today_orderedByDateThenId() throws Exception {
        ReviewSchedule r1 = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), unit.getId(), 1,
                LocalDate.now(), 1, new BigDecimal("2.50")));
        ReviewSchedule r2 = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), unit.getId(), 2,
                LocalDate.now().minusDays(2), 6, new BigDecimal("2.50")));
        ReviewSchedule r3 = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), unit.getId(), 3,
                LocalDate.now(), 1, new BigDecimal("2.50")));

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews[0].id").value(r2.getId()))
                .andExpect(jsonPath("$.data.reviews[1].id").value(r1.getId()))
                .andExpect(jsonPath("$.data.reviews[2].id").value(r3.getId()));
    }

    @Test
    @DisplayName("preview는 SM-2 4가지 평가의 next_interval을 담는다")
    void today_previewMatchesSm2() throws Exception {
        // sequence=2, interval=6, ease=2.50 → seq=3 계산:
        // AGAIN: 1, HARD/GOOD/EASY: round(6 × 2.50) = 15
        reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), unit.getId(), 2,
                LocalDate.now(), 6, new BigDecimal("2.50")));

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews[0].preview.again").value(1))
                .andExpect(jsonPath("$.data.reviews[0].preview.hard").value(15))
                .andExpect(jsonPath("$.data.reviews[0].preview.good").value(15))
                .andExpect(jsonPath("$.data.reviews[0].preview.easy").value(15));
    }

    @Test
    @DisplayName("preview는 sequence=1일 때 next interval=6 (HARD/GOOD/EASY) / 1 (AGAIN)")
    void today_previewForFirstReview() throws Exception {
        reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), unit.getId(), 1,
                LocalDate.now(), 1, new BigDecimal("2.50")));

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews[0].preview.again").value(1))
                .andExpect(jsonPath("$.data.reviews[0].preview.hard").value(6))
                .andExpect(jsonPath("$.data.reviews[0].preview.good").value(6))
                .andExpect(jsonPath("$.data.reviews[0].preview.easy").value(6));
    }

    @Test
    @DisplayName("다른 멤버의 복습은 응답에 포함되지 않는다")
    void today_otherMembersExcluded() throws Exception {
        Member another = memberRepository.save(MemberFixture.create("other", "password"));
        StudyMaterial othersMaterial = studyMaterialRepository.save(StudyMaterialFixture.create(another.getId()));
        StudyUnit othersUnit = studyUnitRepository.save(
                StudyMaterialFixture.createUnit(another.getId(), othersMaterial.getId(), 1));
        reviewScheduleRepository.save(new ReviewSchedule(
                another.getId(), othersUnit.getId(), 1,
                LocalDate.now(), 1, new BigDecimal("2.50")));

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews.length()").value(0));
    }

    @Test
    @DisplayName("인증 없이 호출하면 403")
    void today_noAuth() throws Exception {
        mockMvc.perform(get("/api/planner/reviews/today"))
                .andExpect(status().isForbidden());
    }

    private void markCompleted(ReviewSchedule review) {
        try {
            Field f = ReviewSchedule.class.getDeclaredField("status");
            f.setAccessible(true);
            f.set(review, ReviewStatus.COMPLETED);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        reviewScheduleRepository.save(review);
    }
}
