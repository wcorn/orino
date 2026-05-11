package ds.project.orino.planner.review.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.domain.planner.unit.entity.StudyUnit;
import ds.project.orino.domain.planner.unit.repository.StudyUnitRepository;
import ds.project.orino.planner.review.dto.CompletedUnitResponse;
import ds.project.orino.planner.review.dto.ReviewResponse;
import ds.project.orino.planner.review.dto.UnitCompletionResponse;
import ds.project.orino.planner.review.sm2.Sm2Calculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class UnitCompletionService {

    private static final int FIRST_INTERVAL_DAYS = 1;

    private final StudyUnitRepository studyUnitRepository;
    private final ReviewScheduleRepository reviewScheduleRepository;
    private final Clock clock;

    public UnitCompletionService(StudyUnitRepository studyUnitRepository,
                                 ReviewScheduleRepository reviewScheduleRepository,
                                 Clock clock) {
        this.studyUnitRepository = studyUnitRepository;
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.clock = clock;
    }

    @Transactional
    public UnitCompletionResponse complete(Long memberId, Long unitId) {
        StudyUnit unit = studyUnitRepository.findByIdAndMemberId(unitId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        if (unit.isCompleted()) {
            throw new CustomException(ErrorCode.INVALID_STATE);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = LocalDate.now(clock);
        unit.markCompleted(now);

        ReviewSchedule firstReview = reviewScheduleRepository.save(new ReviewSchedule(
                memberId,
                unit.getId(),
                1,
                today.plusDays(FIRST_INTERVAL_DAYS),
                FIRST_INTERVAL_DAYS,
                Sm2Calculator.INITIAL_EASE
        ));

        return new UnitCompletionResponse(
                CompletedUnitResponse.from(unit),
                ReviewResponse.from(firstReview)
        );
    }
}
