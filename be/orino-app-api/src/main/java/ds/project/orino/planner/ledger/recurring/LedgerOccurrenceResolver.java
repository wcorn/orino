package ds.project.orino.planner.ledger.recurring;

import ds.project.orino.domain.planner.ledger.entity.LedgerBusinessDayPolicy;
import ds.project.orino.domain.planner.ledger.entity.LedgerOverrideAction;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurring;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurringOverride;
import ds.project.orino.planner.holiday.BusinessDays;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 규칙 + 손댄 회차 = 실제 회차. D-5의 하이브리드가 합쳐지는 곳이다.
 *
 * <p>{@link LedgerRecurrence}가 낸 「원래 예정일」에 override를 얹고 영업일 보정을 입힌다.
 * 스케줄러와 예정 목록이 <b>같은 함수</b>를 쓴다 — 두 벌로 두면 「예정에 보이는데 안 적힌다」가
 * 생기고, 그건 사람이 배치 로그를 열어봐야만 알 수 있는 종류의 어긋남이다.
 */
@Component
public class LedgerOccurrenceResolver {

    private final BusinessDays businessDays;

    public LedgerOccurrenceResolver(BusinessDays businessDays) {
        this.businessDays = businessDays;
    }

    /**
     * 회차 하나.
     *
     * @param occurrenceDate 규칙이 계산한 원래 예정일. 언제나 이 값이 키다
     * @param date           실제로 잡히는 날. 이동·영업일 보정이 반영된 값이다
     * @param action         사람이 손댔으면 그 행위, 아니면 {@code null}
     */
    public record Occurrence(LocalDate occurrenceDate, LocalDate date, long amount,
                             LedgerOverrideAction action) {

        /** 예정 목록에서 사라지는가. <b>미납은 여기 없다</b> — 안 낸 돈은 여전히 내야 할 돈이다. */
        public boolean isHidden() {
            return action != null && action.hidesFromUpcoming();
        }

        public boolean isUnpaid() {
            return action == LedgerOverrideAction.UNPAID;
        }
    }

    /** {@code [from, to]} 구간의 회차. 숨겨진 회차도 담아 내보낸다 — 거르는 쪽이 정한다. */
    public List<Occurrence> resolve(LedgerRecurring rule,
                                    Map<LocalDate, LedgerRecurringOverride> overrides,
                                    LocalDate from, LocalDate to) {
        List<Occurrence> result = new ArrayList<>();
        for (LocalDate occurrenceDate : LedgerRecurrence.occurrences(rule, from, to)) {
            LedgerRecurringOverride override = overrides.get(occurrenceDate);
            long amount = override != null && override.getAmount() != null
                    ? override.getAmount() : rule.getAmount();
            // 사람이 옮긴 날짜가 영업일 보정보다 우선한다. 실제로 그날 빠졌다는 사실이니까.
            LocalDate date = override != null && override.getMovedTo() != null
                    ? override.getMovedTo() : adjust(rule, occurrenceDate);
            result.add(new Occurrence(occurrenceDate, date, amount,
                    override == null ? null : override.getAction()));
        }
        return result;
    }

    /** 주말·공휴일 보정. 정책이 {@code AS_IS}면 아무것도 하지 않는다(질의도 하지 않는다). */
    public LocalDate adjust(LedgerRecurring rule, LocalDate date) {
        LedgerBusinessDayPolicy policy = rule.getBusinessDayPolicy();
        if (policy == LedgerBusinessDayPolicy.PREV) {
            return businessDays.previousBusinessDayOrSame(date);
        }
        if (policy == LedgerBusinessDayPolicy.NEXT) {
            return businessDays.nextBusinessDayOrSame(date);
        }
        return date;
    }
}
