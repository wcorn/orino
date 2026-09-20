package ds.project.orino.planner.travel.expense.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripExpense;
import ds.project.orino.domain.planner.travel.entity.TripExpenseStatus;
import ds.project.orino.domain.planner.travel.repository.TripExpenseRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.day.service.TripClock;
import ds.project.orino.planner.travel.day.service.TripDayService;
import ds.project.orino.planner.travel.expense.dto.ExpenseCreateRequest;
import ds.project.orino.planner.travel.expense.dto.ExpenseFxInput;
import ds.project.orino.planner.travel.expense.dto.ExpenseUpdateRequest;
import ds.project.orino.planner.travel.expense.dto.TripExpenseResponse;
import ds.project.orino.planner.travel.tools.service.ExchangeRateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

/**
 * 여행 지출을 적고 고치고 지운다(경비 독립 §6).
 *
 * <p><b>여기가 여행 쪽에 있는 이유가 달라졌다.</b> 예전에는 붙이고 떼는 일만 했다 — 지출은
 * 가계부 API로 만들고 여행은 {@code trip_id} 한 칸을 정할 뿐이었다(D-27). 원장이 없어지면서
 * 여행이 자기 장부를 갖고, 쓰기 전부가 여기로 왔다.
 *
 * <p><b>가계부를 여행 안에 다시 짓지 않는다.</b> 자산·잔액·이체·청구서·정기 항목은 아무것도
 * 따라오지 않았고, 그래서 이 클래스가 하는 일은 「무엇에 얼마를 언제 썼나」를 받아 적는 것뿐이다.
 *
 * <p>환율은 {@link ExchangeRateService}를 직접 쓴다. 가계부의 {@code LedgerFxService}를
 * 거치지 않는다 — 그 클래스는 실패를 {@code LDG-ERR-*}로 옮겨 놓기 위해 있었고, 그 코드들은
 * 2단계(#1409)에서 사라진다.
 */
@Service
@Transactional(readOnly = true)
public class TravelExpenseService {

    /** 저장되는 값은 언제나 원화다. 환산의 도착 통화는 고정이다. */
    private static final String KRW = "KRW";

    private final TripRepository tripRepository;
    private final TripExpenseRepository expenseRepository;
    private final TripDayService tripDayService;
    private final ExchangeRateService exchangeRateService;
    private final Clock clock;

    public TravelExpenseService(TripRepository tripRepository,
                                TripExpenseRepository expenseRepository,
                                TripDayService tripDayService,
                                ExchangeRateService exchangeRateService,
                                Clock clock) {
        this.tripRepository = tripRepository;
        this.expenseRepository = expenseRepository;
        this.tripDayService = tripDayService;
        this.exchangeRateService = exchangeRateService;
        this.clock = clock;
    }

    /**
     * 지출 하나를 적는다. 필수는 날짜와 금액뿐이고 제목·분류는 없어도 저장된다(§6.1).
     *
     * <p>{@code status}를 안 보내면 <b>날짜가 정한다</b> — 오늘 이후면 예정이다. 숙소 잔금을
     * 적으려고 「예정으로 적기」를 따로 외우게 하지 않는다. 기준이 되는 오늘은 서버 로컬
     * 날짜가 아니라 그 여행의 오늘이다.
     */
    @Transactional
    public TripExpenseResponse.ExpenseRow create(Long memberId, Long tripId,
                                                 ExpenseCreateRequest request) {
        Trip trip = requireTrip(memberId, tripId);
        LocalDate today = todayOf(trip);

        TripExpense expense = new TripExpense(trip.getId(), memberId, request.occurredOn(),
                request.status() != null ? request.status()
                        : statusFor(request.occurredOn(), today));
        applyMoney(expense, request.amount(), request.fx());
        expense.rename(request.title());
        expense.changeCategory(request.category());
        expense.changePaymentMethod(request.paymentMethod());

        return ExpenseRows.of(expenseRepository.save(expense), today);
    }

    /**
     * 보낸 것만 바꾼다. 「확정」 버튼이 보내는 것도 이 요청이다 — {@code status} 하나다.
     *
     * <p>금액은 <b>원화와 외화가 한 자리</b>라 함께 다룬다. {@code fx}를 보내면 외화 지출이
     * 되고, {@code clearFx}면 {@code amount}로 원화 지출이 된다. 둘 다 안 보냈으면 금액은
     * 그대로다 — 제목만 고치는 요청이 금액 검증에 걸리면 안 된다.
     */
    @Transactional
    public TripExpenseResponse.ExpenseRow update(Long memberId, Long tripId, Long expenseId,
                                                 ExpenseUpdateRequest request) {
        Trip trip = requireTrip(memberId, tripId);
        TripExpense expense = requireExpense(memberId, trip.getId(), expenseId);

        if (request.occurredOn() != null) {
            expense.changeOccurredOn(request.occurredOn());
        }
        if (request.status() != null) {
            expense.changeStatus(request.status());
        }
        if (Boolean.TRUE.equals(request.clearTitle())) {
            expense.rename(null);
        } else if (request.title() != null) {
            expense.rename(request.title());
        }
        if (Boolean.TRUE.equals(request.clearCategory())) {
            expense.changeCategory(null);
        } else if (request.category() != null) {
            expense.changeCategory(request.category());
        }
        if (Boolean.TRUE.equals(request.clearPaymentMethod())) {
            expense.changePaymentMethod(null);
        } else if (request.paymentMethod() != null) {
            expense.changePaymentMethod(request.paymentMethod());
        }
        if (request.fx() != null) {
            applyMoney(expense, request.amount(), request.fx());
        } else if (Boolean.TRUE.equals(request.clearFx()) || request.amount() != null) {
            applyMoney(expense, request.amount(), null);
        }

        return ExpenseRows.of(expense, todayOf(trip));
    }

    /**
     * 지운다. <b>상쇄하지 않는다</b>(§4.4) — 「원장은 지우지 않는다」는 원장이라서 있던
     * 규칙이고, 여행 경비는 원장이 아니다. 잘못 적었으면 고치거나 지운다.
     *
     * <p>{@code deletedAt}을 채우되 되돌리기는 화면 토스트에서 즉시만 연다. 휴지통 화면을
     * 만들지 않는다 — 여행 중에 열 화면이 아니다.
     */
    @Transactional
    public void delete(Long memberId, Long tripId, Long expenseId) {
        Trip trip = requireTrip(memberId, tripId);
        requireExpense(memberId, trip.getId(), expenseId).delete(clock.instant());
    }

    /**
     * 예산을 정하거나 해제한다(§6). <b>이 동작만은 그대로다</b> — 예산은 지출이 아니라
     * 여행에 붙은 값이라({@code trip.budget_amount}) 경비 독립의 영향을 받지 않는다.
     *
     * <p>{@code null}은 해제이고 <b>0은 400</b>이다. 0원 예산은 「안 정함」과 구분되지 않는데,
     * 화면은 그 둘을 완전히 다르게 그린다 — 안 정했으면 게이지를 숨기고, 0이면 시작부터
     * 초과다(§5.3).
     */
    @Transactional
    public Long updateBudget(Long memberId, Long tripId, Long amount) {
        if (amount != null && amount <= 0) {
            throw new CustomException(ErrorCode.TRAVEL_BUDGET_INVALID);
        }
        requireTrip(memberId, tripId).updateBudgetAmount(amount);
        return amount;
    }

    // ---------------- 금액 ----------------

    /**
     * 금액을 확정해 적는다. 외화면 {@code round(fx.amount × rate)}, 아니면 보낸 원화 그대로.
     *
     * <p><b>환율을 못 가져와도 400을 내지 않는다</b>(§6). 고시표를 받아 오지 못한 것은
     * 사용자 잘못이 아니고, 그것 때문에 여행 중에 기록이 막히면 안 적게 된다. 근거만
     * 남기고 금액은 0으로 두면 합계를 부풀리지 않으면서 화면이 그 자리에 직접 입력 칸을
     * 열 수 있다 — 고시표에 <b>없는 통화</b>는 반대로 사용자가 고쳐야 할 값이라 400이다.
     */
    private void applyMoney(TripExpense expense, Long amount, ExpenseFxInput fx) {
        if (fx == null || fx.currency() == null || fx.amount() == null) {
            // 반쪽짜리 외화는 외화가 아니다. 원화 금액이 함께 왔으면 그걸로 적는다.
            if (amount == null || amount <= 0) {
                throw new CustomException(ErrorCode.TRAVEL_EXPENSE_AMOUNT_REQUIRED);
            }
            expense.applyKrw(amount);
            return;
        }
        if (fx.amount().signum() <= 0) {
            throw new CustomException(ErrorCode.TRAVEL_EXPENSE_AMOUNT_REQUIRED);
        }
        String currency = fx.currency().trim().toUpperCase();
        // 0 이하 환율은 값이 아니다. 안 보낸 것으로 보고 고시로 채운다.
        BigDecimal rate = fx.rate() != null && fx.rate().signum() > 0
                ? fx.rate() : resolveRate(currency);
        expense.applyFx(currency, fx.amount(), rate);
    }

    /** ECB 고시로 환율을 채운다. 못 닿았으면 {@code null} — 에러가 아니다. */
    private BigDecimal resolveRate(String currency) {
        try {
            return exchangeRateService.rate(currency, KRW).rate();
        } catch (CustomException e) {
            if (e.getErrorCode() == ErrorCode.TRAVEL_FX_UNSUPPORTED_CURRENCY) {
                throw new CustomException(ErrorCode.TRAVEL_EXPENSE_UNSUPPORTED_CURRENCY);
            }
            return null;
        }
    }

    // ---------------- 공통 ----------------

    /** 남의 여행도 404 — 403이면 「그 id의 여행은 있다」가 새어나간다. */
    private Trip requireTrip(Long memberId, Long tripId) {
        return tripRepository.findByIdAndMemberId(tripId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_TRIP_NOT_FOUND));
    }

    /**
     * 내 지출이면서 <b>이 여행의 것</b>이어야 한다. 다른 여행의 지출 id를 이 여행 경로로
     * 보내면 404다 — 경로가 말하는 것과 고쳐지는 것이 다르면 화면을 믿을 수 없다.
     */
    private TripExpense requireExpense(Long memberId, Long tripId, Long expenseId) {
        return expenseRepository.findByIdAndMemberIdAndDeletedAtIsNull(expenseId, memberId)
                .filter(expense -> tripId.equals(expense.getTripId()))
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_EXPENSE_NOT_FOUND));
    }

    /** 그 여행의 오늘. 기기 시간대가 아니라 그 날짜의 기준 도시 시계로 본다. */
    private LocalDate todayOf(Trip trip) {
        Map<LocalDate, TravelPlace> cities = tripDayService.baseCitiesOf(trip.getId());
        return TripClock.today(trip, cities, clock);
    }

    /** 오늘 이후면 예정이다. 「예정으로 적기」를 따로 외우게 하지 않는다. */
    private static TripExpenseStatus statusFor(LocalDate occurredOn, LocalDate today) {
        return occurredOn.isAfter(today)
                ? TripExpenseStatus.SCHEDULED : TripExpenseStatus.CONFIRMED;
    }
}
