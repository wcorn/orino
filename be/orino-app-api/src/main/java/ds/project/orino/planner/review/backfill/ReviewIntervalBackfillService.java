package ds.project.orino.planner.review.backfill;

import ds.project.orino.common.time.StudyDay;
import ds.project.orino.core.time.UserTimeZone;
import ds.project.orino.domain.planner.review.entity.Rating;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.planner.review.service.ReviewMirrorService;
import ds.project.orino.planner.review.sm2.Sm2Calculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 등급별 간격 규칙 도입(#1001) 이전에 잡힌 복습 일정을 새 규칙으로 다시 계산한다.
 *
 * <p>앞으로 채점하는 것만 새 규칙을 타면 이미 잡힌 일정은 옛 값으로 남는다. PENDING 복습을 만들어낸
 * <b>직전 회차</b>에 rating·간격·ease·완료시각이 그대로 남아 있으므로, 그 입력으로 새 규칙을 다시 돌려
 * 간격·ease·due 시각을 덮어쓴다.
 *
 * <p><b>옛 규칙이 계산했을 값과 저장값이 일치하는 행만</b> 건드린다. 덕분에
 * <ul>
 *   <li>sibling burying으로 옮겨진 행을 되돌리지 않는다(저장값이 옛 계산과 다르다)</li>
 *   <li>두 번 실행해도 no-op다 — 한 번 덮어쓰고 나면 더 이상 옛 값과 일치하지 않는다</li>
 * </ul>
 *
 * <p>GOOD·AGAIN으로 만들어진 행은 새 규칙에서도 값이 같아 실제로 바뀌는 건 HARD·EASY 행뿐이다.
 * due 날짜가 바뀐 행은 <b>옛 날짜와 새 날짜 둘 다</b> 캘린더 미러를 reconcile한다(비게 된 날짜는
 * 이벤트가 삭제된다).
 */
@Service
public class ReviewIntervalBackfillService {

    private static final Logger log = LoggerFactory.getLogger(ReviewIntervalBackfillService.class);

    private final ReviewScheduleRepository reviewScheduleRepository;
    private final ReviewMirrorService reviewMirrorService;

    public ReviewIntervalBackfillService(ReviewScheduleRepository reviewScheduleRepository,
                                         ReviewMirrorService reviewMirrorService) {
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.reviewMirrorService = reviewMirrorService;
    }

    /** 다시 계산해 저장한 복습 건수를 반환한다. */
    @Transactional
    public int run() {
        ZoneId zone = UserTimeZone.get();
        List<Object[]> pairs = reviewScheduleRepository.findPendingWithPrecedingCompleted();

        // 미러 reconcile 대상 — 멤버별로 (비게 된 옛 날짜 + 새로 채워진 날짜)를 모은다.
        Map<Long, Set<LocalDate>> affectedDates = new LinkedHashMap<>();
        int rescheduled = 0;

        for (Object[] pair : pairs) {
            ReviewSchedule pending = (ReviewSchedule) pair[0];
            ReviewSchedule preceding = (ReviewSchedule) pair[1];

            LocalDate previousDueDate = StudyDay.of(pending.getScheduledAt(), zone);
            if (!reschedule(pending, preceding, zone)) {
                continue;
            }
            rescheduled++;

            LocalDate newDueDate = StudyDay.of(pending.getScheduledAt(), zone);
            if (!newDueDate.equals(previousDueDate)) {
                Set<LocalDate> dates = affectedDates.computeIfAbsent(
                        pending.getMemberId(), id -> new LinkedHashSet<>());
                dates.add(previousDueDate);
                dates.add(newDueDate);
            }
        }

        if (rescheduled > 0) {
            log.info("복습 간격 규칙 백필(#1001): {}건 재계산", rescheduled);
            affectedDates.forEach((memberId, dates) ->
                    reviewMirrorService.reconcileAfterCommit(memberId, dates, zone));
        }
        return rescheduled;
    }

    /** 새 규칙으로 덮어썼으면 true. 옛 계산과 어긋나거나 바뀔 값이 없으면 그대로 두고 false. */
    private boolean reschedule(ReviewSchedule pending, ReviewSchedule preceding, ZoneId zone) {
        Rating rating = preceding.getRating();
        Instant completedAt = preceding.getCompletedAt();
        if (rating == null || completedAt == null) {
            return false;
        }

        int legacyInterval = Sm2Calculator.legacyIntervalDays(
                pending.getSequence(), preceding.getIntervalDays(), preceding.getEaseFactor(), rating);
        Instant legacyScheduledAt = ReviewSchedule.computeScheduledAt(
                rating, legacyInterval, completedAt, zone);

        // 옛 규칙이 놓은 자리에 그대로 있는 행만 손댄다 — burying으로 옮겨졌거나 이미 백필된 행은 건너뛴다.
        if (pending.getIntervalDays() != legacyInterval
                || !pending.getScheduledAt().equals(legacyScheduledAt)) {
            return false;
        }

        // 그때 며칠 늦게 봤는지는 완료 기록에 남아 있다(elapsedDays) — 새 규칙의 days_late 보너스에 쓴다.
        int daysLate = preceding.getElapsedDays() == null ? 0 : preceding.getElapsedDays();
        Sm2Calculator.Result next = Sm2Calculator.next(
                preceding.getIntervalDays(), preceding.getEaseFactor(), daysLate, rating);
        if (next.intervalDays() == pending.getIntervalDays()
                && next.easeFactor().compareTo(pending.getEaseFactor()) == 0) {
            return false;
        }

        pending.reschedule(next.intervalDays(), next.easeFactor(),
                ReviewSchedule.computeScheduledAt(rating, next.intervalDays(), completedAt, zone));
        return true;
    }
}
