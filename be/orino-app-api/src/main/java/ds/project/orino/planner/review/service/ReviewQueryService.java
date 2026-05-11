package ds.project.orino.planner.review.service;

import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.Rating;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.domain.planner.unit.entity.StudyUnit;
import ds.project.orino.domain.planner.unit.repository.StudyUnitRepository;
import ds.project.orino.planner.review.dto.PreviewResponse;
import ds.project.orino.planner.review.dto.ReviewMaterialResponse;
import ds.project.orino.planner.review.dto.ReviewUnitResponse;
import ds.project.orino.planner.review.dto.TodayReviewResponse;
import ds.project.orino.planner.review.dto.TodayReviewsResponse;
import ds.project.orino.planner.review.sm2.Sm2Calculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReviewQueryService {

    private final ReviewScheduleRepository reviewScheduleRepository;
    private final StudyUnitRepository studyUnitRepository;
    private final StudyMaterialRepository studyMaterialRepository;
    private final Clock clock;

    public ReviewQueryService(ReviewScheduleRepository reviewScheduleRepository,
                              StudyUnitRepository studyUnitRepository,
                              StudyMaterialRepository studyMaterialRepository,
                              Clock clock) {
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.studyUnitRepository = studyUnitRepository;
        this.studyMaterialRepository = studyMaterialRepository;
        this.clock = clock;
    }

    public TodayReviewsResponse getTodayReviews(Long memberId) {
        LocalDate today = LocalDate.now(clock);

        List<ReviewSchedule> reviews = reviewScheduleRepository
                .findAllByMemberIdAndScheduledDateLessThanEqualAndStatusOrderByScheduledDateAscIdAsc(
                        memberId, today, ReviewStatus.PENDING);

        if (reviews.isEmpty()) {
            return new TodayReviewsResponse(today, List.of());
        }

        List<Long> unitIds = reviews.stream().map(ReviewSchedule::getStudyUnitId).distinct().toList();
        Map<Long, StudyUnit> unitsById = studyUnitRepository.findAllByIdIn(unitIds).stream()
                .collect(Collectors.toMap(StudyUnit::getId, Function.identity()));

        List<Long> materialIds = unitsById.values().stream()
                .map(StudyUnit::getMaterialId).distinct().toList();
        Map<Long, StudyMaterial> materialsById = studyMaterialRepository.findAllByIdIn(materialIds).stream()
                .collect(Collectors.toMap(StudyMaterial::getId, Function.identity()));

        List<TodayReviewResponse> items = reviews.stream()
                .map(r -> toResponse(r, today, unitsById, materialsById))
                .toList();

        return new TodayReviewsResponse(today, items);
    }

    private TodayReviewResponse toResponse(ReviewSchedule review, LocalDate today,
                                            Map<Long, StudyUnit> unitsById,
                                            Map<Long, StudyMaterial> materialsById) {
        StudyUnit unit = unitsById.get(review.getStudyUnitId());
        StudyMaterial material = materialsById.get(unit.getMaterialId());
        return new TodayReviewResponse(
                review.getId(),
                review.getScheduledDate(),
                Math.max(0, (int) (today.toEpochDay() - review.getScheduledDate().toEpochDay())),
                review.getSequence(),
                review.getIntervalDays(),
                review.getEaseFactor(),
                ReviewUnitResponse.of(unit, ReviewMaterialResponse.from(material)),
                buildPreview(review)
        );
    }

    private PreviewResponse buildPreview(ReviewSchedule review) {
        int seq = review.getSequence();
        int interval = review.getIntervalDays();
        var ease = review.getEaseFactor();
        return new PreviewResponse(
                Sm2Calculator.next(seq, interval, ease, Rating.AGAIN).intervalDays(),
                Sm2Calculator.next(seq, interval, ease, Rating.HARD).intervalDays(),
                Sm2Calculator.next(seq, interval, ease, Rating.GOOD).intervalDays(),
                Sm2Calculator.next(seq, interval, ease, Rating.EASY).intervalDays()
        );
    }
}
