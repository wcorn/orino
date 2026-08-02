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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@Import(FixedClockConfig.class)
@DisplayName("복습 일정 백필 (#1001 간격 · #1003 학습일)")
class ReviewScheduleBackfillServiceTest extends ApiTestSupport {

    private static final BigDecimal INITIAL_EASE = new BigDecimal("2.50");

    @Autowired
    private ReviewScheduleBackfillService backfillService;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        // 3회차 완료(HARD, 직전 6일·ease 2.50) → 옛 규칙은 15일, 새 규칙은 max(round(6×1.2), 7) = 7일
        Pair pair = givenCompletedThenPending(Rating.HARD, 3, 6, INITIAL_EASE);

        assertThat(backfillService.run()).isEqualTo(1);

        ReviewSchedule pending = reload(pair.pendingId());
        assertThat(pending.getIntervalDays()).isEqualTo(7);
        assertThat(pending.getEaseFactor()).isEqualByComparingTo("2.35");
        assertThat(pending.getScheduledAt()).isEqualTo(
                ReviewSchedule.computeScheduledAt(Rating.HARD, 7, pair.completedAt(), TEST_ZONE));
    }

    @Test
    @DisplayName("EASY로 잡힌 일정은 새 규칙(직전×ease×1.3)으로 밀린다")
    void easy_pending_is_pushed_out() {
        // 옛 규칙 15일 → 새 규칙 max(round(6×2.50×1.3), good+1) = 20일. ease도 2.60 → 2.65
        Pair pair = givenCompletedThenPending(Rating.EASY, 3, 6, INITIAL_EASE);

        assertThat(backfillService.run()).isEqualTo(1);

        ReviewSchedule pending = reload(pair.pendingId());
        assertThat(pending.getIntervalDays()).isEqualTo(20);
        assertThat(pending.getEaseFactor()).isEqualByComparingTo("2.65");
    }

    @Test
    @DisplayName("고정 단계가 사라져 GOOD으로 잡힌 일정도 당겨진다")
    void good_pending_is_pulled_in() {
        // 2회차(직전 1일)를 GOOD으로 완료 → 옛 규칙은 고정 6일, 새 규칙은 max(round(1×2.50), hard+1) = 3일
        Pair pair = givenCompletedThenPending(Rating.GOOD, 2, 1, INITIAL_EASE);

        assertThat(backfillService.run()).isEqualTo(1);

        ReviewSchedule pending = reload(pair.pendingId());
        assertThat(pending.getIntervalDays()).isEqualTo(3);
        assertThat(pending.getEaseFactor()).isEqualByComparingTo("2.50");
    }

    @Test
    @DisplayName("AGAIN으로 잡힌 일정은 새 규칙에서도 값이 같아 건드리지 않는다")
    void again_is_untouched() {
        Pair again = givenCompletedThenPending(Rating.AGAIN, 3, 6, INITIAL_EASE);

        assertThat(backfillService.run()).isZero();

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

    @Nested
    @DisplayName("학습일 경계 (#1003) — 자정~04:00에 잡힌 일정")
    class StudyDayBoundary {

        @Test
        @DisplayName("새벽 1시에 만든 카드의 첫 복습이 하루 당겨진다")
        void first_review_created_before_dawn_is_pulled_in() {
            LocalDate today = testToday(clock);
            // 옛 규칙: 새벽 1시를 그날(D)로 쳐서 첫 복습을 D+1 04:00에 잡아뒀다
            ReviewSchedule first = givenFirstReviewCreatedAt(
                    atTestZone(today.minusDays(1).atTime(1, 0)), today.minusDays(1).plusDays(1));

            assertThat(backfillService.run()).isEqualTo(1);

            // 학습일은 D-1이므로 첫 복습도 하루 앞(D)으로 온다
            assertThat(reload(first.getId()).getScheduledAt())
                    .isEqualTo(atTestZone(today.minusDays(1).atTime(4, 0)));
        }

        @Test
        @DisplayName("낮에 만든 카드의 첫 복습은 그대로 둔다")
        void first_review_created_in_daytime_is_untouched() {
            LocalDate today = testToday(clock);
            ReviewSchedule first = givenFirstReviewCreatedAt(
                    atTestZone(today.minusDays(1).atTime(14, 0)), today);

            assertThat(backfillService.run()).isZero();

            assertThat(reload(first.getId()).getScheduledAt())
                    .isEqualTo(atTestZone(today.atTime(4, 0)));
        }

        @Test
        @DisplayName("새벽 1시에 채점한 복습의 다음 일정도 하루 당겨진다 (간격은 그대로)")
        void completion_before_dawn_is_pulled_in() {
            LocalDate today = testToday(clock);
            // 직전 6일·ease 2.50을 GOOD으로 → 간격 15일은 옛/새 규칙이 같고, 날짜만 어긋나 있다
            Pair pair = givenCompletedThenPending(
                    Rating.GOOD, 3, 6, INITIAL_EASE, today.minusDays(10).atTime(1, 0));

            assertThat(backfillService.run()).isEqualTo(1);

            ReviewSchedule pending = reload(pair.pendingId());
            assertThat(pending.getIntervalDays()).isEqualTo(15);
            // 학습일 기준 D-11 + 15일 (옛 규칙은 D-10 + 15일이었다)
            assertThat(pending.getScheduledAt())
                    .isEqualTo(atTestZone(today.minusDays(11).plusDays(15).atTime(4, 0)));
        }

        @Test
        @DisplayName("두 번 돌려도 한 번만 당긴다 (멱등)")
        void is_idempotent() {
            LocalDate today = testToday(clock);
            ReviewSchedule first = givenFirstReviewCreatedAt(
                    atTestZone(today.minusDays(1).atTime(1, 0)), today);

            assertThat(backfillService.run()).isEqualTo(1);
            assertThat(backfillService.run()).isZero();

            assertThat(reload(first.getId()).getScheduledAt())
                    .isEqualTo(atTestZone(today.minusDays(1).atTime(4, 0)));
        }
    }

    /**
     * 카드 생성분(1회차) PENDING을 심는다. {@code createdAt}은 감사 필드라 저장 후 직접 덮어쓴다
     * (JPA auditing이 {@code Instant.now()}를 쓰므로 고정 시계로는 제어할 수 없다).
     */
    private ReviewSchedule givenFirstReviewCreatedAt(Instant createdAt, LocalDate scheduledDate) {
        Flashcard card = flashcardRepository.save(
                new Flashcard(member.getId(), material.getId(), "Q", "A"));
        ReviewSchedule first = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 1,
                atTestZone(scheduledDate.atTime(4, 0)), 1, INITIAL_EASE));

        // Hibernate는 hibernate.jdbc.time_zone=UTC로 쓰는데 JdbcTemplate의 Timestamp는 JVM 존을 타므로
        // UTC 벽시계 값으로 명시해 넣는다(안 그러면 9시간 어긋난다).
        jdbcTemplate.update("UPDATE review_schedule SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC)), first.getId());
        assertThat(reload(first.getId()).getCreatedAt()).isEqualTo(createdAt);
        return first;
    }

    private Pair givenCompletedThenPending(Rating rating, int pendingSequence,
                                           int prevInterval, BigDecimal prevEase) {
        LocalDate today = testToday(clock);
        return givenCompletedThenPending(rating, pendingSequence, prevInterval, prevEase,
                today.minusDays(1).atTime(9, 0));
    }

    /**
     * 옛 규칙이 만들어냈을 상태를 그대로 심는다 — {@code sequence-1} 회차를 {@code rating}으로 완료하고,
     * 거기서 옛 규칙(순정 SM-2 간격 + <b>달력 날짜</b> 경계)이 계산한 자리에 다음 회차 PENDING을 잡아둔 모양.
     */
    private Pair givenCompletedThenPending(Rating rating, int pendingSequence,
                                           int prevInterval, BigDecimal prevEase,
                                           LocalDateTime completedLocal) {
        Flashcard card = flashcardRepository.save(
                new Flashcard(member.getId(), material.getId(), "Q", "A"));

        Instant completedAt = atTestZone(completedLocal);
        ReviewSchedule completed = new ReviewSchedule(member.getId(), card.getId(),
                pendingSequence - 1, completedAt, prevInterval, prevEase);
        completed.complete(rating, completedAt, TEST_ZONE);
        reviewScheduleRepository.save(completed);

        int legacyInterval = Sm2Calculator.legacyIntervalDays(
                pendingSequence, prevInterval, prevEase, rating);
        // 옛 경계는 달력 날짜였다 — 학습일이 아니라 completedAt의 날짜에 간격을 더한다.
        Instant legacyScheduledAt = rating == Rating.AGAIN
                ? completedAt.plusSeconds(ReviewSchedule.RELEARN_MINUTES * 60L)
                : atTestZone(completedLocal.toLocalDate().plusDays(legacyInterval).atTime(4, 0));
        ReviewSchedule pending = reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), pendingSequence, legacyScheduledAt,
                legacyInterval, Sm2Calculator.legacyEaseFactor(prevEase, rating)));

        return new Pair(pending.getId(), completedAt);
    }

    private ReviewSchedule reload(Long id) {
        return reviewScheduleRepository.findById(id).orElseThrow();
    }

    private record Pair(Long pendingId, Instant completedAt) {
    }
}
