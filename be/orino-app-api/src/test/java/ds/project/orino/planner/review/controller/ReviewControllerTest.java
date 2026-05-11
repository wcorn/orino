package ds.project.orino.planner.review.controller;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.domain.planner.unit.entity.StudyUnit;
import ds.project.orino.domain.planner.unit.entity.UnitStatus;
import ds.project.orino.domain.planner.unit.repository.StudyUnitRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.StudyMaterialFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;

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
    private StudyUnitRepository studyUnitRepository;

    @Autowired
    private ReviewScheduleRepository reviewScheduleRepository;

    private Member member;
    private String accessToken;
    private StudyMaterial material;

    @BeforeEach
    void setUp() throws Exception {
        reviewScheduleRepository.deleteAll();
        studyUnitRepository.deleteAll();
        studyMaterialRepository.deleteAll();
        memberRepository.deleteAll();

        member = memberRepository.save(MemberFixture.create());
        accessToken = AuthFixture.loginAndGetAccessToken(mockMvc);
        material = studyMaterialRepository.save(StudyMaterialFixture.create(member.getId()));
    }

    @Test
    @DisplayName("POST /api/planner/units/{id}/complete - 단위 완료 + 첫 복습 생성")
    void completeUnit_createsFirstReview() throws Exception {
        StudyUnit unit = studyUnitRepository.save(
                StudyMaterialFixture.createUnit(member.getId(), material.getId(), 1));

        MvcResult result = mockMvc.perform(post("/api/planner/units/{id}/complete", unit.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unit.id").value(unit.getId()))
                .andExpect(jsonPath("$.data.unit.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.unit.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.firstReview.sequence").value(1))
                .andExpect(jsonPath("$.data.firstReview.intervalDays").value(1))
                .andExpect(jsonPath("$.data.firstReview.easeFactor").value(2.50))
                .andExpect(jsonPath("$.data.firstReview.status").value("PENDING"))
                .andExpect(jsonPath("$.data.firstReview.scheduledDate")
                        .value(LocalDate.now().plusDays(1).toString()))
                .andReturn();

        Long reviewId = ((Number) JsonPath.read(result.getResponse().getContentAsString(),
                "$.data.firstReview.id")).longValue();
        assertThat(reviewScheduleRepository.findById(reviewId)).isPresent();

        StudyUnit refreshed = studyUnitRepository.findById(unit.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(UnitStatus.COMPLETED);
        assertThat(refreshed.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("POST /api/planner/units/{id}/complete - 이미 COMPLETED 단위는 409")
    void completeUnit_alreadyCompleted_returns409() throws Exception {
        StudyUnit unit = studyUnitRepository.save(
                StudyMaterialFixture.createUnit(member.getId(), material.getId(), 1));
        markUnitCompleted(unit);

        mockMvc.perform(post("/api/planner/units/{id}/complete", unit.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SP-ERR-003"));
    }

    @Test
    @DisplayName("POST /api/planner/units/{id}/complete - 다른 멤버 단위는 404")
    void completeUnit_otherMembers_returns404() throws Exception {
        Member another = memberRepository.save(MemberFixture.create("other", "password"));
        StudyMaterial othersMaterial = studyMaterialRepository.save(
                StudyMaterialFixture.create(another.getId()));
        StudyUnit othersUnit = studyUnitRepository.save(
                StudyMaterialFixture.createUnit(another.getId(), othersMaterial.getId(), 1));

        mockMvc.perform(post("/api/planner/units/{id}/complete", othersUnit.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));
    }

    @Test
    @DisplayName("POST /api/planner/reviews/{id}/complete - GOOD 평가 → seq=2, interval=6")
    void completeReview_goodCreatesSecondReview() throws Exception {
        StudyUnit unit = studyUnitRepository.save(
                StudyMaterialFixture.createUnit(member.getId(), material.getId(), 1));
        ReviewSchedule firstReview = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), unit.getId(), 1,
                LocalDate.now().plusDays(1), 1, new BigDecimal("2.50")));

        mockMvc.perform(post("/api/planner/reviews/{id}/complete", firstReview.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating": "GOOD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed.id").value(firstReview.getId()))
                .andExpect(jsonPath("$.data.completed.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completed.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.nextReview.sequence").value(2))
                .andExpect(jsonPath("$.data.nextReview.intervalDays").value(6))
                .andExpect(jsonPath("$.data.nextReview.easeFactor").value(2.50))
                .andExpect(jsonPath("$.data.nextReview.scheduledDate")
                        .value(LocalDate.now().plusDays(6).toString()))
                .andExpect(jsonPath("$.data.nextReview.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/planner/reviews/{id}/complete - AGAIN 평가 → interval=1, ease -= 0.20")
    void completeReview_again_resetsInterval() throws Exception {
        StudyUnit unit = studyUnitRepository.save(
                StudyMaterialFixture.createUnit(member.getId(), material.getId(), 1));
        ReviewSchedule second = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), unit.getId(), 2,
                LocalDate.now(), 6, new BigDecimal("2.50")));

        mockMvc.perform(post("/api/planner/reviews/{id}/complete", second.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating": "AGAIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextReview.intervalDays").value(1))
                .andExpect(jsonPath("$.data.nextReview.easeFactor").value(2.30));
    }

    @Test
    @DisplayName("POST /api/planner/reviews/{id}/complete - 이미 COMPLETED 복습은 409")
    void completeReview_alreadyCompleted_returns409() throws Exception {
        StudyUnit unit = studyUnitRepository.save(
                StudyMaterialFixture.createUnit(member.getId(), material.getId(), 1));
        ReviewSchedule review = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), unit.getId(), 1,
                LocalDate.now(), 1, new BigDecimal("2.50")));
        markReviewCompleted(review);

        mockMvc.perform(post("/api/planner/reviews/{id}/complete", review.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating": "GOOD"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SP-ERR-003"));
    }

    @Test
    @DisplayName("POST /api/planner/reviews/{id}/complete - 다른 멤버 복습은 404")
    void completeReview_otherMembers_returns404() throws Exception {
        Member another = memberRepository.save(MemberFixture.create("other", "password"));
        StudyMaterial othersMaterial = studyMaterialRepository.save(
                StudyMaterialFixture.create(another.getId()));
        StudyUnit othersUnit = studyUnitRepository.save(
                StudyMaterialFixture.createUnit(another.getId(), othersMaterial.getId(), 1));
        ReviewSchedule othersReview = reviewScheduleRepository.save(new ReviewSchedule(
                another.getId(), othersUnit.getId(), 1,
                LocalDate.now(), 1, new BigDecimal("2.50")));

        mockMvc.perform(post("/api/planner/reviews/{id}/complete", othersReview.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating": "GOOD"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));
    }

    @Test
    @DisplayName("POST /api/planner/reviews/{id}/complete - rating 누락 시 400")
    void completeReview_missingRating_returns400() throws Exception {
        StudyUnit unit = studyUnitRepository.save(
                StudyMaterialFixture.createUnit(member.getId(), material.getId(), 1));
        ReviewSchedule review = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), unit.getId(), 1,
                LocalDate.now(), 1, new BigDecimal("2.50")));

        mockMvc.perform(post("/api/planner/reviews/{id}/complete", review.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("전체 흐름: 단위 완료 → 첫 복습 → GOOD 평가 → 두번째 복습 (sequence=2, interval=6)")
    void fullFlow() throws Exception {
        StudyUnit unit = studyUnitRepository.save(
                StudyMaterialFixture.createUnit(member.getId(), material.getId(), 1));

        MvcResult completeResult = mockMvc.perform(post("/api/planner/units/{id}/complete", unit.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        Long firstReviewId = ((Number) JsonPath.read(completeResult.getResponse().getContentAsString(),
                "$.data.firstReview.id")).longValue();

        mockMvc.perform(post("/api/planner/reviews/{id}/complete", firstReviewId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating": "GOOD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextReview.sequence").value(2))
                .andExpect(jsonPath("$.data.nextReview.intervalDays").value(6));
    }

    private void markUnitCompleted(StudyUnit unit) {
        try {
            Field statusField = StudyUnit.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(unit, UnitStatus.COMPLETED);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        studyUnitRepository.save(unit);
    }

    private void markReviewCompleted(ReviewSchedule review) {
        try {
            Field statusField = ReviewSchedule.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(review, ReviewStatus.COMPLETED);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        reviewScheduleRepository.save(review);
    }
}
