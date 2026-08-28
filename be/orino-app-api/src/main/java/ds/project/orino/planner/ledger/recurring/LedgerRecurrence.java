package ds.project.orino.planner.ledger.recurring;

import ds.project.orino.domain.planner.ledger.entity.LedgerFrequencyType;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurring;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 주기를 날짜로 편다. <b>순수 함수다</b> — 스프링도, DB도, 시계도 타지 않는다.
 *
 * <p>회차를 저장하지 않고 매번 계산하기로 한 이상(D-5) 이 계산이 틀리면 예정·예상 잔액·
 * 자동 기록이 함께 틀린다. 그래서 여기에 상태를 두지 않는다 — 고정 입력만 주면 언제 돌려도
 * 같은 답이 나오고, 말일·윤년·정지 구간을 경계까지 단위 테스트로 못박을 수 있다.
 *
 * <p><b>영업일 보정은 여기서 하지 않는다.</b> 이 함수가 내는 것은 「규칙이 계산한 원래
 * 예정일」이고 그 값이 회차의 키다. 보정은 공휴일 자료를 읽어야 하므로 스프링 빈
 * ({@code BusinessDays})의 몫이고, 보정된 날짜는 <b>실제로 언제 빠지는가</b>일 뿐이다.
 * 보정 결과를 키로 삼으면 공휴일 자료가 늦게 갱신될 때 같은 회차가 두 번 적힌다.
 */
public final class LedgerRecurrence {

    /** 한 번의 전개가 낼 수 있는 최대 회차. 잘못된 규칙이 무한 루프가 되지 않게 하는 상한이다. */
    static final int MAX_OCCURRENCES = 1000;

    private LedgerRecurrence() {
    }

    /**
     * {@code [from, to]} 구간의 회차. 정지 구간·종료일·해지일에 걸린 날짜는 빠진다.
     *
     * <p>시작일 이전으로는 절대 거슬러 올라가지 않는다.
     */
    public static List<LocalDate> occurrences(LedgerRecurring rule, LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();
        if (to.isBefore(from)) {
            return dates;
        }
        LocalDate lower = from.isBefore(rule.getStartDate()) ? rule.getStartDate() : from;
        for (LocalDate candidate : candidates(rule, lower, to)) {
            if (candidate.isBefore(lower) || candidate.isAfter(to)) {
                continue;
            }
            if (rule.isActiveOn(candidate)) {
                dates.add(candidate);
            }
        }
        return dates;
    }

    /** {@code from} 이후 첫 회차. 「다음 결제일」이다. 없으면 {@code null}. */
    public static LocalDate next(LedgerRecurring rule, LocalDate from) {
        // 매년 주기도 한 번은 나오도록 넉넉히 본다. 그래도 안 나오면 종료된 규칙이다.
        List<LocalDate> dates = occurrences(rule, from, from.plusYears(2));
        return dates.isEmpty() ? null : dates.get(0);
    }

    /**
     * 월 환산액. 「월 고정비 총액」과 연 환산이 이 값을 더한다(확정 명세 §6.6).
     *
     * <p>연간 구독을 그대로 더하면 1월에만 고정비가 폭증한 것처럼 보이고, 반대로 빼면
     * 없는 셈이 된다. 나눠서 매달 얹는 편이 「이 돈이 매달 나가는 셈이다」라는 실감에 맞다.
     */
    public static long monthlyEquivalent(LedgerRecurring rule) {
        int interval = rule.getFreqInterval() == null ? 1 : Math.max(rule.getFreqInterval(), 1);
        long amount = rule.getAmount();
        return switch (rule.getFreqType()) {
            // 4주가 아니라 52주/12개월이다. 4주로 세면 1년에 한 달치가 사라진다.
            case WEEKLY -> Math.round(amount * 52.0 / 12.0);
            case MONTHLY_DAY, MONTHLY_LAST -> amount;
            case EVERY_N_MONTHS -> Math.round((double) amount / interval);
            case YEARLY -> Math.round(amount / 12.0);
            case EVERY_N_DAYS -> Math.round(amount * (365.0 / 12.0) / interval);
        };
    }

    /** 규칙에 필요한 부속 값이 갖춰졌는가. 없으면 새벽에 조용히 아무것도 안 적힌다. */
    public static boolean isComplete(LedgerFrequencyType type, Integer interval,
                                     Integer day, Integer month) {
        return switch (type) {
            case WEEKLY -> day != null && day >= 1 && day <= 7;
            case MONTHLY_DAY -> day != null && day >= 1 && day <= 31;
            case MONTHLY_LAST -> true;
            case EVERY_N_MONTHS -> interval != null && interval >= 1
                    && day != null && day >= 1 && day <= 31;
            case YEARLY -> month != null && month >= 1 && month <= 12
                    && day != null && day >= 1 && day <= 31;
            case EVERY_N_DAYS -> interval != null && interval >= 1;
        };
    }

    private static List<LocalDate> candidates(LedgerRecurring rule, LocalDate lower, LocalDate to) {
        return switch (rule.getFreqType()) {
            case WEEKLY -> weekly(rule, lower, to);
            case MONTHLY_DAY -> monthly(rule, lower, to, 1, dayOf(rule));
            case MONTHLY_LAST -> monthly(rule, lower, to, 1, LAST_DAY);
            case EVERY_N_MONTHS -> monthly(rule, lower, to, intervalOf(rule), dayOf(rule));
            case YEARLY -> yearly(rule, lower, to);
            case EVERY_N_DAYS -> everyNDays(rule, lower, to);
        };
    }

    /** 「말일」의 표식. 31로 두면 30일까지인 달에서 30일과 구별되지 않는다. */
    private static final int LAST_DAY = 99;

    private static List<LocalDate> weekly(LedgerRecurring rule, LocalDate lower, LocalDate to) {
        LocalDate start = rule.getStartDate();
        int target = rule.getFreqDay() == null
                ? start.getDayOfWeek().getValue() : rule.getFreqDay();
        // 시작일 당일 또는 그 뒤 첫 해당 요일.
        LocalDate first = start.plusDays(Math.floorMod(target - start.getDayOfWeek().getValue(), 7));
        return step(first, lower, to, ChronoUnit.WEEKS, 1);
    }

    private static List<LocalDate> everyNDays(LedgerRecurring rule, LocalDate lower, LocalDate to) {
        return step(rule.getStartDate(), lower, to, ChronoUnit.DAYS, intervalOf(rule));
    }

    /** 고정 간격 주기의 공통 계산. {@code lower}까지 한 칸씩 걷지 않고 위상을 계산해 건너뛴다. */
    private static List<LocalDate> step(LocalDate first, LocalDate lower, LocalDate to,
                                        ChronoUnit unit, int interval) {
        List<LocalDate> dates = new ArrayList<>();
        long elapsed = unit.between(first, lower);
        long skip = elapsed <= 0 ? 0 : elapsed / interval;
        LocalDate cursor = first.plus(skip * interval, unit);
        while (cursor.isBefore(lower)) {
            cursor = cursor.plus(interval, unit);
        }
        for (int i = 0; i < MAX_OCCURRENCES && !cursor.isAfter(to); i++) {
            dates.add(cursor);
            cursor = cursor.plus(interval, unit);
        }
        return dates;
    }

    private static List<LocalDate> monthly(LedgerRecurring rule, LocalDate lower, LocalDate to,
                                           int intervalMonths, int day) {
        List<LocalDate> dates = new ArrayList<>();
        YearMonth startMonth = YearMonth.from(rule.getStartDate());
        YearMonth lowerMonth = YearMonth.from(lower);
        long elapsed = startMonth.until(lowerMonth, ChronoUnit.MONTHS);
        long skip = elapsed <= 0 ? 0 : elapsed / intervalMonths;
        YearMonth cursor = startMonth.plusMonths(skip * intervalMonths);
        // 위상 계산이 한 칸 앞설 수 있다. 한 칸 물러서서 시작하면 경계 회차를 놓치지 않는다.
        if (cursor.isAfter(lowerMonth)) {
            cursor = cursor.minusMonths(intervalMonths);
        }
        for (int i = 0; i < MAX_OCCURRENCES; i++) {
            LocalDate date = dayIn(cursor, day);
            if (date.isAfter(to)) {
                break;
            }
            dates.add(date);
            cursor = cursor.plusMonths(intervalMonths);
        }
        return dates;
    }

    private static List<LocalDate> yearly(LedgerRecurring rule, LocalDate lower, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();
        int month = rule.getFreqMonth() == null
                ? rule.getStartDate().getMonthValue() : rule.getFreqMonth();
        int day = dayOf(rule);
        for (int year = rule.getStartDate().getYear(); year <= to.getYear(); year++) {
            LocalDate date = dayIn(YearMonth.of(year, month), day);
            if (!date.isBefore(lower) && !date.isAfter(to)) {
                dates.add(date);
            }
        }
        return dates;
    }

    /**
     * 그 달의 해당 일자. <b>없는 날은 말일로 내려온다.</b>
     *
     * <p>31일 구독은 2월에 28일(윤년 29일)에 빠진다 — 그 달을 건너뛰면 1년에 다섯 달치가
     * 사라지고, 다음 달 1일로 미루면 그 달 예산에 두 번 잡힌다.
     */
    static LocalDate dayIn(YearMonth month, int day) {
        if (day == LAST_DAY) {
            return month.atEndOfMonth();
        }
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }

    private static int dayOf(LedgerRecurring rule) {
        return rule.getFreqDay() == null
                ? rule.getStartDate().getDayOfMonth() : rule.getFreqDay();
    }

    private static int intervalOf(LedgerRecurring rule) {
        return rule.getFreqInterval() == null ? 1 : Math.max(rule.getFreqInterval(), 1);
    }
}
