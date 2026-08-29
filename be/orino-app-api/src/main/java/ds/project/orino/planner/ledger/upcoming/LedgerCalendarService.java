package ds.project.orino.planner.ledger.upcoming;

import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.ledger.common.LedgerClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 캘린더(`LDG-021`) — <b>과거는 확정, 미래는 예정</b>을 각각 내려준다(확정 명세 §8.3).
 *
 * <p>예정 쪽은 {@link LedgerUpcomingService}의 4출처 UNION을 그대로 쓴다. 직접 예약만
 * 세면 예정 목록에는 있는 카드 대금이 캘린더에는 없게 되고, <b>두 화면이 서로 다른 말을 하는
 * 것</b>이 이 모듈에서 가장 나쁜 종류의 버그다.
 *
 * <p>확정 쪽은 원장에서 온다. 상태로 갈라 읽으므로 오늘 하루에 이미 쓴 것과 아직 안 나간 것이
 * 함께 있어도 어긋나지 않는다.
 *
 * <p>구간은 <b>달력 그대로</b>다 — 월 시작일 설정을 여기 적용하지 않는다. 25일 시작으로
 * 산다고 캘린더가 25일부터 시작하면 그건 달력이 아니다.
 */
@Service
public class LedgerCalendarService {

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerUpcomingService upcomingService;
    private final LedgerClock clock;

    public LedgerCalendarService(LedgerTransactionRepository transactionRepository,
                                 LedgerUpcomingService upcomingService,
                                 LedgerClock clock) {
        this.transactionRepository = transactionRepository;
        this.upcomingService = upcomingService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LedgerUpcomingDtos.CalendarResponse calendar(Long memberId, String month) {
        YearMonth target = month == null || month.isBlank()
                ? YearMonth.from(clock.today()) : YearMonth.parse(month);
        LocalDate from = target.atDay(1);
        LocalDate to = target.atEndOfMonth();

        Map<LocalDate, long[]> byDate = new HashMap<>();
        for (LedgerTransactionRepository.DailyFlowTotal row
                : transactionRepository.sumDailyByTypeAndStatus(memberId, from, to)) {
            // 예정은 원장에서 세지 않는다 — 아래 4출처가 담당한다. 둘 다 세면 직접 예약이 두 번 잡힌다.
            if (row.getStatus() != LedgerTransactionStatus.CONFIRMED) {
                continue;
            }
            long[] day = byDate.computeIfAbsent(row.getDate(), key -> new long[5]);
            if (row.getType() == LedgerFlow.INCOME) {
                day[0] += row.getTotal();
            } else if (row.getType() == LedgerFlow.EXPENSE) {
                day[1] += row.getTotal();
            }
        }

        for (LedgerUpcomingDtos.UpcomingItem item
                : upcomingService.plan(memberId, from, to).items()) {
            if (item.date().isBefore(from) || item.date().isAfter(to)) {
                continue;
            }
            long[] day = byDate.computeIfAbsent(item.date(), key -> new long[5]);
            if (item.flow() == LedgerFlow.INCOME) {
                day[2] += item.amount();
            } else if (item.flow() == LedgerFlow.EXPENSE) {
                day[3] += item.amount();
            } else {
                day[4] += item.amount();
            }
        }

        List<LedgerUpcomingDtos.CalendarDay> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            long[] day = byDate.get(date);
            if (day == null) {
                continue;
            }
            days.add(new LedgerUpcomingDtos.CalendarDay(
                    date, day[0], day[1], day[2], day[3], day[4]));
        }
        return new LedgerUpcomingDtos.CalendarResponse(target.toString(), clock.today(), days);
    }
}
