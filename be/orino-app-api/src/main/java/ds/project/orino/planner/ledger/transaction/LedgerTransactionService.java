package ds.project.orino.planner.ledger.transaction;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerCategory;
import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerTag;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransaction;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionSource;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionTag;
import ds.project.orino.domain.planner.ledger.repository.LedgerAssetRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerCategoryRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTagRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionTagRepository;
import ds.project.orino.planner.ledger.common.LedgerBootstrap;
import ds.project.orino.planner.ledger.common.LedgerClock;
import ds.project.orino.planner.ledger.common.LedgerNames;
import ds.project.orino.planner.ledger.fx.LedgerFxService;
import ds.project.orino.planner.ledger.transaction.dto.BulkCreateResponse;
import ds.project.orino.planner.ledger.transaction.dto.BulkRequest;
import ds.project.orino.planner.ledger.transaction.dto.BulkResponse;
import ds.project.orino.planner.ledger.transaction.dto.FxInput;
import ds.project.orino.planner.ledger.transaction.dto.RefundRequest;
import ds.project.orino.planner.ledger.transaction.dto.RefundResponse;
import ds.project.orino.planner.ledger.transaction.dto.SuggestionView;
import ds.project.orino.planner.ledger.transaction.dto.TransactionCreateRequest;
import ds.project.orino.planner.ledger.transaction.dto.TransactionCreatedResponse;
import ds.project.orino.planner.ledger.transaction.dto.TransactionListResponse;
import ds.project.orino.planner.ledger.transaction.dto.TransactionUpdateRequest;
import ds.project.orino.planner.ledger.transaction.dto.TransactionView;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 원장 쓰기·읽기.
 *
 * <p>이 클래스가 지키는 것이 곧 이 모듈이 틀리면 안 되는 것들이다.
 * <ol>
 *   <li>자산 없는 거래를 만들 수 없다</li>
 *   <li>이체는 지출·수입 합계에 잡히지 않는다</li>
 *   <li>미래 날짜는 예정으로 강제된다</li>
 *   <li>지우지 않고 상쇄한다 — 환불은 원 거래를 남긴다</li>
 *   <li>외화는 저장하는 순간 고정된다 — 환율표가 갱신돼도 과거 거래는 그대로다</li>
 * </ol>
 */
@Service
public class LedgerTransactionService {

    /** 자동완성이 훑는 최근 거래 수. 이보다 옛것까지 뒤져 봐야 제안이 좋아지지 않는다. */
    private static final int SUGGEST_SCAN_LIMIT = 200;
    private static final int SUGGEST_RESULT_LIMIT = 5;

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerTransactionTagRepository transactionTagRepository;
    private final LedgerAssetRepository assetRepository;
    private final LedgerCategoryRepository categoryRepository;
    private final LedgerTagRepository tagRepository;
    private final LedgerFxService fxService;
    private final LedgerBootstrap bootstrap;
    private final LedgerClock clock;

    public LedgerTransactionService(LedgerTransactionRepository transactionRepository,
                                    LedgerTransactionTagRepository transactionTagRepository,
                                    LedgerAssetRepository assetRepository,
                                    LedgerCategoryRepository categoryRepository,
                                    LedgerTagRepository tagRepository,
                                    LedgerFxService fxService,
                                    LedgerBootstrap bootstrap,
                                    LedgerClock clock) {
        this.transactionRepository = transactionRepository;
        this.transactionTagRepository = transactionTagRepository;
        this.assetRepository = assetRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.fxService = fxService;
        this.bootstrap = bootstrap;
        this.clock = clock;
    }

    @Transactional
    public TransactionCreatedResponse create(Long memberId, TransactionCreateRequest request) {
        bootstrap.ensureSeeded(memberId);
        return saveOne(memberId, request);
    }

    /**
     * 다건 입력(`LDG-015`) — 카드 명세서를 보며 몰아 적을 때 쓴다.
     *
     * <p><b>한 트랜잭션이다.</b> 열 줄 중 하나가 거부되면 아홉 줄도 들어가지 않는다 — 일부만
     * 들어간 원장은 「어디까지 적었더라」를 사람이 다시 맞춰야 하고, 그건 몰아 적는 이유를 없앤다.
     *
     * <p>가져오기(#1268)와 겹치지 않는다. 그건 파일이 있을 때고, 이건 화면을 보며 손으로 옮길 때다 —
     * 카드 명세서 PDF처럼 파일로 못 받는 경우가 실제로 많다.
     */
    @Transactional
    public BulkCreateResponse createAll(Long memberId, List<TransactionCreateRequest> requests) {
        bootstrap.ensureSeeded(memberId);

        int scheduled = 0;
        List<TransactionView> created = new ArrayList<>();
        for (TransactionCreateRequest request : requests) {
            TransactionCreatedResponse saved = saveOne(memberId, request);
            created.add(saved.transaction());
            if (saved.savedAs() == LedgerTransactionStatus.SCHEDULED) {
                scheduled++;
            }
        }
        return new BulkCreateResponse(created, scheduled);
    }

    /**
     * 내역 복사(`LDG-014`) — 템플릿으로 만들 만큼 반복되진 않지만 이번 달에 두 번 나오는 지출용.
     *
     * @param useToday 오늘 날짜로 적을지. 아니면 원본 날짜를 그대로 쓴다
     */
    @Transactional
    public TransactionCreatedResponse duplicate(Long memberId, Long id, boolean useToday) {
        LedgerTransaction origin = requireTransaction(memberId, id);
        LocalDate occurredOn = useToday ? clock.today() : origin.getOccurredOn();

        // 복사본은 새 거래다. 상쇄 연결(refundOfId)과 자동 기록 표식은 따라가지 않는다 —
        // 따라가면 원본과 같은 회차로 잡혀 UNIQUE에 걸리거나 환불이 두 번 세어진다.
        TransactionCreateRequest request = new TransactionCreateRequest(
                origin.getType(),
                origin.getAmount(),
                occurredOn,
                origin.getOccurredAt(),
                origin.getAssetId(),
                origin.getCounterAssetId(),
                origin.getCategoryId(),
                origin.getTitle(),
                origin.getMemo(),
                currentTags(memberId, origin.getId()),
                origin.isEstimated(),
                origin.hasFx()
                        ? new FxInput(origin.getFxCurrency(), origin.getFxAmount(), origin.getFxRate())
                        : null);
        return saveOne(memberId, request);
    }

    private TransactionCreatedResponse saveOne(Long memberId, TransactionCreateRequest request) {
        LedgerFlow type = request.type();
        LedgerAsset asset = requireAsset(memberId, request.assetId());
        Long counterAssetId = resolveCounterAsset(memberId, type, asset, request.counterAssetId());
        validateCategory(memberId, request.categoryId(), type);

        Money money = resolveMoney(request.amount(), request.fx());

        // 미래 날짜는 예정이다. 별도 메뉴를 외우게 하지 않는다(확정 명세 §4.2).
        LedgerTransactionStatus status = request.occurredOn().isAfter(clock.today())
                ? LedgerTransactionStatus.SCHEDULED
                : LedgerTransactionStatus.CONFIRMED;
        LedgerTransactionSource source = status == LedgerTransactionStatus.SCHEDULED
                ? LedgerTransactionSource.SCHEDULED_ONE_OFF
                : LedgerTransactionSource.MANUAL;

        LedgerTransaction tx = new LedgerTransaction(
                memberId, type, status, request.occurredOn(), money.amount(),
                asset.getId(), source);
        tx.updateCounterAssetId(counterAssetId);
        tx.updateCategoryId(request.categoryId());
        tx.updateTitle(request.title());
        tx.updateMemo(request.memo());
        tx.updateOccurredAt(request.occurredAt());
        tx.updateEstimated(Boolean.TRUE.equals(request.estimated()));
        if (money.hasFx()) {
            tx.applyFx(money.currency(), money.fxAmount(), money.fxRate());
        }
        transactionRepository.save(tx);

        List<String> tags = replaceTags(memberId, tx.getId(), request.tags());
        return new TransactionCreatedResponse(view(memberId, tx, tags), status);
    }

    @Transactional
    public TransactionView update(Long memberId, Long id, TransactionUpdateRequest request) {
        LedgerTransaction tx = requireTransaction(memberId, id);

        LedgerFlow type = request.type() != null ? request.type() : tx.getType();
        LedgerAsset asset = request.assetId() != null
                ? requireAsset(memberId, request.assetId())
                : requireAsset(memberId, tx.getAssetId());
        Long counterAssetId = resolveCounterAsset(memberId, type, asset,
                request.counterAssetId() != null ? request.counterAssetId() : tx.getCounterAssetId());

        Long categoryId = Boolean.TRUE.equals(request.clearCategory())
                ? null
                : (request.categoryId() != null ? request.categoryId() : tx.getCategoryId());
        // 유형이 바뀌면 기존 카테고리도 다시 검사한다 — 지출 카테고리를 단 채 수입이 될 수 없다.
        validateCategory(memberId, categoryId, type);

        tx.updateType(type);
        tx.updateAssetId(asset.getId());
        tx.updateCounterAssetId(counterAssetId);
        tx.updateCategoryId(categoryId);
        if (request.title() != null) {
            tx.updateTitle(request.title());
        }
        if (request.memo() != null) {
            tx.updateMemo(request.memo());
        }
        if (request.occurredAt() != null) {
            tx.updateOccurredAt(request.occurredAt());
        }
        if (request.estimated() != null) {
            tx.updateEstimated(request.estimated());
        }
        if (request.occurredOn() != null) {
            tx.updateOccurredOn(request.occurredOn());
            // 날짜를 과거로 당기면 확정으로, 미래로 밀면 예정으로. 상태는 날짜에서 파생한다.
            tx.updateStatus(request.occurredOn().isAfter(clock.today())
                    ? LedgerTransactionStatus.SCHEDULED
                    : LedgerTransactionStatus.CONFIRMED);
        }

        applyFxUpdate(tx, request);

        if (request.tags() != null) {
            replaceTags(memberId, tx.getId(), request.tags());
        }
        return view(memberId, tx, currentTags(memberId, tx.getId()));
    }

    /** 소프트 삭제. 행은 남는다 — 되돌릴 수 있어야 하고, 원장은 지우는 곳이 아니다. */
    @Transactional
    public void delete(Long memberId, Long id) {
        LedgerTransaction tx = requireTransaction(memberId, id);
        tx.softDelete(clock.now());
    }

    /**
     * 환불·취소. <b>원 거래를 지우지 않고 연결된 반대 거래를 만든다</b>(확정 명세 §4.3).
     *
     * <p>상쇄 거래는 원 거래의 <b>카테고리를 그대로 물려받는다.</b> 지출을 상쇄하는 줄은
     * 유형상 수입이지만, 그 금액이 「수입이 늘었다」로 읽히면 안 되고 「그 카테고리의 지출이
     * 줄었다」로 읽혀야 하기 때문이다. 그래서 이 한 줄만은 카테고리 흐름 검사(LDG-ERR-005)를
     * 지나간다 — 사람이 고른 값이 아니라 원 거래에서 따라온 값이다.
     */
    @Transactional
    public RefundResponse refund(Long memberId, Long id, RefundRequest request) {
        LedgerTransaction original = requireTransaction(memberId, id);

        long alreadyRefunded = refundedTotal(memberId, original.getId());
        long remaining = original.getAmount() - alreadyRefunded;
        long amount = request.amount() != null ? request.amount() : remaining;
        if (amount <= 0 || amount > remaining) {
            throw new CustomException(ErrorCode.BAD_REQUEST,
                    "환불 가능 금액은 " + remaining + "원입니다.");
        }

        LocalDate occurredOn = request.occurredOn() != null ? request.occurredOn() : clock.today();
        LedgerTransaction refund = new LedgerTransaction(
                memberId, oppositeOf(original.getType()), LedgerTransactionStatus.CONFIRMED,
                occurredOn, amount, original.getAssetId(), LedgerTransactionSource.REFUND);
        if (original.getType() == LedgerFlow.TRANSFER) {
            // 이체를 되돌리는 것은 반대 방향 이체다. 자산 두 개를 맞바꾼다.
            refund.updateAssetId(original.getCounterAssetId());
            refund.updateCounterAssetId(original.getAssetId());
        }
        refund.updateCategoryId(original.getCategoryId());
        refund.updateTitle(original.getTitle());
        refund.updateMemo(request.memo());
        refund.updateRefundOfId(original.getId());
        transactionRepository.save(refund);

        long refundedNow = alreadyRefunded + amount;
        return new RefundResponse(
                view(memberId, refund, List.of()),
                original.getId(),
                refundedNow,
                original.getAmount() - refundedNow);
    }

    @Transactional
    public BulkResponse bulk(Long memberId, BulkRequest request) {
        List<LedgerTransaction> targets =
                transactionRepository.findAllByMemberIdAndIdInAndDeletedAtIsNull(memberId, request.ids());
        if (request.action() == BulkRequest.Action.DELETE) {
            targets.forEach(tx -> tx.softDelete(clock.now()));
            return new BulkResponse(targets.size());
        }

        Long categoryId = request.categoryId();
        if (categoryId != null) {
            LedgerCategory category = requireCategory(memberId, categoryId);
            // 유형이 섞인 선택에 한 카테고리를 붙일 수는 없다 — 맞지 않는 건은 조용히 건너뛰지
            // 않고 통째로 거부한다. 일부만 적용되면 무엇이 바뀌었는지 알 수 없다.
            for (LedgerTransaction tx : targets) {
                if (tx.getType() != category.getFlow()) {
                    throw new CustomException(ErrorCode.LEDGER_CATEGORY_FLOW_MISMATCH);
                }
            }
        }
        targets.forEach(tx -> tx.updateCategoryId(categoryId));
        return new BulkResponse(targets.size());
    }

    @Transactional(readOnly = true)
    public TransactionListResponse list(Long memberId, LocalDate from, LocalDate to) {
        LocalDate today = clock.today();
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        // 기본 끝은 오늘+30일이다. 예정이 보이지 않으면 「앞으로 얼마 나가나」에 답할 수 없다.
        LocalDate end = to != null ? to : today.plusDays(30);

        List<LedgerTransaction> rows = transactionRepository
                .findAllByMemberIdAndDeletedAtIsNullAndOccurredOnBetweenOrderByOccurredOnDescIdDesc(
                        memberId, start, end);
        LedgerNames names = names(memberId);
        Map<Long, List<String>> tagsByTransaction = tagsOf(memberId, rows);

        Map<LocalDate, List<TransactionView>> byDate = new LinkedHashMap<>();
        for (LedgerTransaction tx : rows) {
            byDate.computeIfAbsent(tx.getOccurredOn(), key -> new ArrayList<>())
                    .add(TransactionView.of(tx, names.assetName(tx.getAssetId()),
                            names.assetName(tx.getCounterAssetId()),
                            names.categoryName(tx.getCategoryId()),
                            tagsByTransaction.getOrDefault(tx.getId(), List.of())));
        }

        List<TransactionListResponse.DateGroup> groups = new ArrayList<>();
        for (Map.Entry<LocalDate, List<TransactionView>> entry : byDate.entrySet()) {
            long income = 0;
            long expense = 0;
            for (TransactionView item : entry.getValue()) {
                if (item.status() != LedgerTransactionStatus.CONFIRMED) {
                    continue;
                }
                // 환불은 반대 방향으로 적히지만 「수입이 늘었다」가 아니라 「지출이 줄었다」다.
                // 그래서 자기 유형이 아니라 상쇄하는 쪽의 합계를 깎는다(totals()와 같은 규칙).
                boolean refund = item.source() == LedgerTransactionSource.REFUND;
                long signed = refund ? -item.amount() : item.amount();
                LedgerFlow bucket = refund ? oppositeOf(item.type()) : item.type();
                // 이체는 어느 쪽 합계에도 넣지 않는다 — 그래서 두 갈래만 본다.
                if (bucket == LedgerFlow.EXPENSE) {
                    expense += signed;
                } else if (bucket == LedgerFlow.INCOME) {
                    income += signed;
                }
            }
            groups.add(new TransactionListResponse.DateGroup(
                    entry.getKey(), income, expense, entry.getValue()));
        }

        return new TransactionListResponse(today, totals(memberId, start, end), groups);
    }

    @Transactional(readOnly = true)
    public TransactionView get(Long memberId, Long id) {
        LedgerTransaction tx = requireTransaction(memberId, id);
        return view(memberId, tx, currentTags(memberId, id));
    }

    /** 같은 가맹점을 다시 적을 때 지난번 값을 딸려 보낸다. 제목 하나당 가장 최근 것만 남긴다. */
    @Transactional(readOnly = true)
    public List<SuggestionView> suggest(Long memberId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        List<LedgerTransaction> rows = transactionRepository.searchByTitle(
                keyword.trim(), memberId, Limit.of(SUGGEST_SCAN_LIMIT));
        LedgerNames names = names(memberId);

        Map<String, SuggestionView> byTitle = new LinkedHashMap<>();
        for (LedgerTransaction tx : rows) {
            byTitle.computeIfAbsent(tx.getTitle(), title -> new SuggestionView(
                    title, tx.getType(), tx.getCategoryId(), names.categoryName(tx.getCategoryId()),
                    tx.getAssetId(), names.assetName(tx.getAssetId()), tx.getAmount()));
            if (byTitle.size() >= SUGGEST_RESULT_LIMIT) {
                break;
            }
        }
        return List.copyOf(byTitle.values());
    }

    /**
     * 기간 합계. <b>이체는 어느 쪽에도 들어가지 않고</b>, 환불은 반대편을 깎는다.
     *
     * <p>다른 화면(요약·대시보드)이 같은 값을 다시 계산하지 않도록 여기 한 곳에 둔다.
     */
    @Transactional(readOnly = true)
    public TransactionListResponse.MonthTotals totals(Long memberId, LocalDate from, LocalDate to) {
        long income = 0;
        long expense = 0;
        long transfer = 0;
        long scheduledExpense = 0;
        long scheduledIncome = 0;
        int scheduledCount = 0;

        for (LedgerTransactionRepository.FlowSourceTotal row
                : transactionRepository.sumByTypeAndSource(memberId, from, to)) {
            boolean refund = row.getSource() == LedgerTransactionSource.REFUND;
            long signed = refund ? -row.getTotal() : row.getTotal();
            // 환불은 자기 유형이 아니라 <b>상쇄하는 쪽</b>의 합계를 깎는다.
            LedgerFlow bucket = refund ? oppositeOf(row.getType()) : row.getType();

            if (row.getStatus() == LedgerTransactionStatus.SCHEDULED) {
                if (bucket == LedgerFlow.EXPENSE) {
                    scheduledExpense += signed;
                } else if (bucket == LedgerFlow.INCOME) {
                    scheduledIncome += signed;
                }
                scheduledCount++;
                continue;
            }
            if (bucket == LedgerFlow.EXPENSE) {
                expense += signed;
            } else if (bucket == LedgerFlow.INCOME) {
                income += signed;
            } else {
                // 이체는 따로 센다. 지출에도 수입에도 넣지 않는다.
                transfer += signed;
            }
        }
        return new TransactionListResponse.MonthTotals(
                income, expense, transfer, scheduledExpense, scheduledIncome, scheduledCount);
    }

    // --- 내부 ---

    private void applyFxUpdate(LedgerTransaction tx, TransactionUpdateRequest request) {
        if (Boolean.TRUE.equals(request.clearFx())) {
            if (request.amount() == null) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "원화 금액이 필요합니다.");
            }
            tx.clearFx();
            tx.updateAmount(request.amount());
            return;
        }
        if (request.fx() != null) {
            // 카드사가 실제 적용한 환율이 청구서에 찍힌 뒤 고치는 정상 경로다.
            Money money = resolveMoney(request.amount(), request.fx());
            if (money.hasFx()) {
                tx.applyFx(money.currency(), money.fxAmount(), money.fxRate());
            } else {
                tx.clearFx();
                tx.updateAmount(money.amount());
            }
            return;
        }
        if (request.amount() != null) {
            tx.updateAmount(request.amount());
            if (tx.hasFx()) {
                // 원화 금액만 손으로 고쳤다면 외화 근거는 더 이상 그 금액을 설명하지 못한다.
                tx.clearFx();
            }
        }
    }

    /**
     * 금액을 확정한다. 외화면 {@code round(fxAmount × rate)}, 아니면 보낸 원화 그대로.
     *
     * <p>ECB에 닿지 못해도 <b>400을 내지 않는다</b> — 원화 금액을 함께 보냈으면 원화 거래로
     * 적는다. 기록을 막느니 근거가 약한 채로 남긴다(확정 명세 §11.1).
     */
    private Money resolveMoney(Long amount, FxInput fx) {
        if (fx == null) {
            if (amount == null) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "금액이 필요합니다.");
            }
            return Money.krw(amount);
        }
        if (fx.currency() == null || fx.amount() == null) {
            // 셋 중 일부만 온 상태. 반쪽 근거는 나중에 검증할 수 없다.
            throw new CustomException(ErrorCode.LEDGER_FX_INCOMPLETE);
        }
        BigDecimal rate = fx.rate();
        if (rate == null) {
            rate = fxService.resolveRate(fx.currency()).orElse(null);
        }
        if (rate == null) {
            if (amount == null) {
                throw new CustomException(ErrorCode.LEDGER_FX_INCOMPLETE);
            }
            return Money.krw(amount);
        }
        return Money.fx(fx.currency().trim().toUpperCase(), fx.amount(), rate);
    }

    private LedgerFlow oppositeOf(LedgerFlow type) {
        return switch (type) {
            case EXPENSE -> LedgerFlow.INCOME;
            case INCOME -> LedgerFlow.EXPENSE;
            case TRANSFER -> LedgerFlow.TRANSFER;
        };
    }

    private long refundedTotal(Long memberId, Long originalId) {
        return transactionRepository
                .findAllByMemberIdAndRefundOfIdAndDeletedAtIsNull(memberId, originalId)
                .stream()
                .mapToLong(LedgerTransaction::getAmount)
                .sum();
    }

    private LedgerAsset requireAsset(Long memberId, Long assetId) {
        if (assetId == null) {
            // 자산 없는 거래는 만들 수 없다. 이 제약 하나가 잔액과 자산별 뷰를 전부 만든다.
            throw new CustomException(ErrorCode.LEDGER_ASSET_REQUIRED);
        }
        return assetRepository.findByIdAndMemberId(assetId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_ASSET_NOT_FOUND));
    }

    private LedgerCategory requireCategory(Long memberId, Long categoryId) {
        return categoryRepository.findByIdAndMemberId(categoryId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_CATEGORY_NOT_FOUND));
    }

    private LedgerTransaction requireTransaction(Long memberId, Long id) {
        return transactionRepository.findByIdAndMemberIdAndDeletedAtIsNull(id, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_TRANSACTION_NOT_FOUND));
    }

    private Long resolveCounterAsset(Long memberId, LedgerFlow type,
                                     LedgerAsset asset, Long counterAssetId) {
        if (type != LedgerFlow.TRANSFER) {
            // 이체가 아니면 대상 자산은 의미가 없다. 남겨 두면 나중에 이체로 오해된다.
            return null;
        }
        if (counterAssetId == null) {
            throw new CustomException(ErrorCode.LEDGER_TRANSFER_COUNTER_REQUIRED);
        }
        if (counterAssetId.equals(asset.getId())) {
            throw new CustomException(ErrorCode.LEDGER_TRANSFER_SAME_ASSET);
        }
        requireAsset(memberId, counterAssetId);
        return counterAssetId;
    }

    private void validateCategory(Long memberId, Long categoryId, LedgerFlow type) {
        if (categoryId == null) {
            // 미분류를 허용한다. 기록을 막느니 나중에 채운다.
            return;
        }
        LedgerCategory category = requireCategory(memberId, categoryId);
        if (category.getFlow() != type) {
            throw new CustomException(ErrorCode.LEDGER_CATEGORY_FLOW_MISMATCH);
        }
    }

    /** 태그는 이름으로 온다. 없으면 만들고, 있으면 재사용한다. */
    private List<String> replaceTags(Long memberId, Long transactionId, List<String> names) {
        transactionTagRepository.deleteByIdTransactionId(transactionId);
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        Set<String> wanted = new LinkedHashSet<>();
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                wanted.add(name.trim());
            }
        }
        if (wanted.isEmpty()) {
            return List.of();
        }
        Map<String, LedgerTag> existing = new HashMap<>();
        for (LedgerTag tag : tagRepository.findAllByMemberIdAndNameIn(memberId, wanted)) {
            existing.put(tag.getName(), tag);
        }
        List<String> applied = new ArrayList<>();
        for (String name : wanted) {
            LedgerTag tag = existing.computeIfAbsent(name,
                    key -> tagRepository.save(new LedgerTag(memberId, key)));
            transactionTagRepository.save(new LedgerTransactionTag(transactionId, tag.getId()));
            applied.add(name);
        }
        return applied;
    }

    private List<String> currentTags(Long memberId, Long transactionId) {
        List<Long> tagIds = transactionTagRepository.findAllByIdTransactionId(transactionId)
                .stream().map(LedgerTransactionTag::getTagId).toList();
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return tagRepository.findAllByMemberIdAndIdIn(memberId, tagIds)
                .stream().map(LedgerTag::getName).sorted().toList();
    }

    private Map<Long, List<String>> tagsOf(Long memberId, List<LedgerTransaction> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = rows.stream().map(LedgerTransaction::getId).toList();
        List<LedgerTransactionTag> links = transactionTagRepository.findAllByIdTransactionIdIn(ids);
        if (links.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> tagNames = new HashMap<>();
        List<Long> tagIds = links.stream().map(LedgerTransactionTag::getTagId).distinct().toList();
        for (LedgerTag tag : tagRepository.findAllByMemberIdAndIdIn(memberId, tagIds)) {
            tagNames.put(tag.getId(), tag.getName());
        }
        Map<Long, List<String>> byTransaction = new HashMap<>();
        for (LedgerTransactionTag link : links) {
            String name = tagNames.get(link.getTagId());
            if (name != null) {
                byTransaction.computeIfAbsent(link.getTransactionId(), key -> new ArrayList<>())
                        .add(name);
            }
        }
        byTransaction.values().forEach(list -> list.sort(Comparator.naturalOrder()));
        return byTransaction;
    }

    private LedgerNames names(Long memberId) {
        return new LedgerNames(
                assetRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId),
                categoryRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId));
    }

    private TransactionView view(Long memberId, LedgerTransaction tx, List<String> tags) {
        LedgerNames names = names(memberId);
        return TransactionView.of(tx,
                names.assetName(tx.getAssetId()),
                names.assetName(tx.getCounterAssetId()),
                names.categoryName(tx.getCategoryId()),
                tags);
    }

    /** 확정된 금액과 그 근거. 원화만이면 {@code currency}가 비어 있다. */
    private record Money(long amount, String currency, BigDecimal fxAmount, BigDecimal fxRate) {

        static Money krw(long amount) {
            return new Money(amount, null, null, null);
        }

        static Money fx(String currency, BigDecimal fxAmount, BigDecimal fxRate) {
            return new Money(LedgerTransaction.convertToKrw(fxAmount, fxRate),
                    currency, fxAmount, fxRate);
        }

        boolean hasFx() {
            return currency != null;
        }
    }

}
