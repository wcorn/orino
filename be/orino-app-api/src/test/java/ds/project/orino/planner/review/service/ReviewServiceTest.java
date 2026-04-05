package ds.project.orino.planner.review.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.material.entity.DeadlineMode;
import ds.project.orino.domain.material.entity.MaterialType;
import ds.project.orino.domain.material.entity.StudyMaterial;
import ds.project.orino.domain.material.entity.StudyUnit;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.review.entity.DifficultyFeedback;
import ds.project.orino.domain.review.entity.ReviewSchedule;
import ds.project.orino.domain.review.entity.ReviewStatus;
import ds.project.orino.domain.review.repository.ReviewScheduleRepository;
import ds.project.orino.planner.review.dto.SubmitFeedbackRequest;
import ds.project.orino.planner.review.dto.SubmitFeedbackResponse;
import ds.project.orino.planner.scheduling.dirty.DirtyScheduleMarker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewScheduleRepository reviewScheduleRepository;
    @Mock private ReviewFeedbackProcessor reviewFeedbackProcessor;
    @Mock private DirtyScheduleMarker dirtyScheduleMarker;

    private ReviewService reviewService;

    private Member owner;
    private StudyUnit unit;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(
                reviewScheduleRepository,
                reviewFeedbackProcessor,
                dirtyScheduleMarker);
        owner = new Member("user", "pw");
        ReflectionTestUtils.setField(owner, "id", 1L);
        StudyMaterial material = new StudyMaterial(
                owner, "자료", MaterialType.BOOK, null, null,
                null, DeadlineMode.FREE);
        ReflectionTestUtils.setField(material, "id", 10L);
        unit = new StudyUnit(material, "단원1", 1, 30, null);
        ReflectionTestUtils.setField(unit, "id", 20L);
    }

    @Test
    @DisplayName("NORMAL 피드백을 제출하면 COMPLETED로 전환되고 다음 복습 정보를 반환한다")
    void submitFeedback_normal_success() {
        ReviewSchedule review = review(1, LocalDate.of(2026, 4, 6));
        ReflectionTestUtils.setField(review, "id", 100L);
        ReviewSchedule next = review(2, LocalDate.of(2026, 4, 10));
        ReflectionTestUtils.setField(next, "id", 101L);
        given(reviewScheduleRepository.findById(100L))
                .willReturn(Optional.of(review));
        given(reviewScheduleRepository.findUpcomingByStudyUnit(
                eq(20L), eq(1), eq(ReviewStatus.PENDING)))
                .willReturn(List.of(next));

        SubmitFeedbackResponse result = reviewService.submitFeedback(
                1L, 100L,
                new SubmitFeedbackRequest(DifficultyFeedback.NORMAL));

        assertThat(result.reviewId()).isEqualTo(100L);
        assertThat(result.status()).isEqualTo(ReviewStatus.COMPLETED);
        assertThat(result.feedback())
                .isEqualTo(DifficultyFeedback.NORMAL);
        assertThat(result.nextReviewDate())
                .isEqualTo(LocalDate.of(2026, 4, 10));
        assertThat(result.nextSequence()).isEqualTo(2);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.COMPLETED);
        verify(reviewFeedbackProcessor)
                .applyFeedback(review, DifficultyFeedback.NORMAL);
        verify(dirtyScheduleMarker).markDirtyFromToday(1L);
    }

    @Test
    @DisplayName("EASY 피드백을 제출하면 processor 에 EASY 로 위임한다")
    void submitFeedback_easy_delegatesToProcessor() {
        ReviewSchedule review = review(1, LocalDate.of(2026, 4, 6));
        ReflectionTestUtils.setField(review, "id", 100L);
        given(reviewScheduleRepository.findById(100L))
                .willReturn(Optional.of(review));
        given(reviewScheduleRepository.findUpcomingByStudyUnit(
                anyLong(), anyInt(), any()))
                .willReturn(List.of());

        SubmitFeedbackResponse result = reviewService.submitFeedback(
                1L, 100L,
                new SubmitFeedbackRequest(DifficultyFeedback.EASY));

        assertThat(result.feedback())
                .isEqualTo(DifficultyFeedback.EASY);
        assertThat(result.nextReviewDate()).isNull();
        assertThat(result.nextSequence()).isNull();
        verify(reviewFeedbackProcessor)
                .applyFeedback(review, DifficultyFeedback.EASY);
    }

    @Test
    @DisplayName("HARD 피드백을 제출하면 processor 에 HARD 로 위임한다")
    void submitFeedback_hard_delegatesToProcessor() {
        ReviewSchedule review = review(1, LocalDate.of(2026, 4, 6));
        ReflectionTestUtils.setField(review, "id", 100L);
        given(reviewScheduleRepository.findById(100L))
                .willReturn(Optional.of(review));
        given(reviewScheduleRepository.findUpcomingByStudyUnit(
                anyLong(), anyInt(), any()))
                .willReturn(List.of());

        reviewService.submitFeedback(
                1L, 100L,
                new SubmitFeedbackRequest(DifficultyFeedback.HARD));

        verify(reviewFeedbackProcessor)
                .applyFeedback(review, DifficultyFeedback.HARD);
    }

    @Test
    @DisplayName("가장 가까운 scheduledDate 의 복습을 다음 복습으로 반환한다")
    void submitFeedback_returnsEarliestUpcoming() {
        ReviewSchedule review = review(1, LocalDate.of(2026, 4, 6));
        ReflectionTestUtils.setField(review, "id", 100L);
        ReviewSchedule later = review(3, LocalDate.of(2026, 4, 20));
        ReviewSchedule earlier = review(2, LocalDate.of(2026, 4, 10));
        given(reviewScheduleRepository.findById(100L))
                .willReturn(Optional.of(review));
        given(reviewScheduleRepository.findUpcomingByStudyUnit(
                eq(20L), eq(1), eq(ReviewStatus.PENDING)))
                .willReturn(List.of(later, earlier));

        SubmitFeedbackResponse result = reviewService.submitFeedback(
                1L, 100L,
                new SubmitFeedbackRequest(DifficultyFeedback.NORMAL));

        assertThat(result.nextReviewDate())
                .isEqualTo(LocalDate.of(2026, 4, 10));
        assertThat(result.nextSequence()).isEqualTo(2);
    }

    @Test
    @DisplayName("이미 COMPLETED 된 복습에 피드백을 제출하면 INVALID_STATE")
    void submitFeedback_alreadyCompleted_throws() {
        ReviewSchedule review = review(1, LocalDate.of(2026, 4, 6));
        ReflectionTestUtils.setField(review, "id", 100L);
        review.complete(DifficultyFeedback.NORMAL);
        given(reviewScheduleRepository.findById(100L))
                .willReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.submitFeedback(
                1L, 100L,
                new SubmitFeedbackRequest(DifficultyFeedback.NORMAL)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", ErrorCode.INVALID_STATE);
        verify(reviewFeedbackProcessor, never())
                .applyFeedback(any(), any());
        verify(dirtyScheduleMarker, never())
                .markDirtyFromToday(anyLong());
    }

    @Test
    @DisplayName("SKIPPED 된 복습에 피드백을 제출하면 INVALID_STATE")
    void submitFeedback_skipped_throws() {
        ReviewSchedule review = review(1, LocalDate.of(2026, 4, 6));
        ReflectionTestUtils.setField(review, "id", 100L);
        review.skip();
        given(reviewScheduleRepository.findById(100L))
                .willReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.submitFeedback(
                1L, 100L,
                new SubmitFeedbackRequest(DifficultyFeedback.NORMAL)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", ErrorCode.INVALID_STATE);
    }

    @Test
    @DisplayName("OVERDUE 복습에는 피드백을 제출할 수 있다")
    void submitFeedback_overdue_success() {
        ReviewSchedule review = review(1, LocalDate.of(2026, 4, 3));
        ReflectionTestUtils.setField(review, "id", 100L);
        review.markOverdue();
        given(reviewScheduleRepository.findById(100L))
                .willReturn(Optional.of(review));
        given(reviewScheduleRepository.findUpcomingByStudyUnit(
                anyLong(), anyInt(), any()))
                .willReturn(List.of());

        SubmitFeedbackResponse result = reviewService.submitFeedback(
                1L, 100L,
                new SubmitFeedbackRequest(DifficultyFeedback.NORMAL));

        assertThat(result.status()).isEqualTo(ReviewStatus.COMPLETED);
        verify(reviewFeedbackProcessor)
                .applyFeedback(review, DifficultyFeedback.NORMAL);
    }

    @Test
    @DisplayName("존재하지 않는 복습은 RESOURCE_NOT_FOUND")
    void submitFeedback_notFound_throws() {
        given(reviewScheduleRepository.findById(100L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.submitFeedback(
                1L, 100L,
                new SubmitFeedbackRequest(DifficultyFeedback.NORMAL)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 사용자의 복습에 피드백을 제출하면 RESOURCE_NOT_FOUND")
    void submitFeedback_notOwner_throws() {
        ReviewSchedule review = review(1, LocalDate.of(2026, 4, 6));
        ReflectionTestUtils.setField(review, "id", 100L);
        given(reviewScheduleRepository.findById(100L))
                .willReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.submitFeedback(
                999L, 100L,
                new SubmitFeedbackRequest(DifficultyFeedback.NORMAL)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", ErrorCode.RESOURCE_NOT_FOUND);
        verify(reviewFeedbackProcessor, never())
                .applyFeedback(any(), any());
    }

    @Test
    @DisplayName("다음 복습이 없으면 응답의 nextReviewDate/nextSequence 는 null")
    void submitFeedback_noUpcoming_returnsNulls() {
        ReviewSchedule review = review(5, LocalDate.of(2026, 4, 6));
        ReflectionTestUtils.setField(review, "id", 100L);
        given(reviewScheduleRepository.findById(100L))
                .willReturn(Optional.of(review));
        given(reviewScheduleRepository.findUpcomingByStudyUnit(
                eq(20L), eq(5), eq(ReviewStatus.PENDING)))
                .willReturn(List.of());

        SubmitFeedbackResponse result = reviewService.submitFeedback(
                1L, 100L,
                new SubmitFeedbackRequest(DifficultyFeedback.NORMAL));

        assertThat(result.nextReviewDate()).isNull();
        assertThat(result.nextSequence()).isNull();
    }

    private ReviewSchedule review(int sequence, LocalDate date) {
        return new ReviewSchedule(unit, sequence, date);
    }
}
