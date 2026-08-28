package ds.project.orino.planner.ledger;

import ds.project.orino.domain.planner.ledger.entity.LedgerAmountType;
import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerFrequencyType;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurring;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurringKind;
import ds.project.orino.planner.ledger.recurring.LedgerRecurrence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주기 전개(#1263). <b>스프링도 DB도 시계도 타지 않는 순수 계산</b>이다.
 *
 * <p>회차를 저장하지 않기로 한 이상(D-5) 이 계산이 곧 예정이고, 예상 잔액이고, 자동 기록이다.
 * 여기가 틀리면 셋이 함께 틀린다 — 그래서 말일·윤년·정지 구간·종료일을 경계까지 못박는다.
 *
 * <p>영업일 보정은 여기 없다. 그건 공휴일 자료를 읽어야 하고, 무엇보다 <b>회차의 키가
 * 아니다</b> — 공휴일 자료가 늦게 갱신돼도 이미 적힌 회차가 흔들리면 안 된다.
 */
class LedgerRecurrenceTest {

    private static LedgerRecurring rule(LedgerFrequencyType type, LocalDate start,
                                        Integer interval, Integer day, Integer month) {
        LedgerRecurring rule = new LedgerRecurring(1L, "구독", LedgerRecurringKind.SUBSCRIPTION,
                LedgerFlow.EXPENSE, 12000, LedgerAmountType.FIXED, 1L, type, start, start);
        rule.updateRule(type, interval, day, month);
        return rule;
    }

    @Nested
    @DisplayName("여섯 가지 주기")
    class Frequencies {

        @Test
        @DisplayName("매주 — 시작일 이후 첫 해당 요일부터 7일 간격")
        void weekly() {
            // 2026-08-03은 월요일. 수요일(3) 구독이면 8/5부터다.
            LedgerRecurring rule = rule(LedgerFrequencyType.WEEKLY,
                    LocalDate.of(2026, 8, 3), null, 3, null);

            assertThat(LedgerRecurrence.occurrences(rule,
                    LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
                    .containsExactly(
                            LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 12),
                            LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 26));
        }

        @Test
        @DisplayName("매월 N일")
        void monthlyDay() {
            LedgerRecurring rule = rule(LedgerFrequencyType.MONTHLY_DAY,
                    LocalDate.of(2026, 1, 1), null, 25, null);

            assertThat(LedgerRecurrence.occurrences(rule,
                    LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 31)))
                    .containsExactly(LocalDate.of(2026, 3, 25),
                            LocalDate.of(2026, 4, 25), LocalDate.of(2026, 5, 25));
        }

        @Test
        @DisplayName("매월 말일 — 2월은 28일, 4월은 30일")
        void monthlyLast() {
            LedgerRecurring rule = rule(LedgerFrequencyType.MONTHLY_LAST,
                    LocalDate.of(2026, 1, 1), null, null, null);

            assertThat(LedgerRecurrence.occurrences(rule,
                    LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 30)))
                    .containsExactly(LocalDate.of(2026, 2, 28),
                            LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30));
        }

        @Test
        @DisplayName("N개월마다 — 시작월을 기준으로 센다")
        void everyNMonths() {
            LedgerRecurring rule = rule(LedgerFrequencyType.EVERY_N_MONTHS,
                    LocalDate.of(2026, 2, 10), 3, 10, null);

            assertThat(LedgerRecurrence.occurrences(rule,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                    .containsExactly(LocalDate.of(2026, 2, 10), LocalDate.of(2026, 5, 10),
                            LocalDate.of(2026, 8, 10), LocalDate.of(2026, 11, 10));
        }

        @Test
        @DisplayName("매년 M월 N일")
        void yearly() {
            LedgerRecurring rule = rule(LedgerFrequencyType.YEARLY,
                    LocalDate.of(2025, 11, 3), null, 3, 11);

            assertThat(LedgerRecurrence.occurrences(rule,
                    LocalDate.of(2025, 1, 1), LocalDate.of(2027, 12, 31)))
                    .containsExactly(LocalDate.of(2025, 11, 3),
                            LocalDate.of(2026, 11, 3), LocalDate.of(2027, 11, 3));
        }

        @Test
        @DisplayName("N일마다 — 시작일부터 센다")
        void everyNDays() {
            LedgerRecurring rule = rule(LedgerFrequencyType.EVERY_N_DAYS,
                    LocalDate.of(2026, 8, 1), 10, null, null);

            assertThat(LedgerRecurrence.occurrences(rule,
                    LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
                    .containsExactly(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 11),
                            LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 31));
        }
    }

    @Nested
    @DisplayName("없는 날짜")
    class MissingDays {

        /**
         * 31일 구독은 2월에 <b>28일</b>에 빠진다. 그 달을 건너뛰면 1년에 다섯 달치가 사라지고,
         * 3월 1일로 미루면 3월 예산에 두 번 잡힌다.
         */
        @Test
        @DisplayName("31일 구독은 없는 달에 말일로 내려온다")
        void clampsToLastDay() {
            LedgerRecurring rule = rule(LedgerFrequencyType.MONTHLY_DAY,
                    LocalDate.of(2026, 1, 31), null, 31, null);

            assertThat(LedgerRecurrence.occurrences(rule,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30)))
                    .containsExactly(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28),
                            LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30));
        }

        @Test
        @DisplayName("윤년이면 2월 29일에 그대로 잡힌다")
        void leapYear() {
            LedgerRecurring rule = rule(LedgerFrequencyType.MONTHLY_DAY,
                    LocalDate.of(2028, 1, 31), null, 31, null);

            assertThat(LedgerRecurrence.occurrences(rule,
                    LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)))
                    .containsExactly(LocalDate.of(2028, 2, 29));
        }

        @Test
        @DisplayName("2월 29일 연납은 평년에 28일로 내려온다")
        void yearlyLeapDay() {
            LedgerRecurring rule = rule(LedgerFrequencyType.YEARLY,
                    LocalDate.of(2028, 2, 29), null, 29, 2);

            assertThat(LedgerRecurrence.occurrences(rule,
                    LocalDate.of(2028, 1, 1), LocalDate.of(2029, 12, 31)))
                    .containsExactly(LocalDate.of(2028, 2, 29), LocalDate.of(2029, 2, 28));
        }
    }

    @Nested
    @DisplayName("살아 있는 구간")
    class Window {

        @Test
        @DisplayName("시작일 이전으로는 거슬러 올라가지 않는다")
        void neverBeforeStart() {
            LedgerRecurring rule = rule(LedgerFrequencyType.MONTHLY_DAY,
                    LocalDate.of(2026, 6, 15), null, 15, null);

            assertThat(LedgerRecurrence.occurrences(rule,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 31)))
                    .containsExactly(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 7, 15));
        }

        @Test
        @DisplayName("종료일 뒤 회차는 나오지 않는다")
        void stopsAtEndDate() {
            LedgerRecurring rule = rule(LedgerFrequencyType.MONTHLY_DAY,
                    LocalDate.of(2026, 1, 10), null, 10, null);
            rule.updatePeriod(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 3, 31));

            assertThat(LedgerRecurrence.occurrences(rule,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                    .containsExactly(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 10),
                            LocalDate.of(2026, 3, 10));
        }

        /** 정지는 <b>구간</b>이다. 상태만 보면 「그때는 쉬고 있었나」에 답할 수 없다. */
        @Test
        @DisplayName("정지 구간의 회차만 빠지고 그 뒤는 다시 나온다")
        void skipsPausedRange() {
            LedgerRecurring rule = rule(LedgerFrequencyType.MONTHLY_DAY,
                    LocalDate.of(2026, 1, 10), null, 10, null);
            rule.pause(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 31));

            assertThat(LedgerRecurrence.occurrences(rule,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30)))
                    .containsExactly(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 4, 10));
        }

        @Test
        @DisplayName("해지일 당일부터는 나오지 않는다")
        void stopsAtEndedOn() {
            LedgerRecurring rule = rule(LedgerFrequencyType.MONTHLY_DAY,
                    LocalDate.of(2026, 1, 10), null, 10, null);
            rule.end(LocalDate.of(2026, 3, 10));

            assertThat(LedgerRecurrence.occurrences(rule,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                    .containsExactly(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 10));
        }

        @Test
        @DisplayName("다음 결제일은 오늘 이후 첫 회차다")
        void nextOccurrence() {
            LedgerRecurring rule = rule(LedgerFrequencyType.MONTHLY_DAY,
                    LocalDate.of(2026, 1, 25), null, 25, null);

            assertThat(LedgerRecurrence.next(rule, LocalDate.of(2026, 8, 26)))
                    .isEqualTo(LocalDate.of(2026, 9, 25));
        }
    }

    @Nested
    @DisplayName("월 환산")
    class MonthlyEquivalent {

        /** 연간 구독을 그대로 더하면 1월에만 고정비가 폭증한 것처럼 보인다. */
        @Test
        @DisplayName("연간 구독은 ÷12")
        void yearlyDividedByTwelve() {
            LedgerRecurring rule = rule(LedgerFrequencyType.YEARLY,
                    LocalDate.of(2026, 1, 1), null, 1, 1);
            rule.updateAmount(120000);

            assertThat(LedgerRecurrence.monthlyEquivalent(rule)).isEqualTo(10000);
        }

        /** 4주로 세면 1년에 한 달치가 사라진다. 52주 ÷ 12개월이 맞다. */
        @Test
        @DisplayName("매주는 52주 기준으로 환산한다")
        void weeklyUsesFiftyTwoWeeks() {
            LedgerRecurring rule = rule(LedgerFrequencyType.WEEKLY,
                    LocalDate.of(2026, 1, 1), null, 4, null);
            rule.updateAmount(3000);

            assertThat(LedgerRecurrence.monthlyEquivalent(rule)).isEqualTo(13000);
        }

        @Test
        @DisplayName("3개월마다는 ÷3")
        void everyNMonths() {
            LedgerRecurring rule = rule(LedgerFrequencyType.EVERY_N_MONTHS,
                    LocalDate.of(2026, 1, 1), 3, 1, null);
            rule.updateAmount(30000);

            assertThat(LedgerRecurrence.monthlyEquivalent(rule)).isEqualTo(10000);
        }
    }

    @Nested
    @DisplayName("규칙이 반쪽이면 저장 시점에 막는다")
    class Completeness {

        /** 새벽에 조용히 아무것도 안 적히는 것이 가장 나쁜 실패다. */
        @Test
        @DisplayName("N개월마다인데 간격이 없으면 불완전하다")
        void everyNMonthsNeedsInterval() {
            assertThat(LedgerRecurrence.isComplete(
                    LedgerFrequencyType.EVERY_N_MONTHS, null, 10, null)).isFalse();
            assertThat(LedgerRecurrence.isComplete(
                    LedgerFrequencyType.EVERY_N_MONTHS, 3, 10, null)).isTrue();
        }

        @Test
        @DisplayName("매주인데 요일이 범위를 벗어나면 불완전하다")
        void weeklyNeedsDayOfWeek() {
            assertThat(LedgerRecurrence.isComplete(
                    LedgerFrequencyType.WEEKLY, null, 8, null)).isFalse();
            assertThat(LedgerRecurrence.isComplete(
                    LedgerFrequencyType.WEEKLY, null, 7, null)).isTrue();
        }

        @Test
        @DisplayName("매월 말일은 부속 값이 필요 없다")
        void monthlyLastNeedsNothing() {
            assertThat(LedgerRecurrence.isComplete(
                    LedgerFrequencyType.MONTHLY_LAST, null, null, null)).isTrue();
        }
    }

    @Test
    @DisplayName("빈 구간을 물으면 빈 목록이 온다")
    void emptyRange() {
        LedgerRecurring rule = rule(LedgerFrequencyType.MONTHLY_DAY,
                LocalDate.of(2026, 1, 10), null, 10, null);

        List<LocalDate> dates = LedgerRecurrence.occurrences(rule,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 30));

        assertThat(dates).isEmpty();
    }
}
