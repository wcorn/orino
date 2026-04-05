package ds.project.orino.planner.review.service;

import ds.project.orino.domain.material.entity.DeadlineMode;
import ds.project.orino.domain.material.entity.MaterialType;
import ds.project.orino.domain.material.entity.StudyMaterial;
import ds.project.orino.domain.material.entity.StudyUnit;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.review.entity.DifficultyFeedback;
import ds.project.orino.domain.review.entity.ReviewSchedule;
import ds.project.orino.domain.review.entity.ReviewStatus;
import ds.project.orino.domain.review.repository.ReviewScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewFeedbackProcessorTest {

    @Mock private ReviewScheduleRepository reviewScheduleRepository;
    private ReviewFeedbackProcessor processor;

    private StudyUnit unit;

    @BeforeEach
    void setUp() {
        processor = new ReviewFeedbackProcessor(reviewScheduleRepository);
        Member member = new Member("user", "pw");
        StudyMaterial material = new StudyMaterial(
                member, "자료", MaterialType.BOOK, null, null,
                null, DeadlineMode.FREE);
        ReflectionTestUtils.setField(material, "id", 10L);
        unit = new StudyUnit(material, "단원1", 1, 30, null);
        ReflectionTestUtils.setField(unit, "id", 20L);
    }

    @Test
    @DisplayName("NORMAL 피드백은 간격을 변경하지 않는다")
    void normalFeedback_doesNothing() {
        ReviewSchedule completed = review(1, LocalDate.of(2026, 4, 6));

        processor.applyFeedback(completed, DifficultyFeedback.NORMAL);

        verify(reviewScheduleRepository, never())
                .findUpcomingByStudyUnit(anyLong(), anyInt(), any());
        verify(reviewScheduleRepository, never())
                .save(any(ReviewSchedule.class));
    }

    @Test
    @DisplayName("null 피드백은 아무것도 하지 않는다")
    void nullFeedback_doesNothing() {
        ReviewSchedule completed = review(1, LocalDate.of(2026, 4, 6));

        processor.applyFeedback(completed, null);

        verify(reviewScheduleRepository, never())
                .findUpcomingByStudyUnit(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("EASY 피드백은 남은 간격을 1.5배로 늘린다")
    void easyFeedback_scalesBy1_5() {
        // baseDate=2026-04-06
        // sequence=2: 2026-04-10 (간격 4일 → 6일) → 2026-04-12
        // sequence=3: 2026-04-20 (간격 14일 → 21일) → 2026-04-27
        LocalDate baseDate = LocalDate.of(2026, 4, 6);
        ReviewSchedule completed = review(1, baseDate);
        ReviewSchedule next1 = review(2, LocalDate.of(2026, 4, 10));
        ReviewSchedule next2 = review(3, LocalDate.of(2026, 4, 20));
        given(reviewScheduleRepository.findUpcomingByStudyUnit(
                eq(20L), eq(1), eq(ReviewStatus.PENDING)))
                .willReturn(List.of(next1, next2));

        processor.applyFeedback(completed, DifficultyFeedback.EASY);

        assertThat(next1.getScheduledDate())
                .isEqualTo(LocalDate.of(2026, 4, 12));
        assertThat(next2.getScheduledDate())
                .isEqualTo(LocalDate.of(2026, 4, 27));
        verify(reviewScheduleRepository, never())
                .save(any(ReviewSchedule.class));
    }

    @Test
    @DisplayName("HARD 피드백은 간격을 0.7배로 줄이고 추가 복습을 삽입한다")
    void hardFeedback_scalesBy0_7_andInsertsExtra() {
        // baseDate=2026-04-06
        // sequence=2: 2026-04-10 (간격 4일 → ceil(2.8)=3일) → 2026-04-09
        // sequence=3: 2026-04-20 (간격 14일 → ceil(9.8)=10일) → 2026-04-16
        // 추가 복습: sequence=max+1, 2026-04-08 (baseDate + 2)
        LocalDate baseDate = LocalDate.of(2026, 4, 6);
        ReviewSchedule completed = review(1, baseDate);
        ReviewSchedule next1 = review(2, LocalDate.of(2026, 4, 10));
        ReviewSchedule next2 = review(3, LocalDate.of(2026, 4, 20));
        given(reviewScheduleRepository.findUpcomingByStudyUnit(
                eq(20L), eq(1), eq(ReviewStatus.PENDING)))
                .willReturn(new ArrayList<>(List.of(next1, next2)));
        given(reviewScheduleRepository.findMaxSequenceByStudyUnit(20L))
                .willReturn(3);

        processor.applyFeedback(completed, DifficultyFeedback.HARD);

        assertThat(next1.getScheduledDate())
                .isEqualTo(LocalDate.of(2026, 4, 9));
        assertThat(next2.getScheduledDate())
                .isEqualTo(LocalDate.of(2026, 4, 16));

        ArgumentCaptor<ReviewSchedule> captor =
                ArgumentCaptor.forClass(ReviewSchedule.class);
        verify(reviewScheduleRepository).save(captor.capture());
        ReviewSchedule extra = captor.getValue();
        assertThat(extra.getSequence()).isEqualTo(4);
        assertThat(extra.getScheduledDate())
                .isEqualTo(LocalDate.of(2026, 4, 8));
    }

    @Test
    @DisplayName("HARD 피드백 시 남은 복습이 없어도 추가 복습은 삽입된다")
    void hardFeedback_withNoUpcoming_stillInsertsExtra() {
        LocalDate baseDate = LocalDate.of(2026, 4, 6);
        ReviewSchedule completed = review(3, baseDate);
        given(reviewScheduleRepository.findUpcomingByStudyUnit(
                eq(20L), eq(3), eq(ReviewStatus.PENDING)))
                .willReturn(List.of());
        given(reviewScheduleRepository.findMaxSequenceByStudyUnit(20L))
                .willReturn(3);

        processor.applyFeedback(completed, DifficultyFeedback.HARD);

        ArgumentCaptor<ReviewSchedule> captor =
                ArgumentCaptor.forClass(ReviewSchedule.class);
        verify(reviewScheduleRepository).save(captor.capture());
        assertThat(captor.getValue().getSequence()).isEqualTo(4);
        assertThat(captor.getValue().getScheduledDate())
                .isEqualTo(LocalDate.of(2026, 4, 8));
    }

    private ReviewSchedule review(int sequence, LocalDate date) {
        return new ReviewSchedule(unit, sequence, date);
    }
}
