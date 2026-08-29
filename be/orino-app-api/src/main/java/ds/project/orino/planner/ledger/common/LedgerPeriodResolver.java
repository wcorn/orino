package ds.project.orino.planner.ledger.common;

import ds.project.orino.domain.planner.ledger.entity.LedgerMonthStartWeekendPolicy;
import ds.project.orino.domain.planner.ledger.entity.LedgerSettings;
import ds.project.orino.planner.holiday.BusinessDays;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.UnaryOperator;

/**
 * 설정에 적힌 월 시작일과 주말 보정을 실제 구간으로 바꾼다.
 *
 * <p>계산 자체는 {@link LedgerPeriods}에 순수 함수로 있다. 여기 있는 것은 <b>보정 함수를
 * 고르는 일</b>뿐이고, 공휴일 자료를 읽어야 해서 빈이 되었다(D-3, 재사용).
 *
 * <p>요약·대시보드·예산·통계가 전부 이 하나를 거친다 — 각자 설정을 읽어 각자 계산하면
 * 화면마다 다른 달을 말하게 된다.
 */
@Component
public class LedgerPeriodResolver {

    private final BusinessDays businessDays;

    public LedgerPeriodResolver(BusinessDays businessDays) {
        this.businessDays = businessDays;
    }

    /** {@code anchor}가 속한 구간. */
    public LedgerPeriods.Period containing(LedgerSettings settings, LocalDate anchor) {
        return LedgerPeriods.containing(anchor, settings.getMonthStartDay(), adjuster(settings));
    }

    /** 그 달에 시작하는 구간. */
    public LedgerPeriods.Period of(LedgerSettings settings, YearMonth month) {
        return LedgerPeriods.of(month, settings.getMonthStartDay(), adjuster(settings));
    }

    /**
     * 보정 함수. {@code AS_IS}면 항등이라 <b>공휴일 질의도 하지 않는다.</b>
     *
     * <p>앞으로만 당긴다. 급여일이 주말이면 회사는 그 <b>앞</b> 영업일에 넣는다 —
     * 뒤로 미루면 아직 들어오지 않은 돈이 이번 구간에 있는 것처럼 보인다.
     */
    private UnaryOperator<LocalDate> adjuster(LedgerSettings settings) {
        if (settings.getMonthStartWeekendPolicy() != LedgerMonthStartWeekendPolicy.PREV_BUSINESS_DAY) {
            return UnaryOperator.identity();
        }
        return businessDays::previousBusinessDayOrSame;
    }
}
