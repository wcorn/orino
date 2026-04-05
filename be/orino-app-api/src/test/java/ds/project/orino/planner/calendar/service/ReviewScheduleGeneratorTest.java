package ds.project.orino.planner.calendar.service;

import ds.project.orino.domain.material.entity.MissedPolicy;
import ds.project.orino.domain.material.entity.ReviewConfig;
import ds.project.orino.domain.material.entity.StudyMaterial;
import ds.project.orino.domain.material.entity.StudyUnit;
import ds.project.orino.domain.material.repository.ReviewConfigRepository;
import ds.project.orino.domain.preference.entity.UserPreference;
import ds.project.orino.domain.preference.repository.UserPreferenceRepository;
import ds.project.orino.domain.review.entity.ReviewSchedule;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewScheduleGeneratorTest {

    @Mock private ReviewConfigRepository reviewConfigRepository;
    @Mock private UserPreferenceRepository userPreferenceRepository;
    @Mock private ReviewScheduleRepository reviewScheduleRepository;

    private ReviewScheduleGenerator generator;
    private StudyMaterial material;
    private StudyUnit unit;

    @BeforeEach
    void setUp() {
        generator = new ReviewScheduleGenerator(
                reviewConfigRepository, userPreferenceRepository,
                reviewScheduleRepository);

        material = new StudyMaterial(null, "수학", null,
                null, null, null, null);
        ReflectionTestUtils.setField(material, "id", 100L);
        unit = new StudyUnit(material, "챕터1", 1, 30, null);
        ReflectionTestUtils.setField(unit, "id", 200L);
    }

    @Test
    @DisplayName("material 전용 ReviewConfig가 있으면 해당 간격을 사용한다")
    void usesMaterialConfig() {
        given(reviewConfigRepository.findByMaterialId(100L))
                .willReturn(Optional.of(new ReviewConfig(
                        material, "1,3,7", MissedPolicy.IMMEDIATE)));

        ReviewScheduleGenerator.Result result = generator.generate(
                1L, unit, LocalDate.of(2026, 4, 10));

        assertThat(result.count()).isEqualTo(3);
        assertThat(result.firstReviewDate()).isEqualTo(LocalDate.of(2026, 4, 11));
    }

    @Test
    @DisplayName("material config가 없으면 UserPreference 기본값을 사용한다")
    void usesUserDefault() {
        given(reviewConfigRepository.findByMaterialId(100L))
                .willReturn(Optional.empty());
        UserPreference pref = new UserPreference(null);
        ReflectionTestUtils.setField(pref, "defaultReviewIntervals",
                "2,5,10");
        given(userPreferenceRepository.findByMemberId(1L))
                .willReturn(Optional.of(pref));

        ReviewScheduleGenerator.Result result = generator.generate(
                1L, unit, LocalDate.of(2026, 4, 10));

        assertThat(result.count()).isEqualTo(3);
        assertThat(result.firstReviewDate())
                .isEqualTo(LocalDate.of(2026, 4, 12));
    }

    @Test
    @DisplayName("생성된 ReviewSchedule의 sequence는 1부터 순차 증가한다")
    void assignsSequence() {
        given(reviewConfigRepository.findByMaterialId(100L))
                .willReturn(Optional.of(new ReviewConfig(
                        material, "1,2,3", MissedPolicy.IMMEDIATE)));

        generator.generate(1L, unit, LocalDate.of(2026, 4, 10));

        ArgumentCaptor<List<ReviewSchedule>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(reviewScheduleRepository).saveAll(captor.capture());
        List<ReviewSchedule> saved = captor.getValue();
        assertThat(saved).hasSize(3);
        assertThat(saved.get(0).getSequence()).isEqualTo(1);
        assertThat(saved.get(1).getSequence()).isEqualTo(2);
        assertThat(saved.get(2).getSequence()).isEqualTo(3);
        assertThat(saved.get(0).getScheduledDate())
                .isEqualTo(LocalDate.of(2026, 4, 11));
        assertThat(saved.get(2).getScheduledDate())
                .isEqualTo(LocalDate.of(2026, 4, 13));
    }

    @Test
    @DisplayName("설정이 모두 없으면 기본 fallback(1,2,3,7,15,30)을 사용한다")
    void fallbackIntervals() {
        given(reviewConfigRepository.findByMaterialId(100L))
                .willReturn(Optional.empty());
        given(userPreferenceRepository.findByMemberId(1L))
                .willReturn(Optional.empty());

        ReviewScheduleGenerator.Result result = generator.generate(
                1L, unit, LocalDate.of(2026, 4, 10));

        assertThat(result.count()).isEqualTo(6);
        verify(reviewScheduleRepository).saveAll(any());
    }
}
