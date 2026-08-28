package ds.project.orino.planner.ledger.recurring;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.ledger.entity.LedgerAmountType;
import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerAssetType;
import ds.project.orino.domain.planner.ledger.entity.LedgerCategory;
import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerFrequencyType;
import ds.project.orino.domain.planner.ledger.entity.LedgerOverrideAction;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurring;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurringAmountHistory;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurringKind;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurringOverride;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurringStatus;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransaction;
import ds.project.orino.domain.planner.ledger.repository.LedgerAssetRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerCategoryRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerRecurringAmountHistoryRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerRecurringOverrideRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerRecurringRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.ledger.common.LedgerBootstrap;
import ds.project.orino.planner.ledger.common.LedgerClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 정기 항목 — 구독·보험·자동이체·고정비를 <b>하나의 개념</b>으로 다룬다(확정 명세 §6).
 *
 * <p>종류({@code kind})로 코드가 갈리지 않는다. 「정해진 날에 정해진 돈이 나간다」는 같은
 * 사실이고, 종류마다 다른 경로를 두면 같은 버그를 다섯 번 고치게 된다.
 *
 * <p>회차를 만들거나 지우는 일은 여기 없다. 규칙만 바꾸면 <b>앞으로의 예정이 즉시</b>
 * 새 규칙대로 계산된다(D-5) — 대량 UPDATE도, "이 건만 / 이후 모두"를 묻는 대화상자도 없다.
 */
@Service
public class LedgerRecurringService {

    /** 인상 신호가 보는 기간. 「최근」이 반년을 넘어가면 신호가 아니라 배경이 된다. */
    private static final int PRICE_INCREASE_MONTHS = 6;

    /** 「장기 미변동」의 문턱. 확정 명세 §6.6이 정한 값이다. */
    private static final int LONG_UNCHANGED_MONTHS = 6;

    /** 무료 체험 종료 임박으로 볼 남은 날수. 해지를 결심할 시간이 남아 있어야 신호다. */
    private static final int TRIAL_ENDING_DAYS = 14;

    private final LedgerRecurringRepository recurringRepository;
    private final LedgerRecurringOverrideRepository overrideRepository;
    private final LedgerRecurringAmountHistoryRepository historyRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerAssetRepository assetRepository;
    private final LedgerCategoryRepository categoryRepository;
    private final LedgerOccurrenceResolver resolver;
    private final LedgerBootstrap bootstrap;
    private final LedgerClock clock;

    public LedgerRecurringService(LedgerRecurringRepository recurringRepository,
                                  LedgerRecurringOverrideRepository overrideRepository,
                                  LedgerRecurringAmountHistoryRepository historyRepository,
                                  LedgerTransactionRepository transactionRepository,
                                  LedgerAssetRepository assetRepository,
                                  LedgerCategoryRepository categoryRepository,
                                  LedgerOccurrenceResolver resolver,
                                  LedgerBootstrap bootstrap,
                                  LedgerClock clock) {
        this.recurringRepository = recurringRepository;
        this.overrideRepository = overrideRepository;
        this.historyRepository = historyRepository;
        this.transactionRepository = transactionRepository;
        this.assetRepository = assetRepository;
        this.categoryRepository = categoryRepository;
        this.resolver = resolver;
        this.bootstrap = bootstrap;
        this.clock = clock;
    }

    @Transactional
    public LedgerRecurringDtos.RecurringView create(Long memberId,
                                                    LedgerRecurringDtos.CreateRequest request) {
        bootstrap.ensureSeeded(memberId);
        LedgerAmountType amountType = request.amountType() == null
                ? LedgerAmountType.FIXED : request.amountType();
        validateRule(request.freqType(), request.freqInterval(),
                request.freqDay(), request.freqMonth());
        validateTarget(memberId, request.txType(), request.assetId(),
                request.counterAssetId(), request.categoryId());

        // 하한을 등록 시점에 박는다. 과거 시작일을 그대로 두면 지난 여섯 달치가 쏟아진다.
        LocalDate postingFrom = request.startDate().isAfter(clock.today())
                ? request.startDate() : clock.today();
        LedgerRecurring rule = new LedgerRecurring(memberId, request.name(), request.kind(),
                request.txType(), request.amount(), amountType, request.assetId(),
                request.freqType(), request.startDate(), postingFrom);
        rule.updateRule(request.freqType(), request.freqInterval(),
                request.freqDay(), request.freqMonth());
        rule.updateTarget(request.assetId(), request.counterAssetId(), request.categoryId());
        rule.updatePeriod(request.startDate(), request.endDate());
        rule.updateMemo(request.cancelUrl(), request.memo());
        if (request.businessDayPolicy() != null) {
            rule.updateBusinessDayPolicy(request.businessDayPolicy());
        }
        recurringRepository.save(rule);

        // 첫 금액도 이력이다. 없으면 「한 번도 안 오른 항목」과 「이력이 없는 항목」이 구분되지 않는다.
        historyRepository.save(new LedgerRecurringAmountHistory(
                rule.getId(), request.startDate(), request.amount()));
        return view(rule, assetNames(memberId), categoryNames(memberId));
    }

    /**
     * 부분 수정. <b>과거 내역은 건드리지 않는다</b> — 지난달에 12,000원 낸 사실은 그대로다.
     *
     * <p>금액이 바뀌면 이력 한 줄이 남는다. 현재 금액만 갖고 있으면 「조용히 올랐다」는
     * 구독 관리의 핵심 문제를 볼 수 없다.
     */
    @Transactional
    public LedgerRecurringDtos.RecurringView update(Long memberId, Long id,
                                                    LedgerRecurringDtos.UpdateRequest request) {
        LedgerRecurring rule = require(memberId, id);

        LedgerFrequencyType freqType = request.freqType() == null
                ? rule.getFreqType() : request.freqType();
        Integer interval = request.freqInterval() == null
                ? rule.getFreqInterval() : request.freqInterval();
        Integer day = request.freqDay() == null ? rule.getFreqDay() : request.freqDay();
        Integer month = request.freqMonth() == null ? rule.getFreqMonth() : request.freqMonth();
        validateRule(freqType, interval, day, month);
        rule.updateRule(freqType, interval, day, month);

        Long assetId = request.assetId() == null ? rule.getAssetId() : request.assetId();
        Long counterAssetId = request.counterAssetId() == null
                ? rule.getCounterAssetId() : request.counterAssetId();
        Long categoryId = request.categoryId() == null
                ? rule.getCategoryId() : request.categoryId();
        validateTarget(memberId, rule.getTxType(), assetId, counterAssetId, categoryId);
        rule.updateTarget(assetId, counterAssetId, categoryId);

        if (request.name() != null) {
            rule.updateName(request.name());
        }
        if (request.kind() != null) {
            rule.updateKind(request.kind());
        }
        if (request.amountType() != null) {
            rule.updateAmountType(request.amountType());
        }
        if (request.businessDayPolicy() != null) {
            rule.updateBusinessDayPolicy(request.businessDayPolicy());
        }
        if (request.startDate() != null || request.endDate() != null) {
            rule.updatePeriod(
                    request.startDate() == null ? rule.getStartDate() : request.startDate(),
                    request.endDate() == null ? rule.getEndDate() : request.endDate());
        }
        if (request.cancelUrl() != null || request.memo() != null) {
            rule.updateMemo(
                    request.cancelUrl() == null ? rule.getCancelUrl() : request.cancelUrl(),
                    request.memo() == null ? rule.getMemo() : request.memo());
        }
        if (request.amount() != null && request.amount() != rule.getAmount()) {
            rule.updateAmount(request.amount());
            historyRepository.save(new LedgerRecurringAmountHistory(
                    rule.getId(), clock.today(), request.amount()));
        }
        return view(rule, assetNames(memberId), categoryNames(memberId));
    }

    /** 기간을 정해 쉰다. 규칙은 그대로이고 그 구간의 회차만 전개에서 빠진다. */
    @Transactional
    public LedgerRecurringDtos.RecurringView pause(Long memberId, Long id,
                                                   LedgerRecurringDtos.PauseRequest request) {
        LedgerRecurring rule = require(memberId, id);
        rule.pause(request.from(), request.to());
        return view(rule, assetNames(memberId), categoryNames(memberId));
    }

    @Transactional
    public LedgerRecurringDtos.RecurringView resume(Long memberId, Long id) {
        LedgerRecurring rule = require(memberId, id);
        rule.resume();
        return view(rule, assetNames(memberId), categoryNames(memberId));
    }

    /**
     * 해지. 항목은 <b>목록에 「종료됨」으로 남는다</b> — 연간 고정비 회고에 필요하다(§6.6).
     *
     * <p>{@code revertPostedAfter}가 참이면 해지일 이후 자동 기록된 것을 일괄로 되돌린다.
     * 이미 원장에 들어간 것을 지우는 유일한 경로라 <b>기본값 없이 명시적으로 받는다</b> —
     * 「해지했으니 당연히 지우겠지」로 두면 3월에 해지한 것을 8월에 등록하면서 다섯 달치가
     * 소리 없이 사라진다.
     */
    @Transactional
    public LedgerRecurringDtos.EndResponse end(Long memberId, Long id,
                                               LedgerRecurringDtos.EndRequest request) {
        LedgerRecurring rule = require(memberId, id);
        rule.end(request.endedOn());
        if (!Boolean.TRUE.equals(request.revertPostedAfter())) {
            return new LedgerRecurringDtos.EndResponse(0, "해지했습니다.");
        }

        int reverted = 0;
        for (LedgerTransaction tx
                : transactionRepository.findAllByRecurringIdAndDeletedAtIsNull(id)) {
            if (tx.getOccurredOn().isBefore(request.endedOn())) {
                continue;
            }
            tx.softDelete(clock.now());
            // 되돌린 회차는 이력에 남는다 — 되돌렸다는 사실 자체가 정보다.
            markOverride(id, tx.getOccurrenceDate(), LedgerOverrideAction.REVERTED, null, null,
                    "소급 해지");
            reverted++;
        }
        return new LedgerRecurringDtos.EndResponse(reverted,
                "%d월 %d일 이후 자동 기록된 %d건을 되돌렸습니다.".formatted(
                        request.endedOn().getMonthValue(),
                        request.endedOn().getDayOfMonth(), reverted));
    }

    /** 금액 변경 이력 + 미발생 이력. 몇 달째 되돌리고 있는지가 여기서 드러난다. */
    @Transactional(readOnly = true)
    public LedgerRecurringDtos.HistoryResponse history(Long memberId, Long id) {
        require(memberId, id);
        List<LedgerRecurringDtos.AmountChange> amounts = new ArrayList<>();
        Long previous = null;
        for (LedgerRecurringAmountHistory row
                : historyRepository.findAllByRecurringIdOrderByEffectiveFromAscIdAsc(id)) {
            amounts.add(new LedgerRecurringDtos.AmountChange(
                    row.getEffectiveFrom(), row.getAmount(), previous));
            previous = row.getAmount();
        }

        List<LedgerRecurringDtos.MissedOccurrence> missed = new ArrayList<>();
        for (LedgerRecurringOverride override
                : overrideRepository.findAllByRecurringIdOrderByOccurrenceDateDesc(id)) {
            if (override.getAction() == LedgerOverrideAction.AMOUNT
                    || override.getAction() == LedgerOverrideAction.MOVE) {
                continue;
            }
            missed.add(new LedgerRecurringDtos.MissedOccurrence(
                    override.getOccurrenceDate(), override.getAction(), override.getNote()));
        }
        return new LedgerRecurringDtos.HistoryResponse(amounts, missed);
    }

    /** 목록은 점검 도구다(§6.6) — 합계와 신호를 함께 내려야 「이거 아직도 내나」가 보인다. */
    @Transactional(readOnly = true)
    public LedgerRecurringDtos.RecurringListResponse list(Long memberId) {
        bootstrap.ensureSeeded(memberId);
        List<LedgerRecurring> rules = recurringRepository.findAllByMemberIdOrderByIdAsc(memberId);
        Map<Long, String> assets = assetNames(memberId);
        Map<Long, String> categories = categoryNames(memberId);

        List<LedgerRecurringDtos.RecurringView> items = new ArrayList<>();
        long monthlyFixed = 0;
        int subscriptions = 0;
        int active = 0;
        for (LedgerRecurring rule : rules) {
            items.add(view(rule, assets, categories));
            if (rule.getStatus() == LedgerRecurringStatus.ENDED) {
                continue;
            }
            active++;
            if (rule.getKind() == LedgerRecurringKind.SUBSCRIPTION) {
                subscriptions++;
            }
            if (rule.getTxType() == LedgerFlow.EXPENSE) {
                monthlyFixed += LedgerRecurrence.monthlyEquivalent(rule);
            }
        }
        // 다음 결제일 순. 종료된 항목은 뒤로 밀되 사라지지는 않는다.
        items.sort(Comparator.comparing(LedgerRecurringDtos.RecurringView::nextDate,
                Comparator.nullsLast(Comparator.naturalOrder())));

        return new LedgerRecurringDtos.RecurringListResponse(
                items,
                new LedgerRecurringDtos.Stats(monthlyFixed, monthlyFixed * 12,
                        subscriptions, active),
                signals(rules),
                overdue(rules));
    }

    LedgerRecurring require(Long memberId, Long id) {
        return recurringRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_RECURRING_NOT_FOUND));
    }

    /** 그 회차에 손댄 흔적. 없으면 아무도 건드리지 않은 회차다. */
    Optional<LedgerRecurringOverride> findOverride(Long recurringId, LocalDate occurrenceDate) {
        return overrideRepository.findByRecurringIdAndOccurrenceDate(recurringId, occurrenceDate);
    }

    /** 회차 조작이 만드는 override. 같은 회차를 두 번 손대면 덮어쓴다. */
    LedgerRecurringOverride markOverride(Long recurringId, LocalDate occurrenceDate,
                                         LedgerOverrideAction action, Long amount,
                                         LocalDate movedTo, String note) {
        LedgerRecurringOverride override = overrideRepository
                .findByRecurringIdAndOccurrenceDate(recurringId, occurrenceDate)
                .orElseGet(() -> new LedgerRecurringOverride(recurringId, occurrenceDate, action));
        override.apply(action, amount, movedTo, note);
        return overrideRepository.save(override);
    }

    /**
     * 점검 신호 4종(§6.6).
     *
     * <p>목록이 「내가 뭘 내고 있나」에서 끝나면 정리할 계기가 없다. 오른 것·끝나가는 것·
     * 오래 손대지 않은 것·기한 없는 것을 골라 놓는 것이 이 화면의 쓸모다.
     */
    private LedgerRecurringDtos.Signals signals(List<LedgerRecurring> rules) {
        LocalDate today = clock.today();
        List<LedgerRecurringDtos.PriceIncrease> increased = new ArrayList<>();
        List<LedgerRecurringDtos.TrialEnding> trials = new ArrayList<>();
        List<Long> longUnchanged = new ArrayList<>();
        List<Long> noEndDate = new ArrayList<>();

        Map<Long, List<LedgerRecurringAmountHistory>> histories = historiesOf(rules);
        for (LedgerRecurring rule : rules) {
            if (rule.getStatus() == LedgerRecurringStatus.ENDED) {
                continue;
            }
            if (rule.getEndDate() == null) {
                noEndDate.add(rule.getId());
            }
            // 아직 한 번도 안 나갔는데 곧 처음 나간다 = 무료 체험이 끝나간다.
            if (!rule.getStartDate().isBefore(today)
                    && !rule.getStartDate().isAfter(today.plusDays(TRIAL_ENDING_DAYS))
                    && !transactionRepository.existsByRecurringIdAndDeletedAtIsNull(rule.getId())) {
                trials.add(new LedgerRecurringDtos.TrialEnding(
                        rule.getId(), rule.getName(), rule.getStartDate(), rule.getAmount()));
            }

            List<LedgerRecurringAmountHistory> history = histories.getOrDefault(
                    rule.getId(), List.of());
            if (history.isEmpty()) {
                continue;
            }
            LedgerRecurringAmountHistory last = history.get(history.size() - 1);
            if (history.size() >= 2) {
                LedgerRecurringAmountHistory previous = history.get(history.size() - 2);
                if (last.getAmount() > previous.getAmount()
                        && last.getEffectiveFrom().isAfter(
                                today.minusMonths(PRICE_INCREASE_MONTHS))) {
                    increased.add(new LedgerRecurringDtos.PriceIncrease(
                            rule.getId(), rule.getName(), previous.getAmount(),
                            last.getAmount(), last.getEffectiveFrom()));
                }
            }
            if (last.getEffectiveFrom().isBefore(today.minusMonths(LONG_UNCHANGED_MONTHS))) {
                longUnchanged.add(rule.getId());
            }
        }
        return new LedgerRecurringDtos.Signals(increased, trials, longUnchanged, noEndDate);
    }

    /** 미납 회차. 확정하거나 건너뛰어야만 사라진다 — 「무시」는 없다(§6.4). */
    private List<LedgerRecurringDtos.OverdueView> overdue(List<LedgerRecurring> rules) {
        if (rules.isEmpty()) {
            return List.of();
        }
        LocalDate today = clock.today();
        Map<Long, LedgerRecurring> byId = new HashMap<>();
        rules.forEach(rule -> byId.put(rule.getId(), rule));

        List<LedgerRecurringDtos.OverdueView> views = new ArrayList<>();
        for (LedgerRecurringOverride override : overrideRepository
                .findAllByRecurringIdInAndAction(byId.keySet(), LedgerOverrideAction.UNPAID)) {
            LedgerRecurring rule = byId.get(override.getRecurringId());
            if (rule == null) {
                continue;
            }
            long amount = override.getAmount() == null ? rule.getAmount() : override.getAmount();
            views.add(new LedgerRecurringDtos.OverdueView(
                    rule.getId(), rule.getName(), override.getOccurrenceDate(), amount,
                    ChronoUnit.DAYS.between(
                            override.getOccurrenceDate(), today),
                    override.getNote()));
        }
        views.sort(Comparator.comparing(LedgerRecurringDtos.OverdueView::occurrenceDate));
        return views;
    }

    private Map<Long, List<LedgerRecurringAmountHistory>> historiesOf(
            List<LedgerRecurring> rules) {
        if (rules.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<LedgerRecurringAmountHistory>> byRule = new HashMap<>();
        List<Long> ids = rules.stream().map(LedgerRecurring::getId).toList();
        for (LedgerRecurringAmountHistory row
                : historyRepository.findAllByRecurringIdInOrderByEffectiveFromAscIdAsc(ids)) {
            byRule.computeIfAbsent(row.getRecurringId(), key -> new ArrayList<>()).add(row);
        }
        return byRule;
    }

    LedgerRecurringDtos.RecurringView view(LedgerRecurring rule, Map<Long, String> assets,
                                           Map<Long, String> categories) {
        LocalDate next = rule.getStatus() == LedgerRecurringStatus.ENDED
                ? null : nextDateOf(rule);
        return new LedgerRecurringDtos.RecurringView(
                rule.getId(), rule.getName(), rule.getKind(), rule.getTxType(), rule.getAmount(),
                rule.getAmountType(), rule.getAssetId(), assets.get(rule.getAssetId()),
                rule.getCounterAssetId(), rule.getCategoryId(),
                categories.get(rule.getCategoryId()),
                rule.getFreqType(), rule.getFreqInterval(), rule.getFreqDay(), rule.getFreqMonth(),
                LedgerFrequencyLabel.of(rule), rule.getBusinessDayPolicy(),
                rule.getStartDate(), rule.getEndDate(), rule.getPausedFrom(), rule.getPausedTo(),
                rule.getStatus(), rule.getEndedOn(), rule.getCancelUrl(), rule.getMemo(),
                next, LedgerRecurrence.monthlyEquivalent(rule));
    }

    /** 다음 결제일에는 영업일 보정이 반영돼야 한다 — 사람이 보는 건 실제로 빠지는 날이다. */
    private LocalDate nextDateOf(LedgerRecurring rule) {
        LocalDate next = LedgerRecurrence.next(rule, clock.today());
        return next == null ? null : resolver.adjust(rule, next);
    }

    private void validateRule(LedgerFrequencyType type, Integer interval,
                              Integer day, Integer month) {
        if (!LedgerRecurrence.isComplete(type, interval, day, month)) {
            throw new CustomException(ErrorCode.LEDGER_RECURRING_INVALID_RULE);
        }
    }

    /**
     * 대상 검증. 마지막 조건이 자동 기록의 <b>유일한 제외 대상</b>을 막는다.
     *
     * <p>카드 대금은 잔고 부족·리볼빙·선결제 때문에 실제 출금액을 앱이 알 수 없다(§7.2).
     * 정기 항목으로 만들 수 있게 두면 매달 「예상 청구액」이 원장에 사실인 척 적힌다.
     */
    private void validateTarget(Long memberId, LedgerFlow txType, Long assetId,
                                Long counterAssetId, Long categoryId) {
        LedgerAsset asset = assetRepository.findByIdAndMemberId(assetId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_ASSET_NOT_FOUND));
        if (txType == LedgerFlow.TRANSFER) {
            if (counterAssetId == null) {
                throw new CustomException(ErrorCode.LEDGER_TRANSFER_COUNTER_REQUIRED);
            }
            if (counterAssetId.equals(asset.getId())) {
                throw new CustomException(ErrorCode.LEDGER_TRANSFER_SAME_ASSET);
            }
            LedgerAsset counter = assetRepository.findByIdAndMemberId(counterAssetId, memberId)
                    .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_ASSET_NOT_FOUND));
            if (counter.getType() == LedgerAssetType.CREDIT_CARD) {
                throw new CustomException(ErrorCode.LEDGER_RECURRING_CARD_PAYMENT);
            }
        }
        if (categoryId == null) {
            return;
        }
        LedgerCategory category = categoryRepository.findByIdAndMemberId(categoryId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_CATEGORY_NOT_FOUND));
        if (txType != LedgerFlow.TRANSFER && category.getFlow() != txType) {
            throw new CustomException(ErrorCode.LEDGER_CATEGORY_FLOW_MISMATCH);
        }
    }

    private Map<Long, String> assetNames(Long memberId) {
        Map<Long, String> names = new HashMap<>();
        assetRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId)
                .forEach(asset -> names.put(asset.getId(), asset.getName()));
        return names;
    }

    private Map<Long, String> categoryNames(Long memberId) {
        Map<Long, String> names = new HashMap<>();
        categoryRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId)
                .forEach(category -> names.put(category.getId(), category.getName()));
        return names;
    }
}
