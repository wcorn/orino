package ds.project.orino.planner.travel.expense.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripExpense;
import ds.project.orino.domain.planner.travel.entity.TripExpenseStatus;
import ds.project.orino.domain.planner.travel.entity.TripStatus;
import ds.project.orino.domain.planner.travel.repository.TripExpenseRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.day.service.TripClock;
import ds.project.orino.planner.travel.day.service.TripDayService;
import ds.project.orino.planner.travel.expense.dto.ExpenseSummary;
import ds.project.orino.planner.travel.expense.dto.TripExpenseResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 경비 조회(경비 독립 §6).
 *
 * <p><b>조립은 여기서 한다.</b> 그 여행의 지출을 읽어 여행의 문법(출발 전 · N일차·도시 ·
 * 다녀온 뒤)으로 묶는다. 예전에는 가계부 원장에서 {@code trip_id}가 이 여행인 행을 읽는
 * 읽기 뷰였다(D-27) — 원장이 없어져 여행이 자기 장부를 읽는다.
 *
 * <p><b>그룹 라벨을 저장하지 않는다.</b> 「3일차 · 교토」는 날짜와 기준 도시에서 매번 파생한다 —
 * 저장하면 기준 도시를 바꿨을 때 옛 도시가 조용히 남는다.
 */
@Service
@Transactional(readOnly = true)
public class TripExpenseQueryService {

    private final TripRepository tripRepository;
    private final TripExpenseRepository expenseRepository;
    private final TripDayService tripDayService;
    private final Clock clock;

    public TripExpenseQueryService(TripRepository tripRepository,
                                   TripExpenseRepository expenseRepository,
                                   TripDayService tripDayService,
                                   Clock clock) {
        this.tripRepository = tripRepository;
        this.expenseRepository = expenseRepository;
        this.tripDayService = tripDayService;
        this.clock = clock;
    }

    public TripExpenseResponse get(Long memberId, Long tripId) {
        Trip trip = tripRepository.findByIdAndMemberId(tripId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_TRIP_NOT_FOUND));

        Map<LocalDate, TravelPlace> cities = tripDayService.baseCitiesOf(tripId);
        TripStatus status = TripClock.status(trip, cities, clock);
        LocalDate today = TripClock.today(trip, cities, clock);

        List<TripExpense> rows = expenseRepository
                .findAllByTripIdAndDeletedAtIsNullOrderByOccurredOnAscIdAsc(tripId);
        long spent = sumOf(rows, TripExpenseStatus.CONFIRMED);
        long scheduled = sumOf(rows, TripExpenseStatus.SCHEDULED);
        boolean completed = status == TripStatus.COMPLETED;

        return new TripExpenseResponse(
                tripId,
                status,
                status == TripStatus.ONGOING ? trip.dayNumberOf(today) : null,
                budgetOf(trip, spent, scheduled, today, status),
                totalsOf(trip, spent, scheduled, completed),
                (int) rows.stream().filter(row -> row.getCategory() == null).count(),
                groupsOf(trip, cities, rows, today));
    }

    /**
     * 여러 여행의 경비 한 줄씩. 사이드바 여행 트리와 폴백 화면이 진행 중·예정 전부를 함께
     * 그린다 — 여행마다 {@link #get}을 부르면 화면 한 벌을 여러 번 조립하게 된다.
     *
     * <p>여기서 세는 것은 <b>화면과 같은 행</b>이다(확정 · 안 지운 것). 다만 합계만 필요하므로
     * 목록을 끌어오지 않고 DB에서 더한다
     * ({@link TripExpenseRepository#sumConfirmedByTrip}).
     *
     * @return 여행 id → 요약. <b>지출이 한 건도 없는 여행도 들어 있다</b>({@code spent: 0}) —
     *         빠뜨리면 화면이 「모른다」와 「안 썼다」를 구분할 수 없다
     */
    public Map<Long, ExpenseSummary> summariesOf(List<Trip> trips) {
        if (trips.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> spentByTrip = expenseRepository
                .sumConfirmedByTrip(trips.stream().map(Trip::getId).toList()).stream()
                .collect(Collectors.toMap(
                        TripExpenseRepository.TripTotal::getTripId,
                        TripExpenseRepository.TripTotal::getTotal));

        Map<Long, ExpenseSummary> summaries = new LinkedHashMap<>();
        for (Trip trip : trips) {
            summaries.put(trip.getId(), new ExpenseSummary(trip.getBudgetAmount(),
                    spentByTrip.getOrDefault(trip.getId(), 0L)));
        }
        return summaries;
    }

    // ---------------- 그룹 ----------------

    /**
     * 날짜 묶음. <b>기간 안의 날짜는 지출이 없어도 내려간다</b> — 화면이 「아직 적은 게 없어요」를
     * 그려야 한다. 반대로 {@code BEFORE}·{@code AFTER}는 비어 있으면 내리지 않는다. 늘 보이면
     * 여행 중 화면의 위아래가 빈 카드로 찬다.
     */
    private static List<TripExpenseResponse.ExpenseGroup> groupsOf(
            Trip trip, Map<LocalDate, TravelPlace> cities, List<TripExpense> rows,
            LocalDate today) {
        List<TripExpense> before = new ArrayList<>();
        List<TripExpense> after = new ArrayList<>();
        Map<LocalDate, List<TripExpense>> byDate = new LinkedHashMap<>();
        for (int i = 0; i < trip.totalDays(); i++) {
            byDate.put(trip.getStartDate().plusDays(i), new ArrayList<>());
        }

        for (TripExpense expense : rows) {
            LocalDate date = expense.getOccurredOn();
            if (date.isBefore(trip.getStartDate())) {
                before.add(expense);
            } else if (date.isAfter(trip.getEndDate())) {
                after.add(expense);
            } else {
                byDate.get(date).add(expense);
            }
        }

        List<TripExpenseResponse.ExpenseGroup> groups = new ArrayList<>();
        if (!before.isEmpty()) {
            groups.add(group("BEFORE", "출발 전", null, null, null, before, today));
        }
        byDate.forEach((date, dayRows) -> {
            int dayNumber = trip.dayNumberOf(date);
            String cityName = cityNameOn(date, cities);
            String label = cityName == null
                    ? labelOf(date)
                    : "%s · %s".formatted(labelOf(date), cityName);
            groups.add(group("DAY-" + dayNumber, label, dayNumber, date, cityName, dayRows,
                    today));
        });
        if (!after.isEmpty()) {
            groups.add(group("AFTER", "다녀온 뒤", null, null, null, after, today));
        }
        return groups;
    }

    /**
     * 「10.24 (금)」 — 묶음 이름의 날짜 부분.
     *
     * <p>예전에는 「2일차」였다(#1370). 「N일차」는 여행 안에서만 뜻이 있는 상대값이라,
     * 날짜 카드가 세로로 쌓이면 <b>그게 며칠인지 알 수 없다</b> — 무엇을 언제 썼는지 보려고
     * 여는 화면에서 그건 답이 아니다.
     *
     * <p>{@code dayNumber}는 응답에 그대로 남는다. 화면이 오늘 묶음을 찾는 데 쓴다.
     */
    private static String labelOf(LocalDate date) {
        return "%d.%02d (%s)".formatted(date.getMonthValue(), date.getDayOfMonth(),
                date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN));
    }

    private static TripExpenseResponse.ExpenseGroup group(
            String key, String label, Integer dayNumber, LocalDate date, String cityName,
            List<TripExpense> rows, LocalDate today) {
        return new TripExpenseResponse.ExpenseGroup(key, label, dayNumber, date, cityName,
                rows.stream().mapToLong(TripExpense::getAmount).sum(),
                rows.stream().map(expense -> ExpenseRows.of(expense, today)).toList());
    }

    /**
     * 그 날짜의 기준 도시 이름. <b>도시가 바뀌는 날은 도착 도시 하나로 센다</b>(§4.4) —
     * {@code TripDay}가 이미 그 날짜의 도시로 도착지를 들고 있어서, 여기서는 그대로 읽으면 된다.
     * 하루를 둘로 쪼개면 어느 쪽 합계도 맞지 않는다.
     */
    private static String cityNameOn(LocalDate date, Map<LocalDate, TravelPlace> cities) {
        TravelPlace city = cities.get(date);
        if (city == null) {
            return null;
        }
        return city.getCityName() != null ? city.getCityName() : city.getName();
    }

    // ---------------- 예산·총계 ----------------

    /**
     * 예산과 그 파생값. <b>안 정했으면 통째로 {@code null}</b>이다 — {@code amount: 0}을 내리면
     * 화면이 「0원 중 41.2만」을 그린다(§5.3).
     */
    private static TripExpenseResponse.BudgetView budgetOf(
            Trip trip, long spent, long scheduled, LocalDate today, TripStatus status) {
        Long amount = trip.getBudgetAmount();
        if (amount == null) {
            return null;
        }
        long remaining = amount - spent;
        Integer daysLeft = daysLeftOf(trip, today, status);
        // 여행이 끝나면 이 자리는 「하루 평균」이 받는다. 둘이 동시에 차지 않는다.
        Long dailyAllowance = daysLeft == null || daysLeft <= 0
                ? null : Math.max(remaining, 0) / daysLeft;
        return new TripExpenseResponse.BudgetView(
                amount, spent, scheduled, remaining, daysLeft, dailyAllowance);
    }

    /**
     * 남은 날짜(오늘 포함). 아직 안 떠났으면 여행 전체이고, 다녀왔으면 {@code null}이다 —
     * 「하루 얼마 쓸 수 있나」는 남은 날이 있어야 답이 있는 질문이다.
     */
    private static Integer daysLeftOf(Trip trip, LocalDate today, TripStatus status) {
        return switch (status) {
            case UPCOMING -> trip.totalDays();
            case ONGOING -> (int) ChronoUnit.DAYS.between(today, trip.getEndDate()) + 1;
            case COMPLETED -> null;
        };
    }

    /** 예산과 무관한 총계. 「하루 평균」은 <b>다녀온 뒤에만</b> 채운다(§5.3). */
    private static TripExpenseResponse.TotalsView totalsOf(
            Trip trip, long spent, long scheduled, boolean completed) {
        int days = trip.totalDays();
        return new TripExpenseResponse.TotalsView(spent, scheduled, days,
                completed && days > 0 ? spent / days : null);
    }

    private static long sumOf(List<TripExpense> rows, TripExpenseStatus status) {
        return rows.stream()
                .filter(expense -> expense.getStatus() == status)
                .mapToLong(TripExpense::getAmount)
                .sum();
    }
}
