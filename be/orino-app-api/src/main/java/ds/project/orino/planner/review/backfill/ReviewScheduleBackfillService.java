package ds.project.orino.planner.review.backfill;

import ds.project.orino.common.time.StudyDay;
import ds.project.orino.core.time.UserTimeZone;
import ds.project.orino.domain.planner.review.entity.Rating;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.planner.review.service.ReviewMirrorService;
import ds.project.orino.planner.review.sm2.Sm2Calculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 스케줄 규칙이 바뀌기 전에 잡힌 복습 일정을 현재 규칙으로 다시 계산한다.
 *
 * <p>앞으로 채점하는 것만 새 규칙을 타면 이미 잡힌 일정은 옛 값으로 남는다. 그래서 아직 오지 않은
 * 복습을 <b>그 일정을 만들어낸 시점</b>에서 다시 계산해 덮어쓴다.
 *
 * <ul>
 *   <li>2회차 이상 — 직전 COMPLETED 회차에 rating·간격·ease·밀린 일수·완료시각이 남아 있다</li>
 *   <li>1회차(카드 생성분) — 직전 회차가 없으니 그 행의 {@code created_at}이 기준이다</li>
 * </ul>
 *
 * <p>지금까지 규칙이 두 번 바뀌었다.
 * <ol>
 *   <li><b>#1001</b> 등급별 간격 배수(Anki) — Hard/Good/Easy 간격이 갈린다</li>
 *   <li><b>#1003</b> 학습일 경계 04:00 — 자정~04:00에 만든 일정이 하루 늦게 잡혀 있었다</li>
 * </ol>
 *
 * <p><b>옛 규칙이 놓았을 자리에 그대로 있는 행만</b> 건드린다(두 세대 모두 후보로 본다 — 1번 백필이
 * 돌기 전인지 후인지 알 수 없어서다). 덕분에 sibling burying으로 옮겨진 행을 되돌리지 않고, 여러 번
 * 실행해도 no-op다 — 한 번 덮어쓰고 나면 더 이상 옛 자리와 일치하지 않는다.
 *
 * <p>due 날짜가 바뀐 행은 <b>옛 날짜와 새 날짜 둘 다</b> 캘린더 미러를 reconcile한다(비게 된 날짜는
 * 이벤트가 삭제된다).
 */
@Service
public class ReviewScheduleBackfillService {

    private static final Logger log = LoggerFactory.getLogger(ReviewScheduleBackfillService.class);

    /** 짝 카드는 첫 복습을 A=+1일, B=+2일로 엇갈린다({@code ReviewSchedule.firstReview}). */
    private static final int MAX_FIRST_REVIEW_OFFSET_DAYS = 2;

    private final ReviewScheduleRepository reviewScheduleRepository;
    private final ReviewMirrorService reviewMirrorService;

    public ReviewScheduleBackfillService(ReviewScheduleRepository reviewScheduleRepository,
                                         ReviewMirrorService reviewMirrorService) {
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.reviewMirrorService = reviewMirrorService;
    }

    /** 다시 계산해 저장한 복습 건수를 반환한다. */
    @Transactional
    public int run() {
        ZoneId zone = UserTimeZone.get();
        List<ReviewSchedule> pendings = reviewScheduleRepository.findAllByStatus(ReviewStatus.PENDING);
        Map<Long, ReviewSchedule> precedingByPendingId = reviewScheduleRepository
                .findPendingWithPrecedingCompleted().stream()
                .collect(Collectors.toMap(
                        pair -> ((ReviewSchedule) pair[0]).getId(),
                        pair -> (ReviewSchedule) pair[1],
                        (first, duplicate) -> first));

        // 미러 reconcile 대상 — 멤버별로 (비게 된 옛 날짜 + 새로 채워진 날짜)를 모은다.
        Map<Long, Set<LocalDate>> affectedDates = new LinkedHashMap<>();
        int rescheduled = 0;

        for (ReviewSchedule pending : pendings) {
            LocalDate previousDueDate = StudyDay.of(pending.getScheduledAt(), zone);
            if (!reschedule(pending, precedingByPendingId.get(pending.getId()), zone)) {
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
            log.info("복습 일정 백필: {}건 재계산", rescheduled);
            affectedDates.forEach((memberId, dates) ->
                    reviewMirrorService.reconcileAfterCommit(memberId, dates, zone));
        }
        return rescheduled;
    }

    /** 현재 규칙으로 덮어썼으면 true. 옛 자리와 어긋나거나 바뀔 값이 없으면 그대로 두고 false. */
    private boolean reschedule(ReviewSchedule pending, ReviewSchedule preceding, ZoneId zone) {
        if (preceding != null) {
            return rescheduleFromCompletion(pending, preceding, zone);
        }
        return rescheduleFirstReview(pending, zone);
    }

    /** 2회차 이상 — 직전 회차를 그대로 다시 채점했다고 보고 현재 규칙으로 계산한다. */
    private boolean rescheduleFromCompletion(ReviewSchedule pending, ReviewSchedule preceding, ZoneId zone) {
        Rating rating = preceding.getRating();
        Instant completedAt = preceding.getCompletedAt();
        if (rating == null || completedAt == null) {
            return false;
        }

        // 그때 며칠 늦게 봤는지는 완료 기록에 남아 있다(elapsedDays) — days_late 보너스에 쓴다.
        int daysLate = preceding.getElapsedDays() == null ? 0 : preceding.getElapsedDays();
        Sm2Calculator.Result target = Sm2Calculator.next(
                preceding.getIntervalDays(), preceding.getEaseFactor(), daysLate, rating);
        Instant targetScheduledAt = ReviewSchedule.computeScheduledAt(
                rating, target.intervalDays(), completedAt, zone);

        if (!sitsWhereAnOldRuleLeftIt(pending, preceding, rating, completedAt, target, zone)) {
            return false;
        }
        if (pending.getIntervalDays() == target.intervalDays()
                && pending.getEaseFactor().compareTo(target.easeFactor()) == 0
                && pending.getScheduledAt().equals(targetScheduledAt)) {
            return false;
        }

        pending.reschedule(target.intervalDays(), target.easeFactor(), targetScheduledAt);
        return true;
    }

    /**
     * 옛 규칙 세대들이 놓았을 자리 중 하나에 그대로 있는지. 두 세대 모두 due 날짜를 <b>달력 날짜</b>
     * 기준으로 잡았다(학습일 경계 도입 전).
     *
     * <ol>
     *   <li>순정 SM-2 간격 + 달력 날짜 — 간격 백필이 아직 안 돈 상태</li>
     *   <li>현재 간격 + 달력 날짜 — 간격 백필(#1001)만 돈 상태</li>
     * </ol>
     */
    private boolean sitsWhereAnOldRuleLeftIt(ReviewSchedule pending, ReviewSchedule preceding,
                                             Rating rating, Instant completedAt,
                                             Sm2Calculator.Result target, ZoneId zone) {
        int legacyInterval = Sm2Calculator.legacyIntervalDays(
                pending.getSequence(), preceding.getIntervalDays(), preceding.getEaseFactor(), rating);
        BigDecimal legacyEase = Sm2Calculator.legacyEaseFactor(preceding.getEaseFactor(), rating);

        return matches(pending, legacyInterval, legacyEase, rating, completedAt, zone)
                || matches(pending, target.intervalDays(), target.easeFactor(), rating, completedAt, zone);
    }

    private boolean matches(ReviewSchedule pending, int intervalDays, BigDecimal easeFactor,
                            Rating rating, Instant completedAt, ZoneId zone) {
        return pending.getIntervalDays() == intervalDays
                && pending.getEaseFactor().compareTo(easeFactor) == 0
                && pending.getScheduledAt().equals(
                        legacyScheduledAt(rating, intervalDays, completedAt, zone));
    }

    /** 학습일 경계(#1003) 이전의 due 계산 — 달력 날짜 기준이었다. */
    private static Instant legacyScheduledAt(Rating rating, int intervalDays,
                                             Instant completedAt, ZoneId zone) {
        if (rating == Rating.AGAIN) {
            return completedAt.plusSeconds(ReviewSchedule.RELEARN_MINUTES * 60L);
        }
        LocalDate calendarDate = completedAt.atZone(zone).toLocalDate();
        return StudyDay.startOf(calendarDate.plusDays(intervalDays), zone);
    }

    /**
     * 1회차(카드 생성분) — 직전 회차가 없어 간격 규칙과는 무관하다. 학습일 경계만 다시 적용해,
     * 자정~04:00에 만든 카드의 첫 복습을 하루 당긴다(#1003).
     */
    private boolean rescheduleFirstReview(ReviewSchedule pending, ZoneId zone) {
        Instant createdAt = pending.getCreatedAt();
        if (pending.getSequence() != 1 || createdAt == null) {
            return false;
        }

        LocalDate calendarDate = createdAt.atZone(zone).toLocalDate();
        LocalDate studyDay = StudyDay.of(createdAt, zone);
        if (calendarDate.equals(studyDay)) {
            // 04:00 이후에 만든 카드는 두 규칙이 같은 날짜를 낸다.
            return false;
        }

        // 짝 카드 엇갈림(A=+1일, B=+2일)을 보존하려고 저장값에서 offset을 읽는다.
        long offset = ChronoUnit.DAYS.between(calendarDate, StudyDay.of(pending.getScheduledAt(), zone));
        if (offset < 1 || offset > MAX_FIRST_REVIEW_OFFSET_DAYS) {
            return false;
        }
        if (!pending.getScheduledAt().equals(StudyDay.startOf(calendarDate.plusDays(offset), zone))) {
            // 04:00 정각이 아니다 — 옛 규칙이 놓은 자리가 아니라 누가 옮긴 것이다.
            return false;
        }

        pending.reschedule(pending.getIntervalDays(), pending.getEaseFactor(),
                StudyDay.startOf(studyDay.plusDays(offset), zone));
        return true;
    }
}
