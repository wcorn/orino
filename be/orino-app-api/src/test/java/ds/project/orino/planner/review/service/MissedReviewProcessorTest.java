package ds.project.orino.planner.review.service;

import ds.project.orino.domain.material.entity.DeadlineMode;
import ds.project.orino.domain.material.entity.MaterialType;
import ds.project.orino.domain.material.entity.MissedPolicy;
import ds.project.orino.domain.material.entity.ReviewConfig;
import ds.project.orino.domain.material.entity.StudyMaterial;
import ds.project.orino.domain.material.entity.StudyUnit;
import ds.project.orino.domain.material.repository.ReviewConfigRepository;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.preference.entity.UserPreference;
import ds.project.orino.domain.preference.repository.UserPreferenceRepository;
import ds.project.orino.domain.review.entity.ReviewSchedule;
import ds.project.orino.domain.review.entity.ReviewStatus;
import ds.project.orino.domain.review.repository.ReviewScheduleRepository;
import ds.project.orino.planner.calendar.service.ReviewScheduleGenerator;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MissedReviewProcessorTest {

    @Mock private ReviewConfigRepository reviewConfigRepository;
    @Mock private UserPreferenceRepository userPreferenceRepository;
    @Mock private ReviewScheduleRepository reviewScheduleRepository;
    @Mock private ReviewScheduleGenerator reviewScheduleGenerator;

    private MissedReviewProcessor processor;

    private StudyMaterial material;
    private StudyUnit unit;

    @BeforeEach
    void setUp() {
        processor = new MissedReviewProcessor(
                reviewConfigRepository, userPreferenceRepository,
                reviewScheduleRepository, reviewScheduleGenerator);
        Member member = new Member("user", "pw");
        material = new StudyMaterial(
                member, "자료", MaterialType.BOOK, null, null,
                null, DeadlineMode.FREE);
        ReflectionTestUtils.setField(material, "id", 10L);
        unit = new StudyUnit(material, "단원1", 1, 30, null);
        ReflectionTestUtils.setField(unit, "id", 20L);
    }

    @Test
    @DisplayName("IMMEDIATE 정책은 상태를 변경하지 않는다")
    void immediate_keepsOverdue() {
        ReviewSchedule overdue = overdueReview();
        given(reviewConfigRepository.findByMaterialId(10L))
                .willReturn(Optional.of(config(MissedPolicy.IMMEDIATE)));

        MissedPolicy result = processor.apply(
                1L, overdue, LocalDate.of(2026, 4, 6));

        assertThat(result).isEqualTo(MissedPolicy.IMMEDIATE);
        assertThat(overdue.getStatus()).isEqualTo(ReviewStatus.OVERDUE);
        verify(reviewScheduleRepository, never())
                .deleteAll(any());
        verify(reviewScheduleGenerator, never())
                .generate(anyLong(), any(), any());
    }

    @Test
    @DisplayName("SKIP 정책은 SKIPPED 상태로 전환한다")
    void skip_marksSkipped() {
        ReviewSchedule overdue = overdueReview();
        given(reviewConfigRepository.findByMaterialId(10L))
                .willReturn(Optional.of(config(MissedPolicy.SKIP)));

        MissedPolicy result = processor.apply(
                1L, overdue, LocalDate.of(2026, 4, 6));

        assertThat(result).isEqualTo(MissedPolicy.SKIP);
        assertThat(overdue.getStatus()).isEqualTo(ReviewStatus.SKIPPED);
    }

    @Test
    @DisplayName("RESET 정책은 남은 복습을 삭제 후 재생성한다")
    void reset_regeneratesAll() {
        ReviewSchedule overdue = overdueReview();
        ReviewSchedule other = new ReviewSchedule(
                unit, 3, LocalDate.of(2026, 4, 20));
        given(reviewConfigRepository.findByMaterialId(10L))
                .willReturn(Optional.of(config(MissedPolicy.RESET)));
        given(reviewScheduleRepository.findByStudyUnitIdAndStatusIn(
                eq(20L), any()))
                .willReturn(List.of(overdue, other));

        LocalDate today = LocalDate.of(2026, 4, 6);
        MissedPolicy result = processor.apply(1L, overdue, today);

        assertThat(result).isEqualTo(MissedPolicy.RESET);
        verify(reviewScheduleRepository)
                .deleteAll(List.of(overdue, other));
        verify(reviewScheduleRepository).flush();
        verify(reviewScheduleGenerator).generate(1L, unit, today);
    }

    @Test
    @DisplayName("ReviewConfig가 없으면 UserPreference 기본 정책을 사용한다")
    void fallsBackToUserPreference() {
        ReviewSchedule overdue = overdueReview();
        given(reviewConfigRepository.findByMaterialId(10L))
                .willReturn(Optional.empty());
        UserPreference preference = new UserPreference(material.getMember());
        ReflectionTestUtils.setField(
                preference, "defaultMissedPolicy", MissedPolicy.SKIP);
        given(userPreferenceRepository.findByMemberId(1L))
                .willReturn(Optional.of(preference));

        MissedPolicy result = processor.apply(
                1L, overdue, LocalDate.of(2026, 4, 6));

        assertThat(result).isEqualTo(MissedPolicy.SKIP);
        assertThat(overdue.getStatus()).isEqualTo(ReviewStatus.SKIPPED);
    }

    @Test
    @DisplayName("설정과 Preference가 모두 없으면 IMMEDIATE 폴백")
    void fallsBackToImmediate_whenNoSettings() {
        ReviewSchedule overdue = overdueReview();
        given(reviewConfigRepository.findByMaterialId(10L))
                .willReturn(Optional.empty());
        given(userPreferenceRepository.findByMemberId(1L))
                .willReturn(Optional.empty());

        MissedPolicy result = processor.apply(
                1L, overdue, LocalDate.of(2026, 4, 6));

        assertThat(result).isEqualTo(MissedPolicy.IMMEDIATE);
        assertThat(overdue.getStatus()).isEqualTo(ReviewStatus.OVERDUE);
    }

    private ReviewSchedule overdueReview() {
        ReviewSchedule review = new ReviewSchedule(
                unit, 1, LocalDate.of(2026, 4, 4));
        review.markOverdue();
        return review;
    }

    private ReviewConfig config(MissedPolicy policy) {
        return new ReviewConfig(material, "1,2,3,7,15,30", policy);
    }
}
