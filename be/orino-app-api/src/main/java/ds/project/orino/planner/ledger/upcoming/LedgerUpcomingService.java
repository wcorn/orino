package ds.project.orino.planner.ledger.upcoming;

import ds.project.orino.domain.planner.ledger.entity.LedgerAmountType;
import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerAssetType;
import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerInstallment;
import ds.project.orino.domain.planner.ledger.entity.LedgerInstallmentRound;
import ds.project.orino.domain.planner.ledger.entity.LedgerOverrideAction;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurring;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurringOverride;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurringStatus;
import ds.project.orino.domain.planner.ledger.entity.LedgerStatement;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransaction;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;
import ds.project.orino.domain.planner.ledger.repository.LedgerAssetRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerInstallmentRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerInstallmentRoundRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerRecurringOverrideRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerRecurringRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.holiday.BusinessDays;
import ds.project.orino.planner.ledger.card.LedgerBillingCycle;
import ds.project.orino.planner.ledger.card.LedgerStatementService;
import ds.project.orino.planner.ledger.common.LedgerBalances;
import ds.project.orino.planner.ledger.common.LedgerClock;
import ds.project.orino.planner.ledger.recurring.LedgerOccurrenceResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 예정 목록 — <b>네 출처의 UNION</b>(확정 명세 §8.1, 아키텍처 §4).
 *
 * <pre>
 *   정기 회차     : 규칙 전개 − override(SKIP/REVERTED) + override(AMOUNT/MOVE/UNPAID)
 *   직접 예약     : ledger_transaction WHERE status='SCHEDULED'
 *   카드 결제 예정 : ledger_statement (COLLECTING·CONFIRMED·PARTIAL)
 *   할부 잔여     : ledger_installment_round WHERE settled=0 AND statement_id IS NULL
 * </pre>
 *
 * <p><b>같은 돈을 두 번 세지 않는 것이 이 클래스의 전부다.</b> 이미 청구서에 붙은 할부 회차는
 * 그 청구서의 청구액에 들어가 있으므로 빠지고, 카드로 나가는 정기 회차는 잔액을 줄이지 않는다 —
 * 그 돈은 결제일에 카드 대금으로 한 번에 빠진다.
 *
 * <p><b>예정은 원장 잔액을 바꾸지 않는다.</b> 여기서 계산하는 것은 <b>예상</b> 잔액뿐이고,
 * 그 불변 조건이 깨지면 「월말 예상 잔액」이 현재 잔액과 같아져 아무 말도 하지 않게 된다.
 *
 * <p>캐시하지 않는다 — 12개월 × 정기 항목 20개면 240행 수준이다(아키텍처 §4).
 */
@Service
public class LedgerUpcomingService {

    public static final int DEFAULT_DAYS = 30;

    /** 최대 12개월. 그 너머는 예정이 아니라 추측이다. */
    public static final int MAX_DAYS = 366;

    /** 지난 미납을 얼마나 거슬러 보여줄지. 「몇 달째 안 냈다」가 보여야 한다. */
    private static final int OVERDUE_LOOKBACK_DAYS = 180;

    private final LedgerRecurringRepository recurringRepository;
    private final LedgerRecurringOverrideRepository overrideRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerAssetRepository assetRepository;
    private final LedgerInstallmentRepository installmentRepository;
    private final LedgerInstallmentRoundRepository roundRepository;
    private final LedgerOccurrenceResolver resolver;
    private final LedgerStatementService statementService;
    private final BusinessDays businessDays;
    private final LedgerClock clock;

    public LedgerUpcomingService(LedgerRecurringRepository recurringRepository,
                                 LedgerRecurringOverrideRepository overrideRepository,
                                 LedgerTransactionRepository transactionRepository,
                                 LedgerAssetRepository assetRepository,
                                 LedgerInstallmentRepository installmentRepository,
                                 LedgerInstallmentRoundRepository roundRepository,
                                 LedgerOccurrenceResolver resolver,
                                 LedgerStatementService statementService,
                                 BusinessDays businessDays,
                                 LedgerClock clock) {
        this.recurringRepository = recurringRepository;
        this.overrideRepository = overrideRepository;
        this.transactionRepository = transactionRepository;
        this.assetRepository = assetRepository;
        this.installmentRepository = installmentRepository;
        this.roundRepository = roundRepository;
        this.resolver = resolver;
        this.statementService = statementService;
        this.businessDays = businessDays;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LedgerUpcomingDtos.UpcomingResponse upcoming(Long memberId, int days) {
        int window = Math.clamp(days, 1, MAX_DAYS);
        LocalDate today = clock.today();
        LocalDate to = today.plusDays(window);

        Plan plan = plan(memberId, today, to);
        return new LedgerUpcomingDtos.UpcomingResponse(
                today, to, window, plan.stats(), plan.items());
    }

    /** 대시보드·요약이 같은 계산을 쓴다 — 두 벌로 두면 화면끼리 다른 숫자를 말한다. */
    @Transactional(readOnly = true)
    public Plan plan(Long memberId, LocalDate from, LocalDate to) {
        List<LedgerAsset> assets =
                assetRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId);
        Map<Long, LedgerAsset> byId = new HashMap<>();
        assets.forEach(asset -> byId.put(asset.getId(), asset));
        LedgerBalances balances = LedgerBalances.of(assets,
                transactionRepository.sumConfirmedByAssetAndType(
                        memberId, LedgerTransactionStatus.CONFIRMED),
                transactionRepository.sumConfirmedByCounterAsset(
                        memberId, LedgerTransactionStatus.CONFIRMED));

        List<Planned> planned = new ArrayList<>();
        planned.addAll(recurring(memberId, from, to, byId));
        planned.addAll(oneOff(memberId, from, to, byId));
        planned.addAll(cardPayments(memberId, to, byId));
        planned.addAll(installments(memberId, to, byId));
        planned.sort(Comparator.comparing((Planned p) -> p.item().date())
                .thenComparing(p -> p.item().kind()));

        return summarize(planned, spendableBalance(assets, balances), from);
    }

    /** 예정 계산 결과. 목록과 통계가 <b>같은 한 번의 계산</b>에서 나온다. */
    public record Plan(List<LedgerUpcomingDtos.UpcomingItem> items,
                       LedgerUpcomingDtos.UpcomingStats stats) {
    }

    private record Planned(LedgerUpcomingDtos.UpcomingItem item, long cashDelta) {
    }

    // ── 출처 1. 정기 회차 ───────────────────────────────────────────────────────

    private List<Planned> recurring(Long memberId, LocalDate from, LocalDate to,
                                    Map<Long, LedgerAsset> assets) {
        List<LedgerRecurring> rules = recurringRepository.findAllByMemberIdOrderByIdAsc(memberId)
                .stream()
                .filter(rule -> rule.getStatus() != LedgerRecurringStatus.ENDED)
                .toList();
        if (rules.isEmpty()) {
            return List.of();
        }
        Map<Long, LedgerRecurring> ruleById = new HashMap<>();
        rules.forEach(rule -> ruleById.put(rule.getId(), rule));
        Map<Long, Map<LocalDate, LedgerRecurringOverride>> overrides = new HashMap<>();
        for (LedgerRecurringOverride override
                : overrideRepository.findAllByRecurringIdIn(ruleById.keySet())) {
            overrides.computeIfAbsent(override.getRecurringId(), key -> new HashMap<>())
                    .put(override.getOccurrenceDate(), override);
        }

        // 지난 회차는 규칙에서 다시 만들지 않는다. 이미 적혔으면 원장에 있고, 안 적혔으면
        // 건너뛰었거나 되돌렸거나 미납이다 — 미납만 아래에서 따로 붙인다. 여기를 열어 두면
        // 캘린더가 「아무 일도 없었던 지난 날짜」에 예정을 그린다.
        LocalDate floor = from.isBefore(clock.today()) ? clock.today() : from;

        List<Planned> result = new ArrayList<>();
        for (LedgerRecurring rule : rules) {
            Map<LocalDate, LedgerRecurringOverride> mine =
                    overrides.getOrDefault(rule.getId(), Map.of());
            for (LedgerOccurrenceResolver.Occurrence occurrence
                    : resolver.resolve(rule, mine, floor, to)) {
                if (occurrence.isHidden()) {
                    continue;
                }
                result.add(plannedOccurrence(rule, occurrence.occurrenceDate(),
                        occurrence.date(), occurrence.amount(), occurrence.isUnpaid(), assets));
            }
        }
        // 지난 미납은 구간 밖이어도 따라온다 — 사라지면 「무시」 버튼을 만든 것과 같다(§6.4).
        result.addAll(pastUnpaid(ruleById, floor, assets));
        return result;
    }

    private List<Planned> pastUnpaid(Map<Long, LedgerRecurring> rules, LocalDate from,
                                     Map<Long, LedgerAsset> assets) {
        List<Planned> result = new ArrayList<>();
        LocalDate floor = from.minusDays(OVERDUE_LOOKBACK_DAYS);
        for (LedgerRecurringOverride override : overrideRepository
                .findAllByRecurringIdInAndAction(rules.keySet(), LedgerOverrideAction.UNPAID)) {
            LocalDate date = override.effectiveDate();
            if (!date.isBefore(from) || date.isBefore(floor)) {
                continue;
            }
            LedgerRecurring rule = rules.get(override.getRecurringId());
            long amount = override.getAmount() == null ? rule.getAmount() : override.getAmount();
            result.add(plannedOccurrence(rule, override.getOccurrenceDate(), date, amount,
                    true, assets));
        }
        return result;
    }

    private Planned plannedOccurrence(LedgerRecurring rule, LocalDate occurrenceDate,
                                      LocalDate date, long amount, boolean overdue,
                                      Map<Long, LedgerAsset> assets) {
        LedgerAsset asset = assets.get(rule.getAssetId());
        LedgerUpcomingDtos.UpcomingItem item = new LedgerUpcomingDtos.UpcomingItem(
                LedgerUpcomingDtos.Kind.RECURRING, date, dday(date), rule.getName(), amount,
                rule.getTxType(), rule.getTxType() == LedgerFlow.TRANSFER, overdue,
                rule.getAmountType() == LedgerAmountType.VARIABLE, rule.getCategoryId(),
                rule.getAssetId(), asset == null ? null : asset.getName(),
                null, rule.getId(), occurrenceDate, null, null);
        return new Planned(item, cashDelta(rule.getTxType(), amount, asset,
                assets.get(rule.getCounterAssetId()), assets));
    }

    // ── 출처 2. 직접 예약 ──────────────────────────────────────────────────────

    private List<Planned> oneOff(Long memberId, LocalDate from, LocalDate to,
                                 Map<Long, LedgerAsset> assets) {
        List<Planned> result = new ArrayList<>();
        for (LedgerTransaction tx : transactionRepository
                .findAllByMemberIdAndStatusAndDeletedAtIsNullAndOccurredOnBetweenOrderByOccurredOnAscIdAsc(
                        memberId, LedgerTransactionStatus.SCHEDULED, from, to)) {
            LedgerAsset asset = assets.get(tx.getAssetId());
            LedgerUpcomingDtos.UpcomingItem item = new LedgerUpcomingDtos.UpcomingItem(
                    LedgerUpcomingDtos.Kind.ONE_OFF, tx.getOccurredOn(), dday(tx.getOccurredOn()),
                    tx.getTitle(), tx.getAmount(), tx.getType(),
                    tx.getType() == LedgerFlow.TRANSFER, false, tx.isEstimated(),
                    tx.getCategoryId(), tx.getAssetId(), asset == null ? null : asset.getName(),
                    tx.getId(), null, null, null, null);
            result.add(new Planned(item, cashDelta(tx.getType(), tx.getAmount(), asset,
                    assets.get(tx.getCounterAssetId()), assets)));
        }
        return result;
    }

    // ── 출처 3. 카드 결제 예정 ─────────────────────────────────────────────────

    private List<Planned> cardPayments(Long memberId, LocalDate to,
                                       Map<Long, LedgerAsset> assets) {
        List<Planned> result = new ArrayList<>();
        List<LedgerStatement> statements = new ArrayList<>(statementService.overdue(memberId));
        for (LedgerStatement statement : statementService.statementsDueUntil(memberId, to)) {
            if (statements.stream().noneMatch(row -> row.getId().equals(statement.getId()))) {
                statements.add(statement);
            }
        }
        for (LedgerStatement statement : statements) {
            long remaining = statementService.breakdownOf(statement).remaining();
            if (remaining <= 0) {
                continue;
            }
            LedgerAsset card = assets.get(statement.getCardAssetId());
            LedgerAsset payFrom = card == null ? null : assets.get(card.getPaymentAssetId());
            LedgerUpcomingDtos.UpcomingItem item = new LedgerUpcomingDtos.UpcomingItem(
                    LedgerUpcomingDtos.Kind.CARD_PAYMENT, statement.getPaymentDate(),
                    dday(statement.getPaymentDate()),
                    "카드 대금 · " + (card == null ? "" : card.getName()), remaining,
                    // 대금 납부는 이체다. 여기서 지출로 새면 카드로 쓴 돈이 두 번 잡힌다(§7.3).
                    LedgerFlow.TRANSFER, true,
                    statement.getPaymentDate().isBefore(clock.today()), false, null,
                    payFrom == null ? null : payFrom.getId(),
                    payFrom == null ? null : payFrom.getName(),
                    null, null, null, statement.getId(), null);
            result.add(new Planned(item, isSpendable(payFrom) || payFrom == null
                    ? -remaining : 0));
        }
        return result;
    }

    // ── 출처 4. 할부 잔여 ──────────────────────────────────────────────────────

    private List<Planned> installments(Long memberId, LocalDate to,
                                       Map<Long, LedgerAsset> assets) {
        List<LedgerInstallmentRound> rounds = roundRepository.findUnbilledByMember(
                memberId, LedgerInstallment.Status.ACTIVE);
        if (rounds.isEmpty()) {
            return List.of();
        }
        Map<Long, LedgerAsset> cardByInstallment = cardsOf(memberId, assets);

        List<Planned> result = new ArrayList<>();
        for (LedgerInstallmentRound round : rounds) {
            LedgerAsset card = cardByInstallment.get(round.getInstallmentId());
            if (card == null || !card.hasBillingCycle()) {
                continue;
            }
            LocalDate date = businessDays.previousBusinessDayOrSame(
                    LedgerBillingCycle.paymentDateIn(card, YearMonth.parse(round.getBillingMonth())));
            if (date.isAfter(to)) {
                continue;
            }
            LedgerAsset payFrom = assets.get(card.getPaymentAssetId());
            LedgerUpcomingDtos.UpcomingItem item = new LedgerUpcomingDtos.UpcomingItem(
                    LedgerUpcomingDtos.Kind.INSTALLMENT, date, dday(date),
                    "할부 %d회차 · %s".formatted(round.getRoundNo(), card.getName()),
                    round.getAmount(), LedgerFlow.TRANSFER, true, false, false, null,
                    payFrom == null ? null : payFrom.getId(),
                    payFrom == null ? null : payFrom.getName(),
                    null, null, null, null, round.getInstallmentId());
            result.add(new Planned(item, -round.getAmount()));
        }
        return result;
    }

    /** 할부가 어느 카드에 걸린 것인지 — 원 거래의 자산이 곧 그 카드다. */
    private Map<Long, LedgerAsset> cardsOf(Long memberId, Map<Long, LedgerAsset> assets) {
        Map<Long, LedgerAsset> byInstallment = new HashMap<>();
        for (LedgerInstallment installment : installmentRepository
                .findAllByMemberIdAndStatus(memberId, LedgerInstallment.Status.ACTIVE)) {
            transactionRepository.findById(installment.getTransactionId())
                    .map(tx -> assets.get(tx.getAssetId()))
                    .ifPresent(card -> byInstallment.put(installment.getId(), card));
        }
        return byInstallment;
    }

    // ── 합계 ───────────────────────────────────────────────────────────────────

    private Plan summarize(List<Planned> planned, long currentBalance, LocalDate from) {
        long outflow = 0;
        long income = 0;
        long running = currentBalance;
        long minAmount = currentBalance;
        LocalDate minDate = from;
        String minReason = null;
        Map<LedgerUpcomingDtos.Kind, Integer> byKind =
                new EnumMap<>(LedgerUpcomingDtos.Kind.class);

        List<LedgerUpcomingDtos.UpcomingItem> items = new ArrayList<>();
        for (Planned entry : planned) {
            items.add(entry.item());
            byKind.merge(entry.item().kind(), 1, Integer::sum);
            if (entry.cashDelta() < 0) {
                outflow += -entry.cashDelta();
            } else {
                income += entry.cashDelta();
            }
            running += entry.cashDelta();
            if (running < minAmount) {
                minAmount = running;
                minDate = entry.item().date();
                minReason = entry.item().title();
            }
        }
        return new Plan(items, new LedgerUpcomingDtos.UpcomingStats(
                outflow, income, currentBalance, running,
                new LedgerUpcomingDtos.MinBalance(minAmount, minDate, minReason),
                items.size(), byKind));
    }

    /**
     * 「쓸 수 있는 돈」. <b>저축은 뺀다.</b>
     *
     * <p>확정 명세 §8.4의 산식이 이체를 빼는 이유가 여기 있다 — 청약으로 옮긴 돈은 총자산에는
     * 그대로 있지만 이번 달에 쓸 수 있는 돈은 아니다. 저축까지 넣으면 계좌 간 이체가 항상
     * 상쇄돼 「청약 이체 직후 바닥」이 영원히 보이지 않는다.
     */
    private long spendableBalance(List<LedgerAsset> assets, LedgerBalances balances) {
        long sum = 0;
        for (LedgerAsset asset : assets) {
            if (!isSpendable(asset)) {
                continue;
            }
            Long balance = balances.balanceOf(asset.getId());
            sum += balance == null ? 0 : balance;
        }
        return sum;
    }

    private boolean isSpendable(LedgerAsset asset) {
        return asset != null && asset.getType().holdsBalance()
                && asset.getType() != LedgerAssetType.SAVINGS;
    }

    /**
     * 그 예정이 「쓸 수 있는 돈」을 얼마나 움직이는가.
     *
     * <p>신용카드 지출은 <b>0이다</b> — 그 돈은 결제일에 카드 대금으로 한 번에 빠지고,
     * 여기서도 빼면 같은 돈이 두 번 나간다. 체크카드는 연결 계좌에서 빠진다(D-4).
     */
    private long cashDelta(LedgerFlow flow, long amount, LedgerAsset asset,
                           LedgerAsset counter, Map<Long, LedgerAsset> assets) {
        LedgerAsset source = sourceOf(asset, assets);
        if (flow == LedgerFlow.INCOME) {
            return isSpendable(source) ? amount : 0;
        }
        if (flow == LedgerFlow.EXPENSE) {
            return isSpendable(source) ? -amount : 0;
        }
        long delta = 0;
        if (isSpendable(source)) {
            delta -= amount;
        }
        if (isSpendable(counter)) {
            delta += amount;
        }
        return delta;
    }

    /** 체크카드로 쓴 돈은 연결 계좌에서 빠진다. 카드 자체는 잔액을 갖지 않는다(D-4). */
    private LedgerAsset sourceOf(LedgerAsset asset, Map<Long, LedgerAsset> assets) {
        if (asset != null && asset.getType() == LedgerAssetType.DEBIT_CARD) {
            return assets.get(asset.getLinkedAssetId());
        }
        return asset;
    }

    private long dday(LocalDate date) {
        return ChronoUnit.DAYS.between(clock.today(), date);
    }
}
