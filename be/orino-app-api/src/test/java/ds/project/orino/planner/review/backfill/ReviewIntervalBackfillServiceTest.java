package ds.project.orino.planner.review.backfill;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.repository.FlashcardRepository;
import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.Rating;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.FixedClockConfig;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.planner.review.sm2.Sm2Calculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(FixedClockConfig.class)
@DisplayName("복습 간격 규칙 백필 (#1001)")
class ReviewIntervalBackfillServiceTest extends ApiTestSupport {

    private static final BigDecimal INITIAL_EASE = new BigDecimal("2.50");

    @Autowired
    private ReviewIntervalBackfillService backfillService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudyMaterialRepository studyMaterialRepository;

    @Autowired
    private FlashcardRepository flashcardRepository;

    @Autowired
    private ReviewScheduleRepository reviewScheduleRepository;

    @Autowired
    private DbCleaner dbCleaner;

    @Autowired
    private Clock clock;

    private Member member;
    private StudyMaterial material;

    @BeforeEach
    void setUp() {
        dbCleaner.clean();
        member = memberRepository.save(MemberFixture.create());
        material = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "자료", MaterialType.BOOK));
    }

    @Test
    @DisplayName("HARD로 잡힌 일정은 새 규칙(직전×1.2)으로 당겨진다")
    void hard_pending_is_pulled_in() {
        // 3회차 완료(HARD, 직전 6일·ease 2.50) → 옛 규칙은 15일, 새 규칙은 7일
        Pair pair = givenCompletedThenPending(Rating.HARD, 3, 6, INITIAL_EASE);

        assertThat(backfillService.run()).isEqualTo(1);

        ReviewSchedule pending = reload(pair.pendingId());
        assertThat(pending.getIntervalDays()).isEqualTo(7);
        assertThat(pending.getEaseFactor()).isEqualByComparingTo("2.35");
        assertThat(pending.getScheduledAt()).isEqualTo(
                ReviewSchedule.computeScheduledAt(Rating.HARD, 7, pair.completedAt(), TEST_ZONE));
    }

    @Test
    @DisplayName("EASY로 잡힌 일정은 새 규칙(Good×1.3)으로 밀린다")
    void easy_pending_is_pushed_out() {
        Pair pair = givenCompletedThenPending(Rating.EASY, 3, 6, INITIAL_EASE);

        assertThat(backfillService.run()).isEqualTo(1);

        ReviewSchedule pending = reload(pair.pendingId());
        assertThat(pending.getIntervalDays()).isEqualTo(21);
        assertThat(pending.getEaseFactor()).isEqualByComparingTo("2.65");
    }

    @Test
    @DisplayName("GOOD·AGAIN으로 잡힌 일정은 새 규칙에서도 값이 같아 건드리지 않는다")
    void good_and_again_are_untouched() {
        Pair good = givenCompletedThenPending(Rating.GOOD, 3, 6, INITIAL_EASE);
        Pair again = givenCompletedThenPending(Rating.AGAIN, 3, 6, INITIAL_EASE);

        assertThat(backfillService.run()).isZero();

        assertThat(reload(good.pendingId()).getIntervalDays()).isEqualTo(15);
        assertThat(reload(again.pendingId()).getIntervalDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("두 번 돌려도 한 번만 반영된다 (멱등)")
    void is_idempotent() {
        Pair pair = givenCompletedThenPending(Rating.HARD, 3, 6, INITIAL_EASE);

        assertThat(backfillService.run()).isEqualTo(1);
        assertThat(backfillService.run()).isZero();

        assertThat(reload(pair.pendingId()).getIntervalDays()).isEqualTo(7);
    }

    @Test
    @DisplayName("burying으로 옮겨진 일정은 되돌리지 않는다")
    void buried_pending_is_left_alone() {
        Pair pair = givenCompletedThenPending(Rating.HARD, 3, 6, INITIAL_EASE);
        ReviewSchedule pending = reload(pair.pendingId());
        Instant buriedAt = pending.getScheduledAt().plusSeconds(86_400);
        pending.reschedule(pending.getIntervalDays(), pending.getEaseFactor(), buriedAt);
        reviewScheduleRepository.save(pending);

        assertThat(backfillService.run()).isZero();

        assertThat(reload(pair.pendingId()).getScheduledAt()).isEqualTo(buriedAt);
    }

    @Test
    @DisplayName("직전 회차가 없는 1회차 일정(카드 생성분)은 건드리지 않는다")
    void first_review_without_predecessor_is_untouched() {
        LocalDate today = testToday(clock);
        Flashcard card = flashcardRepository.save(
                new Flashcard(member.getId(), material.getId(), "Q", "A"));
        ReviewSchedule first = reviewScheduleRepository.save(
                ReviewSchedule.firstReview(member.getId(), card.getId(), today, TEST_ZONE));

        assertThat(backfillService.run()).isZero();

        assertThat(reload(first.getId()).getScheduledAt()).isEqualTo(first.getScheduledAt());
    }

    /**
     * 옛 규칙이 만들어냈을 상태를 그대로 심는다 — {@code sequence-1} 회차를 {@code rating}으로 완료하고,
     * 거기서 옛 규칙이 계산한 간격으로 다음 회차 PENDING을 잡아둔 모양.
     */
    private Pair givenCompletedThenPending(Rating rating, int pendingSequence,
                                           int prevInterval, BigDecimal prevEase) {
        LocalDate today = testToday(clock);
        Flashcard card = flashcardRepository.save(
                new Flashcard(member.getId(), material.getId(), "Q", "A"));

        Instant completedAt = atTestZone(today.minusDays(1).atTime(9, 0));
        ReviewSchedule completed = new ReviewSchedule(member.getId(), card.getId(),
                pendingSequence - 1, completedAt, prevInterval, prevEase);
        completed.complete(rating, completedAt, TEST_ZONE);
        reviewScheduleRepository.save(completed);

        int legacyInterval = Sm2Calculator.legacyIntervalDays(
                pendingSequence, prevInterval, prevEase, rating);
        BigDecimal legacyEase = legacyEase(prevEase, rating);
        ReviewSchedule pending = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), pendingSequence,
                ReviewSchedule.computeScheduledAt(rating, legacyInterval, completedAt, TEST_ZONE),
                legacyInterval, legacyEase));

        return new Pair(pending.getId(), completedAt);
    }

    /** 옛 규칙의 ease 갱신폭(AGAIN -0.20 / HARD -0.14 / GOOD 0 / EASY +0.10). */
    private static BigDecimal legacyEase(BigDecimal prevEase, Rating rating) {
        BigDecimal delta = switch (rating) {
            case AGAIN -> new BigDecimal("-0.20");
            case HARD -> new BigDecimal("-0.14");
            case GOOD -> BigDecimal.ZERO;
            case EASY -> new BigDecimal("0.10");
        };
        BigDecimal next = prevEase.add(delta);
        return next.compareTo(Sm2Calculator.MIN_EASE) < 0 ? Sm2Calculator.MIN_EASE : next;
    }

    private ReviewSchedule reload(Long id) {
        return reviewScheduleRepository.findById(id).orElseThrow();
    }

    private record Pair(Long pendingId, Instant completedAt) {
    }
}
