package ds.project.orino.planner.calendar.service;

import ds.project.orino.domain.material.entity.ReviewConfig;
import ds.project.orino.domain.material.entity.StudyUnit;
import ds.project.orino.domain.material.repository.ReviewConfigRepository;
import ds.project.orino.domain.preference.entity.UserPreference;
import ds.project.orino.domain.preference.repository.UserPreferenceRepository;
import ds.project.orino.domain.review.entity.ReviewSchedule;
import ds.project.orino.domain.review.repository.ReviewScheduleRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * STUDY 단위 완료 시 복습 일정(ReviewSchedule)을 자동 생성한다.
 *
 * 간격 우선순위: material 전용 ReviewConfig.intervals
 *  → UserPreference.defaultReviewIntervals (기본 "1,2,3,7,15,30")
 */
@Component
public class ReviewScheduleGenerator {

    private static final String FALLBACK_INTERVALS = "1,2,3,7,15,30";

    private final ReviewConfigRepository reviewConfigRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final ReviewScheduleRepository reviewScheduleRepository;

    public ReviewScheduleGenerator(
            ReviewConfigRepository reviewConfigRepository,
            UserPreferenceRepository userPreferenceRepository,
            ReviewScheduleRepository reviewScheduleRepository) {
        this.reviewConfigRepository = reviewConfigRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.reviewScheduleRepository = reviewScheduleRepository;
    }

    public Result generate(Long memberId, StudyUnit unit, LocalDate completedDate) {
        List<Integer> intervals = resolveIntervals(memberId, unit.getMaterial().getId());
        List<ReviewSchedule> created = new ArrayList<>();
        int sequence = 1;
        for (int days : intervals) {
            LocalDate scheduledDate = completedDate.plusDays(days);
            created.add(new ReviewSchedule(unit, sequence, scheduledDate));
            sequence++;
        }
        reviewScheduleRepository.saveAll(created);

        LocalDate firstReviewDate = intervals.isEmpty()
                ? null : completedDate.plusDays(intervals.get(0));
        return new Result(created.size(), firstReviewDate);
    }

    private List<Integer> resolveIntervals(Long memberId, Long materialId) {
        return reviewConfigRepository.findByMaterialId(materialId)
                .map(ReviewConfig::getIntervals)
                .map(this::parseIntervals)
                .orElseGet(() -> parseIntervals(
                        defaultIntervalsOf(memberId)));
    }

    private String defaultIntervalsOf(Long memberId) {
        return userPreferenceRepository.findByMemberId(memberId)
                .map(UserPreference::getDefaultReviewIntervals)
                .orElse(FALLBACK_INTERVALS);
    }

    private List<Integer> parseIntervals(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .toList();
    }

    public record Result(int count, LocalDate firstReviewDate) {
    }
}
