package ds.project.orino.planner.ledger;

import ds.project.orino.planner.ledger.common.LedgerPeriods;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 월 시작일 기간 계산(`LDG-071`). 순수 계산이라 시계도 DB도 없다.
 *
 * <p>급여일 기준으로 사는 사람에게 「이번 달」은 달력의 달이 아니다. 요약·통계·예산이 전부
 * 이 계산을 쓰므로, 여기가 하루라도 어긋나면 <b>화면마다 다른 달</b>을 말하게 된다.
 */
class LedgerPeriodsTest {

    /** 주말이면 앞 금요일로. 공휴일 자료 없이 주말만 보는 테스트용 보정이다. */
    private static final UnaryOperator<LocalDate> TO_WEEKDAY = date -> {
        LocalDate cursor = date;
        while (cursor.getDayOfWeek() == DayOfWeek.SATURDAY
                || cursor.getDayOfWeek() == DayOfWeek.SUNDAY) {
            cursor = cursor.minusDays(1);
        }
        return cursor;
    };

    @Nested
    @DisplayName("월 시작일")
    class MonthStart {

        @Test
        @DisplayName("1일 시작이면 달력의 달 그대로다")
        void firstOfMonth() {
            LedgerPeriods.Period period =
                    LedgerPeriods.containing(LocalDate.of(2026, 8, 15), 1);

            assertThat(period.start()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(period.end()).isEqualTo(LocalDate.of(2026, 8, 31));
        }

        /** 25일 시작이면 8월 20일은 아직 <b>7월 25일에 시작한 구간</b>이다. */
        @Test
        @DisplayName("25일 시작이면 시작일 전은 지난 구간이다")
        void payday() {
            LedgerPeriods.Period period =
                    LedgerPeriods.containing(LocalDate.of(2026, 8, 20), 25);

            assertThat(period.start()).isEqualTo(LocalDate.of(2026, 7, 25));
            assertThat(period.end()).isEqualTo(LocalDate.of(2026, 8, 24));
        }

        @Test
        @DisplayName("시작일 당일은 새 구간이다")
        void startDayItself() {
            LedgerPeriods.Period period =
                    LedgerPeriods.containing(LocalDate.of(2026, 8, 25), 25);

            assertThat(period.start()).isEqualTo(LocalDate.of(2026, 8, 25));
            assertThat(period.end()).isEqualTo(LocalDate.of(2026, 9, 24));
        }

        /** 구간의 이름은 <b>시작한 달</b>이다 — 「7월 급여로 사는 기간」이 그 이름이다. */
        @Test
        @DisplayName("그 달에 시작하는 구간은 두 달에 걸친다")
        void spansTwoMonths() {
            LedgerPeriods.Period period = LedgerPeriods.of(YearMonth.of(2026, 7), 25);

            assertThat(period.start()).isEqualTo(LocalDate.of(2026, 7, 25));
            assertThat(period.end()).isEqualTo(LocalDate.of(2026, 8, 24));
        }

        @Test
        @DisplayName("말일 시작(99)은 짧은 달에서도 존재하는 날이다")
        void lastDayOfMonth() {
            LedgerPeriods.Period period =
                    LedgerPeriods.containing(LocalDate.of(2026, 3, 1), 99);

            assertThat(period.start()).isEqualTo(LocalDate.of(2026, 2, 28));
            assertThat(period.end()).isEqualTo(LocalDate.of(2026, 3, 30));
        }
    }

    @Nested
    @DisplayName("주말 보정")
    class WeekendShift {

        /** 2026-04-25는 토요일. 급여는 24일(금)에 들어오고 구간도 그날 시작해야 한다. */
        @Test
        @DisplayName("시작일이 주말이면 앞 영업일로 당긴다")
        void pullsStartForward() {
            LedgerPeriods.Period period =
                    LedgerPeriods.containing(LocalDate.of(2026, 5, 1), 25, TO_WEEKDAY);

            assertThat(period.start()).isEqualTo(LocalDate.of(2026, 4, 24));
            assertThat(period.end()).isEqualTo(LocalDate.of(2026, 5, 24));
        }

        /**
         * 보정이 시작일을 당긴 그 하루도 새 구간에 든다. 시작만 당기고 끝을 두면 그날이
         * 두 구간에 동시에 들어가고, 합계가 어느 쪽에서든 한 번씩 더 세어진다.
         */
        @Test
        @DisplayName("당겨진 하루는 새 구간에 든다 — 두 구간이 겹치지 않는다")
        void noOverlapOnShiftedDay() {
            LedgerPeriods.Period period =
                    LedgerPeriods.containing(LocalDate.of(2026, 4, 24), 25, TO_WEEKDAY);

            assertThat(period.start()).isEqualTo(LocalDate.of(2026, 4, 24));
            LedgerPeriods.Period previous =
                    LedgerPeriods.containing(LocalDate.of(2026, 4, 23), 25, TO_WEEKDAY);
            assertThat(previous.end()).isEqualTo(LocalDate.of(2026, 4, 23));
        }

        @Test
        @DisplayName("보정이 달을 넘겨도 구간이 이어진다")
        void shiftCrossesMonth() {
            // 2026-11-01은 일요일 → 10월 30일(금)에 시작한다.
            LedgerPeriods.Period period =
                    LedgerPeriods.containing(LocalDate.of(2026, 10, 31), 1, TO_WEEKDAY);

            assertThat(period.start()).isEqualTo(LocalDate.of(2026, 10, 30));
            assertThat(period.end()).isEqualTo(LocalDate.of(2026, 11, 30));
        }
    }
}
