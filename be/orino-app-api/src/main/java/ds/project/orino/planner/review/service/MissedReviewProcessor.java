package ds.project.orino.planner.review.service;

import ds.project.orino.domain.material.entity.MissedPolicy;
import ds.project.orino.domain.material.entity.ReviewConfig;
import ds.project.orino.domain.material.entity.StudyMaterial;
import ds.project.orino.domain.material.entity.StudyUnit;
import ds.project.orino.domain.material.repository.ReviewConfigRepository;
import ds.project.orino.domain.preference.entity.UserPreference;
import ds.project.orino.domain.preference.repository.UserPreferenceRepository;
import ds.project.orino.domain.review.entity.ReviewSchedule;
import ds.project.orino.domain.review.entity.ReviewStatus;
import ds.project.orino.domain.review.repository.ReviewScheduleRepository;
import ds.project.orino.planner.calendar.service.ReviewScheduleGenerator;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * OVERDUE 상태의 복습에 대한 놓침 정책(missedPolicy) 을 적용한다.
 *
 * <ul>
 *     <li>IMMEDIATE: OVERDUE 유지 (ItemCollector 가 다음 날 최우선 배치)</li>
 *     <li>SKIP: SKIPPED 로 전환, 이후 예정된 복습은 그대로 유지</li>
 *     <li>RESET: 같은 StudyUnit 의 PENDING/OVERDUE 복습 전부 삭제 후 오늘 기준으로 재생성</li>
 * </ul>
 *
 * 정책 우선순위: 자료별 {@link ReviewConfig#getMissedPolicy()}
 *              → 사용자 {@link UserPreference#getDefaultMissedPolicy()}
 *              → {@link MissedPolicy#IMMEDIATE} (최종 폴백)
 */
@Component
public class MissedReviewProcessor {

    private final ReviewConfigRepository reviewConfigRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final ReviewScheduleRepository reviewScheduleRepository;
    private final ReviewScheduleGenerator reviewScheduleGenerator;

    public MissedReviewProcessor(
            ReviewConfigRepository reviewConfigRepository,
            UserPreferenceRepository userPreferenceRepository,
            ReviewScheduleRepository reviewScheduleRepository,
            ReviewScheduleGenerator reviewScheduleGenerator) {
        this.reviewConfigRepository = reviewConfigRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.reviewScheduleGenerator = reviewScheduleGenerator;
    }

    public MissedPolicy apply(Long memberId, ReviewSchedule overdueReview,
                              LocalDate today) {
        MissedPolicy policy = resolvePolicy(memberId, overdueReview);
        switch (policy) {
            case IMMEDIATE -> {
                // OVERDUE 그대로 유지
            }
            case SKIP -> overdueReview.skip();
            case RESET -> resetReviews(memberId, overdueReview, today);
            default -> {
                // no-op
            }
        }
        return policy;
    }

    private MissedPolicy resolvePolicy(Long memberId, ReviewSchedule review) {
        StudyMaterial material = review.getStudyUnit().getMaterial();
        return reviewConfigRepository.findByMaterialId(material.getId())
                .map(ReviewConfig::getMissedPolicy)
                .orElseGet(() -> userPreferenceRepository
                        .findByMemberId(memberId)
                        .map(UserPreference::getDefaultMissedPolicy)
                        .orElse(MissedPolicy.IMMEDIATE));
    }

    private void resetReviews(Long memberId, ReviewSchedule review,
                              LocalDate today) {
        StudyUnit unit = review.getStudyUnit();
        List<ReviewSchedule> remaining = reviewScheduleRepository
                .findByStudyUnitIdAndStatusIn(
                        unit.getId(),
                        List.of(ReviewStatus.PENDING, ReviewStatus.OVERDUE));
        reviewScheduleRepository.deleteAll(remaining);
        reviewScheduleRepository.flush();
        reviewScheduleGenerator.generate(memberId, unit, today);
    }
}
