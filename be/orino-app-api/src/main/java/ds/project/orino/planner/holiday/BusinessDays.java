package ds.project.orino.planner.holiday;

import ds.project.orino.domain.planner.holiday.repository.HolidayRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * 영업일 보정. 카드 결제일과 정기 항목이 함께 쓴다.
 *
 * <p>공휴일 자료는 플래너가 이미 갖고 있다 — <b>새 외부 API를 들이지 않는다</b>(D-3).
 * #1148이 세운 하드캡에 새 항목이 붙지 않는 것이 이 재사용의 요점이다.
 *
 * <p>보정 방향은 <b>앞으로</b>다. 결제일이 주말·공휴일이면 카드사는 그 <b>앞</b> 영업일에
 * 출금한다 — 뒤로 미루면 실제보다 늦은 날짜를 보여주게 되고, 「아직 안 빠졌네」로 읽힌다.
 */
@Component
public class BusinessDays {

    /** 공휴일이 연달아도 이 안에서 끝난다. 무한 루프를 막는 안전장치이기도 하다. */
    private static final int MAX_SHIFT_DAYS = 14;

    private final HolidayRepository holidayRepository;

    public BusinessDays(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    /** 그날이 영업일이면 그대로, 아니면 <b>앞</b>으로 당긴 첫 영업일. */
    @Transactional(readOnly = true)
    public LocalDate previousBusinessDayOrSame(LocalDate date) {
        Set<LocalDate> holidays = holidaysAround(date);
        LocalDate cursor = date;
        for (int i = 0; i < MAX_SHIFT_DAYS; i++) {
            if (isBusinessDay(cursor, holidays)) {
                return cursor;
            }
            cursor = cursor.minusDays(1);
        }
        // 2주 내내 공휴일일 수는 없다. 그래도 여기 닿으면 원래 날짜를 그대로 쓴다 —
        // 보정에 실패했다고 결제일 자체를 잃을 수는 없다.
        return date;
    }

    /**
     * 그날이 영업일이면 그대로, 아니면 <b>뒤</b>로 미룬 첫 영업일.
     *
     * <p>카드 결제일에는 쓰지 않는다. 정기 항목이 「다음 영업일」 정책을 고를 수 있어서
     * 있는 방향이다 — 실제로 그렇게 빠지는 자동이체가 있다(확정 명세 §6.2).
     */
    @Transactional(readOnly = true)
    public LocalDate nextBusinessDayOrSame(LocalDate date) {
        Set<LocalDate> holidays = holidaysForward(date);
        LocalDate cursor = date;
        for (int i = 0; i < MAX_SHIFT_DAYS; i++) {
            if (isBusinessDay(cursor, holidays)) {
                return cursor;
            }
            cursor = cursor.plusDays(1);
        }
        return date;
    }

    private boolean isBusinessDay(LocalDate date, Set<LocalDate> holidays) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY
                && date.getDayOfWeek() != DayOfWeek.SUNDAY
                && !holidays.contains(date);
    }

    /** 한 번에 읽어 둔다 — 하루씩 물어보면 연휴마다 질의가 늘어난다. */
    private Set<LocalDate> holidaysAround(LocalDate date) {
        return holidaysBetween(date.minusDays(MAX_SHIFT_DAYS), date);
    }

    private Set<LocalDate> holidaysForward(LocalDate date) {
        return holidaysBetween(date, date.plusDays(MAX_SHIFT_DAYS));
    }

    private Set<LocalDate> holidaysBetween(LocalDate from, LocalDate to) {
        Set<LocalDate> dates = new HashSet<>();
        holidayRepository.findByDateBetween(from, to)
                .forEach(holiday -> dates.add(holiday.getDate()));
        return dates;
    }
}
