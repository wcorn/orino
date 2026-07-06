package ds.project.orino.planner.review.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.repository.FlashcardRepository;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.planner.review.dto.CompletedReviewView;
import ds.project.orino.planner.review.dto.ReviewCompletionRequest;
import ds.project.orino.planner.review.dto.ReviewCompletionResponse;
import ds.project.orino.planner.review.dto.ReviewScheduleView;
import ds.project.orino.core.time.UserTimeZone;
import ds.project.orino.planner.review.sm2.Sm2Calculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReviewCompletionService {

    private final ReviewScheduleRepository reviewScheduleRepository;
    private final FlashcardRepository flashcardRepository;
    private final ReviewMirrorService reviewMirrorService;
    private final Clock clock;

    public ReviewCompletionService(ReviewScheduleRepository reviewScheduleRepository,
                                   FlashcardRepository flashcardRepository,
                                   ReviewMirrorService reviewMirrorService, Clock clock) {
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.flashcardRepository = flashcardRepository;
        this.reviewMirrorService = reviewMirrorService;
        this.clock = clock;
    }

    @Transactional
    public ReviewCompletionResponse complete(Long memberId, Long reviewId, ReviewCompletionRequest request) {
        ReviewSchedule current = reviewScheduleRepository.findByIdAndMemberId(reviewId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        if (current.getStatus() != ReviewStatus.PENDING) {
            throw new CustomException(ErrorCode.INVALID_STATE);
        }

        Instant now = clock.instant();
        ZoneId zone = UserTimeZone.get();

        int newSequence = current.getSequence() + 1;
        Sm2Calculator.Result computed = Sm2Calculator.next(
                newSequence, current.getIntervalDays(), current.getEaseFactor(), request.rating());

        current.complete(request.rating(), now, zone);

        Instant scheduledAt = ReviewSchedule.computeScheduledAt(
                request.rating(), computed.intervalDays(), now, zone);
        ReviewSchedule next = reviewScheduleRepository.save(new ReviewSchedule(
                memberId, current.getFlashcardId(), newSequence,
                scheduledAt, computed.intervalDays(), computed.easeFactor()));

        // Sibling burying — 오늘 due인 짝 복습을 내일로 미룬다(regurgitation 방지). SM-2는 불변.
        List<Long> affectedDates = new ArrayList<>();
        List<Long> buriedReviewIds = burySiblings(memberId, current.getFlashcardId(), now, zone, affectedDates);

        // 완료된 dueDate(감소)와 다음 dueDate(증가) 묶음 + burying으로 바뀐 dueDate들을 재동기화(커밋 후).
        // AGAIN은 04:00 정각이 아니어서 reconcile 집계에서 자연히 제외된다.
        List<LocalDate> reconcileDates = new ArrayList<>();
        reconcileDates.add(current.getScheduledAt().atZone(zone).toLocalDate());
        reconcileDates.add(next.getScheduledAt().atZone(zone).toLocalDate());
        for (Long epochDay : affectedDates) {
            reconcileDates.add(LocalDate.ofEpochDay(epochDay));
        }
        reviewMirrorService.reconcileAfterCommit(memberId, reconcileDates, zone);

        return new ReviewCompletionResponse(
                CompletedReviewView.of(current),
                ReviewScheduleView.firstReview(next),
                buriedReviewIds);
    }

    /**
     * 완료한 카드에 짝(siblingGroupId)이 있으면, 지금 due(scheduled_at ≤ now, PENDING)인 짝 복습을
     * 내일 04:00으로 미룬다. SM-2 간격/ease는 건드리지 않는다. 밀린 복습 id들을 반환한다.
     * {@code affectedDates}에는 재동기화가 필요한 날짜(밀리기 전/후)를 epochDay로 담는다.
     */
    private List<Long> burySiblings(Long memberId, Long flashcardId, Instant now, ZoneId zone,
                                    List<Long> affectedDates) {
        Flashcard card = flashcardRepository.findById(flashcardId).orElse(null);
        if (card == null || card.getSiblingGroupId() == null) {
            return List.of();
        }
        List<Long> siblingIds = flashcardRepository.findAllBySiblingGroupId(card.getSiblingGroupId()).stream()
                .map(Flashcard::getId)
                .filter(id -> !id.equals(flashcardId))
                .toList();
        if (siblingIds.isEmpty()) {
            return List.of();
        }
        List<ReviewSchedule> dueSiblings = reviewScheduleRepository
                .findAllByFlashcardIdInAndStatusAndScheduledAtLessThanEqual(
                        siblingIds, ReviewStatus.PENDING, now);

        List<Long> buriedIds = new ArrayList<>();
        for (ReviewSchedule sibling : dueSiblings) {
            affectedDates.add(sibling.getScheduledAt().atZone(zone).toLocalDate().toEpochDay());
            sibling.bury(now, zone);
            affectedDates.add(sibling.getScheduledAt().atZone(zone).toLocalDate().toEpochDay());
            buriedIds.add(sibling.getId());
        }
        return buriedIds;
    }
}
