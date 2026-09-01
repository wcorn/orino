package ds.project.orino.planner.ledger.asset;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerAssetGroup;
import ds.project.orino.domain.planner.ledger.entity.LedgerAssetGroupKind;
import ds.project.orino.domain.planner.ledger.entity.LedgerAssetType;
import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransaction;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionSource;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;
import ds.project.orino.domain.planner.ledger.repository.LedgerAssetGroupRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerAssetRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerCategoryRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerRecurringRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerSettingsRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerStatementRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionTemplateRepository;
import ds.project.orino.planner.ledger.asset.dto.AssetDetailResponse;
import ds.project.orino.planner.ledger.asset.dto.AssetListResponse;
import ds.project.orino.planner.ledger.asset.dto.AssetRequests;
import ds.project.orino.planner.ledger.asset.dto.AssetTransactionsResponse;
import ds.project.orino.planner.ledger.asset.dto.AssetView;
import ds.project.orino.planner.ledger.asset.dto.ReconcileDtos;
import ds.project.orino.planner.ledger.common.LedgerBalances;
import ds.project.orino.planner.ledger.common.LedgerBootstrap;
import ds.project.orino.planner.ledger.common.LedgerCategorySpending;
import ds.project.orino.planner.ledger.common.LedgerClock;
import ds.project.orino.planner.ledger.common.LedgerNames;
import ds.project.orino.planner.ledger.transaction.dto.TransactionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 자산 읽기·쓰기. <b>잔액은 여기서 계산되지 저장되지 않는다</b>(D-8).
 *
 * <p>쓰인 자산은 지우지 않는다. 숨길 뿐이다 — 해지한 카드의 지난 내역이 갈 곳을 잃으면
 * 그것도 원장이 틀어지는 길이다. <b>아직 아무것도 붙지 않은 자산에만</b> 삭제가 열려 있다:
 * 잘못 만든 줄 하나를 되돌릴 길이 없으면 사람은 「숨긴 자산」 목록에 쓰레기를 쌓는다.
 */
@Service
public class LedgerAssetService {

    /** 「그 외」 묶음의 이름. 그룹 없이 만든 자산이 갈 곳이다. */
    private static final String UNGROUPED_NAME = "그 외";

    private static final int TREND_DAYS = 30;
    private static final int TREND_MONTHS = 12;
    private static final int TREND_YEARS = 5;

    private final LedgerAssetRepository assetRepository;
    private final LedgerAssetGroupRepository groupRepository;
    private final LedgerCategoryRepository categoryRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerTransactionTemplateRepository templateRepository;
    private final LedgerRecurringRepository recurringRepository;
    private final LedgerStatementRepository statementRepository;
    private final LedgerSettingsRepository settingsRepository;
    private final LedgerBootstrap bootstrap;
    private final LedgerClock clock;

    public LedgerAssetService(LedgerAssetRepository assetRepository,
                              LedgerAssetGroupRepository groupRepository,
                              LedgerCategoryRepository categoryRepository,
                              LedgerTransactionRepository transactionRepository,
                              LedgerTransactionTemplateRepository templateRepository,
                              LedgerRecurringRepository recurringRepository,
                              LedgerStatementRepository statementRepository,
                              LedgerSettingsRepository settingsRepository,
                              LedgerBootstrap bootstrap,
                              LedgerClock clock) {
        this.assetRepository = assetRepository;
        this.groupRepository = groupRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.templateRepository = templateRepository;
        this.recurringRepository = recurringRepository;
        this.statementRepository = statementRepository;
        this.settingsRepository = settingsRepository;
        this.bootstrap = bootstrap;
        this.clock = clock;
    }

    @Transactional
    public AssetListResponse list(Long memberId) {
        bootstrap.ensureSeeded(memberId);

        List<LedgerAsset> assets = assetRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId);
        LedgerBalances balances = balances(memberId, assets);
        Map<Long, String> assetNames = new HashMap<>();
        assets.forEach(asset -> assetNames.put(asset.getId(), asset.getName()));

        Map<Long, LedgerAssetGroup> groups = new LinkedHashMap<>();
        for (LedgerAssetGroup group : groupRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId)) {
            groups.put(group.getId(), group);
        }

        Map<Long, List<AssetView>> visibleByGroup = new LinkedHashMap<>();
        List<AssetView> hidden = new ArrayList<>();
        for (LedgerAsset asset : assets) {
            AssetView view = AssetView.of(asset, assetNames.get(asset.getLinkedAssetId()),
                    balances.balanceOf(asset.getId()), balances.unpaidOf(asset.getId()));
            if (asset.isHidden()) {
                hidden.add(view);
            } else {
                visibleByGroup.computeIfAbsent(asset.getGroupId(), key -> new ArrayList<>()).add(view);
            }
        }

        List<AssetListResponse.GroupView> groupViews = new ArrayList<>();
        for (LedgerAssetGroup group : groups.values()) {
            List<AssetView> members = visibleByGroup.getOrDefault(group.getId(), List.of());
            groupViews.add(new AssetListResponse.GroupView(
                    group.getId(), group.getName(), group.getKind(), group.getDisplayOrder(),
                    group.isCollapsed(), subtotal(members), members));
        }
        List<AssetView> ungrouped = visibleByGroup.getOrDefault(null, List.of());
        if (!ungrouped.isEmpty()) {
            // 그룹 없는 자산이 있을 때만 「그 외」를 만든다 — 늘 보이면 빈 묶음이 남는다.
            groupViews.add(new AssetListResponse.GroupView(
                    null, UNGROUPED_NAME, LedgerAssetGroupKind.ETC, Integer.MAX_VALUE,
                    false, subtotal(ungrouped), ungrouped));
        }

        return new AssetListResponse(groupViews, hidden,
                balances.totalAssets(), balances.liabilities(), balances.netWorth());
    }

    @Transactional
    public AssetView create(Long memberId, AssetRequests.Create request) {
        bootstrap.ensureSeeded(memberId);

        LedgerAsset asset = new LedgerAsset(memberId, request.name(), request.type());
        if (request.groupId() != null) {
            requireGroup(memberId, request.groupId());
            asset.updateGroupId(request.groupId());
        }
        asset.updateAccountLast4(request.accountLast4());
        if (request.displayOrder() != null) {
            asset.updateDisplayOrder(request.displayOrder());
        }
        asset.updateMaturityDate(request.maturityDate());
        asset.updateTargetAmount(request.targetAmount());
        applyLink(memberId, asset, request.type(), request.linkedAssetId());

        assetRepository.save(asset);
        return AssetView.of(asset, linkedName(memberId, asset), null, null);
    }

    @Transactional
    public AssetView update(Long memberId, Long id, AssetRequests.Update request) {
        LedgerAsset asset = requireAsset(memberId, id);

        if (request.name() != null) {
            asset.updateName(request.name());
        }
        if (Boolean.TRUE.equals(request.clearGroup())) {
            asset.updateGroupId(null);
        } else if (request.groupId() != null) {
            requireGroup(memberId, request.groupId());
            asset.updateGroupId(request.groupId());
        }
        if (request.accountLast4() != null) {
            asset.updateAccountLast4(request.accountLast4());
        }
        if (request.displayOrder() != null) {
            asset.updateDisplayOrder(request.displayOrder());
        }
        if (request.hidden() != null) {
            asset.updateHidden(request.hidden(), request.closedReason());
        }
        if (request.maturityDate() != null) {
            asset.updateMaturityDate(request.maturityDate());
        }
        if (request.targetAmount() != null) {
            asset.updateTargetAmount(request.targetAmount());
        }
        if (request.linkedAssetId() != null) {
            applyLink(memberId, asset, asset.getType(), request.linkedAssetId());
        }

        List<LedgerAsset> assets = assetRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId);
        LedgerBalances balances = balances(memberId, assets);
        return AssetView.of(asset, linkedName(memberId, asset),
                balances.balanceOf(asset.getId()), balances.unpaidOf(asset.getId()));
    }

    /**
     * 자산 삭제. <b>아직 아무것도 붙지 않은 자산에만</b> 열린다.
     *
     * <p>거래 한 줄이라도 이 자산을 가리키면 거부한다(`LDG-ERR-034`). 지운 거래도 센다 —
     * 소프트 삭제라 행은 남아 있고, 남은 행이 가리키는 자산을 지우면 그 행은 어느 자산의
     * 것인지 말할 수 없게 된다. 그럴 때 사람이 원하는 것은 삭제가 아니라 <b>해지</b>다.
     *
     * <p>기본 자산으로 걸려 있으면 그것만 풀고 지운다. 없는 자산을 가리키는 설정을 남기면
     * 입력 모달이 열릴 때마다 빈 자리를 고르게 된다.
     */
    @Transactional
    public void delete(Long memberId, Long id) {
        LedgerAsset asset = requireAsset(memberId, id);
        if (inUse(memberId, id)) {
            throw new CustomException(ErrorCode.LEDGER_ASSET_IN_USE);
        }

        settingsRepository.findById(memberId)
                .filter(settings -> id.equals(settings.getDefaultAssetId()))
                .ifPresent(settings -> settings.updateDefaultAssetId(null));

        assetRepository.delete(asset);
    }

    /** 이 자산을 가리키는 것이 하나라도 있는가. 하나라도 있으면 삭제가 아니라 해지다. */
    private boolean inUse(Long memberId, Long id) {
        return transactionRepository.existsByMemberIdAndAssetId(memberId, id)
                || transactionRepository.existsByMemberIdAndCounterAssetId(memberId, id)
                || recurringRepository.existsByMemberIdAndAssetId(memberId, id)
                || recurringRepository.existsByMemberIdAndCounterAssetId(memberId, id)
                || templateRepository.existsByMemberIdAndAssetId(memberId, id)
                || statementRepository.existsByMemberIdAndCardAssetId(memberId, id)
                || assetRepository.existsByMemberIdAndLinkedAssetId(memberId, id)
                || assetRepository.existsByMemberIdAndPaymentAssetId(memberId, id);
    }

    @Transactional
    public AssetDetailResponse detail(Long memberId, Long id, AssetDetailResponse.Range range) {
        LedgerAsset asset = requireAsset(memberId, id);
        List<LedgerAsset> assets = assetRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId);
        LedgerBalances balances = balances(memberId, assets);

        AssetDetailResponse.Range effective = range != null ? range : AssetDetailResponse.Range.MONTH;
        LocalDate today = clock.today();
        LocalDate from = switch (effective) {
            case DAY -> today.minusDays(TREND_DAYS - 1L);
            case MONTH -> today.minusMonths(TREND_MONTHS - 1L).withDayOfMonth(1);
            case YEAR -> today.minusYears(TREND_YEARS - 1L).withDayOfYear(1);
        };

        return new AssetDetailResponse(
                AssetView.of(asset, linkedName(memberId, asset),
                        balances.balanceOf(asset.getId()), balances.unpaidOf(asset.getId())),
                effective,
                trend(memberId, asset, effective, from, today),
                categoryShare(memberId, asset, chargedTo(memberId, asset), from, today));
    }

    /**
     * 그 자산의 내역과 줄마다의 잔액.
     *
     * <p>이체받는 줄도 함께 온다 — 「이 통장에 무슨 일이 있었나」에는 나간 돈과 들어온 돈이
     * 같이 답해야 한다.
     */
    @Transactional(readOnly = true)
    public AssetTransactionsResponse transactions(Long memberId, Long id) {
        LedgerAsset asset = requireAsset(memberId, id);
        LedgerNames names = new LedgerNames(
                assetRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId),
                categoryRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId));

        Set<Long> owned = chargedTo(memberId, asset);
        List<LedgerTransaction> rows = transactionRepository.findAllForAssetsOldestFirst(
                memberId, owned);

        boolean tracksBalance = asset.getType().holdsBalance()
                || asset.getType() == LedgerAssetType.CREDIT_CARD;
        long running = 0;
        List<AssetTransactionsResponse.Row> items = new ArrayList<>();
        for (LedgerTransaction tx : rows) {
            Long balanceAfter = null;
            if (tracksBalance && tx.getStatus() == LedgerTransactionStatus.CONFIRMED) {
                running += delta(asset, owned, tx);
                balanceAfter = running;
            }
            items.add(new AssetTransactionsResponse.Row(
                    TransactionView.of(tx, names.assetName(tx.getAssetId()),
                            names.assetName(tx.getCounterAssetId()),
                            names.categoryName(tx.getCategoryId()), List.of()),
                    balanceAfter));
        }
        // 화면은 최신이 위다. 잔액은 오래된 것부터 쌓아야 나오므로 계산 뒤에 뒤집는다.
        java.util.Collections.reverse(items);
        return new AssetTransactionsResponse(items);
    }

    @Transactional
    public List<AssetListResponse.GroupView> createGroup(Long memberId, AssetRequests.GroupCreate request) {
        LedgerAssetGroup group = new LedgerAssetGroup(memberId, request.name(), request.kind(),
                request.displayOrder() != null ? request.displayOrder() : 0);
        groupRepository.save(group);
        return list(memberId).groups();
    }

    @Transactional
    public List<AssetListResponse.GroupView> updateGroup(Long memberId, Long id,
                                                         AssetRequests.GroupUpdate request) {
        LedgerAssetGroup group = requireGroup(memberId, id);
        group.update(request.name(), request.kind(), request.displayOrder(), request.collapsed());
        return list(memberId).groups();
    }

    // --- 내부 ---

    /**
     * 그룹 합계.
     *
     * <p>신용카드의 미결제 사용액은 <b>빼야 한다</b> — 그건 자산이 아니라 빚이다. 더하면
     * 카드사 그룹의 합계가 양수로 나와 「이만큼 있다」로 읽히고, 그 합을 다 더한 값이
     * 화면 맨 위의 총자산과 어긋난다(실제로 어긋났다).
     */
    private long subtotal(List<AssetView> members) {
        long sum = 0;
        for (AssetView view : members) {
            if (view.balance() != null) {
                sum += view.balance();
            } else if (view.unpaidAmount() != null) {
                sum -= view.unpaidAmount();
            }
        }
        return sum;
    }

    /**
     * 이 거래가 그 자산의 잔액을 얼마나 움직이는가. 신용카드는 「빚이 얼마나 늘었나」다.
     *
     * <p>{@code owned}는 <b>그 자산에서 돈이 빠지는 자산들</b>이다 — 자기 자신과, 자기를
     * 연결 계좌로 삼은 체크카드들. 체크카드 지출을 빼고 세면 이 화면의 잔액이 자산 목록의
     * 잔액과 어긋난다. 저장된 잔액을 두지 않기로 한 이유가 바로 그 어긋남인데,
     * 화면 두 곳이 다른 값을 말하면 같은 문제가 자리만 옮긴 것이다.
     */
    private long delta(LedgerAsset asset, Set<Long> owned, LedgerTransaction tx) {
        boolean creditCard = asset.getType() == LedgerAssetType.CREDIT_CARD;
        boolean outgoing = owned.contains(tx.getAssetId());
        if (creditCard) {
            if (!outgoing) {
                // 카드로 들어온 이체 = 대금 납부. 빚이 준다.
                return -tx.getAmount();
            }
            return switch (tx.getType()) {
                case EXPENSE -> tx.getAmount();
                case INCOME -> -tx.getAmount();
                case TRANSFER -> 0L;
            };
        }
        if (!outgoing) {
            return tx.getAmount();
        }
        return switch (tx.getType()) {
            case INCOME -> tx.getAmount();
            case EXPENSE, TRANSFER -> -tx.getAmount();
        };
    }

    private List<AssetDetailResponse.TrendPoint> trend(Long memberId, LedgerAsset asset,
                                                       AssetDetailResponse.Range range,
                                                       LocalDate from, LocalDate to) {
        if (asset.getType() == LedgerAssetType.DEBIT_CARD) {
            // 체크카드에는 잔액이 없다. 없는 값을 0으로 그려 두면 있는 것처럼 읽힌다.
            return List.of();
        }
        Set<Long> owned = chargedTo(memberId, asset);
        List<LedgerTransaction> rows = transactionRepository.findAllForAssetsOldestFirst(
                memberId, owned);

        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();
        for (LocalDate cursor = bucketOf(from, range); !cursor.isAfter(bucketOf(to, range));
                cursor = next(cursor, range)) {
            buckets.put(cursor, new long[]{0, 0, 0});
        }

        long running = 0;
        Map<LocalDate, Long> balanceAtBucket = new HashMap<>();
        for (LedgerTransaction tx : rows) {
            if (tx.getStatus() != LedgerTransactionStatus.CONFIRMED) {
                continue;
            }
            running += delta(asset, owned, tx);
            LocalDate bucket = bucketOf(tx.getOccurredOn(), range);
            balanceAtBucket.put(bucket, running);
            long[] cell = buckets.get(bucket);
            if (cell != null) {
                boolean refund = tx.getSource() == LedgerTransactionSource.REFUND;
                long signed = refund ? -tx.getAmount() : tx.getAmount();
                LedgerFlow flow = refund ? opposite(tx.getType()) : tx.getType();
                if (flow == LedgerFlow.INCOME) {
                    cell[1] += signed;
                } else if (flow == LedgerFlow.EXPENSE) {
                    cell[2] += signed;
                }
            }
        }

        // 거래가 없는 구간은 직전 잔액을 그대로 잇는다 — 끊어 그리면 0으로 떨어진 것처럼 보인다.
        long carried = openingBalance(rows, asset, owned, bucketOf(from, range), range);
        List<AssetDetailResponse.TrendPoint> points = new ArrayList<>();
        for (Map.Entry<LocalDate, long[]> entry : buckets.entrySet()) {
            Long atBucket = balanceAtBucket.get(entry.getKey());
            if (atBucket != null) {
                carried = atBucket;
            }
            points.add(new AssetDetailResponse.TrendPoint(
                    entry.getKey(), carried, entry.getValue()[1], entry.getValue()[2]));
        }
        return points;
    }

    /** 첫 구간이 시작되기 전까지 쌓인 잔액. 그래프의 출발점이다. */
    private long openingBalance(List<LedgerTransaction> rows, LedgerAsset asset, Set<Long> owned,
                                LocalDate firstBucket, AssetDetailResponse.Range range) {
        long running = 0;
        for (LedgerTransaction tx : rows) {
            if (tx.getStatus() != LedgerTransactionStatus.CONFIRMED) {
                continue;
            }
            if (!bucketOf(tx.getOccurredOn(), range).isBefore(firstBucket)) {
                break;
            }
            running += delta(asset, owned, tx);
        }
        return running;
    }

    private LocalDate bucketOf(LocalDate date, AssetDetailResponse.Range range) {
        return switch (range) {
            case DAY -> date;
            case MONTH -> date.withDayOfMonth(1);
            case YEAR -> date.withDayOfYear(1);
        };
    }

    private LocalDate next(LocalDate bucket, AssetDetailResponse.Range range) {
        return switch (range) {
            case DAY -> bucket.plusDays(1);
            case MONTH -> bucket.plusMonths(1);
            case YEAR -> bucket.plusYears(1);
        };
    }

    private LedgerFlow opposite(LedgerFlow type) {
        return switch (type) {
            case EXPENSE -> LedgerFlow.INCOME;
            case INCOME -> LedgerFlow.EXPENSE;
            case TRANSFER -> LedgerFlow.TRANSFER;
        };
    }

    /**
     * 이 자산의 지출 분포. <b>통계 화면과 같은 규칙</b>을 쓴다({@link LedgerCategorySpending}) —
     * 이체는 빠지고 환불은 그 카테고리를 깎는다. 두 화면이 각자 세면 같은 달의 「식비」가
     * 두 값이 된다.
     */
    private List<AssetDetailResponse.CategoryShare> categoryShare(Long memberId, LedgerAsset asset,
                                                                  Set<Long> owned,
                                                                  LocalDate from, LocalDate to) {
        LedgerNames names = new LedgerNames(List.of(),
                categoryRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId));
        List<AssetDetailResponse.CategoryShare> shares = new ArrayList<>();
        for (LedgerCategorySpending.Bucket bucket : LedgerCategorySpending.netExpense(
                transactionRepository.sumByCategoryAndFlowForAssets(
                        memberId, owned, LedgerTransactionStatus.CONFIRMED, from, to))) {
            shares.add(new AssetDetailResponse.CategoryShare(
                    bucket.categoryId(), names.categoryName(bucket.categoryId()),
                    bucket.amount(), bucket.count()));
        }
        return shares;
    }

    /**
     * 잔액 맞추기(`LDG-004`) — 실제 잔액을 받아 차액을 「조정」 거래로 만든다.
     *
     * <p>어긋난 원장을 <b>포기하지 않고 드러내서 복구하는</b> 장치다. 잔액을 컬럼으로 저장하지
     * 않기로 한 이상(D-8) 어긋남은 반드시 생기고, 그때 고칠 길이 없으면 사람은 가계부를 버린다.
     *
     * <p><b>차이가 0이면 아무것도 만들지 않는다.</b> 어긋남을 감추지 않되 없는 거래를 만들지도
     * 않는다 — 0원짜리 조정 줄이 매달 쌓이면 그것대로 원장이 지저분해진다.
     *
     * <p>조정 거래는 <b>미분류</b>다. 「돈이 어디로 갔는지 모른다」가 사실이고, 그 상태로
     * 「정리할 내역」에 남아 나중에 채워지는 것이 맞다.
     */
    @Transactional
    public ReconcileDtos.Response reconcile(Long memberId, Long id, ReconcileDtos.Request request) {
        LedgerAsset asset = requireAsset(memberId, id);
        if (!asset.getType().holdsBalance()) {
            // 카드에는 맞출 잔액이 없다. 신용카드의 차이는 청구서로 푸는 문제다(v1.5).
            throw new CustomException(ErrorCode.BAD_REQUEST,
                    "잔액을 갖는 자산에서만 맞출 수 있습니다.");
        }

        List<LedgerAsset> assets = assetRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId);
        long derived = balances(memberId, assets).balanceOf(asset.getId());
        long difference = request.actualBalance() - derived;
        if (difference == 0) {
            return new ReconcileDtos.Response(null, 0, derived);
        }

        LedgerTransaction adjustment = new LedgerTransaction(
                memberId,
                difference > 0 ? LedgerFlow.INCOME : LedgerFlow.EXPENSE,
                LedgerTransactionStatus.CONFIRMED,
                request.occurredOn() != null ? request.occurredOn() : clock.today(),
                Math.abs(difference),
                asset.getId(),
                LedgerTransactionSource.ADJUSTMENT);
        adjustment.updateTitle("잔액 조정");
        adjustment.updateMemo(request.memo());
        transactionRepository.save(adjustment);

        return new ReconcileDtos.Response(
                adjustment.getId(), difference, request.actualBalance());
    }

    /**
     * 그 자산에서 돈이 빠지는 자산들 — 자기 자신과, 자기를 연결 계좌로 삼은 체크카드들.
     *
     * <p>체크카드 지출은 카드에 붙어 있지만 실제로 줄어드는 것은 이 계좌다(D-4).
     * 계좌 화면이 그 줄들을 빼고 그리면 잔액이 맞지 않는다.
     */
    private Set<Long> chargedTo(Long memberId, LedgerAsset asset) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(asset.getId());
        if (asset.getType().holdsBalance()) {
            assetRepository.findAllByMemberIdAndLinkedAssetId(memberId, asset.getId())
                    .forEach(card -> ids.add(card.getId()));
        }
        return ids;
    }

    private LedgerBalances balances(Long memberId, List<LedgerAsset> assets) {
        return LedgerBalances.of(assets,
                transactionRepository.sumConfirmedByAssetAndType(
                        memberId, LedgerTransactionStatus.CONFIRMED),
                transactionRepository.sumConfirmedByCounterAsset(
                        memberId, LedgerTransactionStatus.CONFIRMED));
    }

    /**
     * 체크카드에는 연결 계좌를 강제한다(LDG-ERR-019).
     *
     * <p>연결 없는 체크카드는 잔액이 어디서도 빠지지 않는 유령 자산이 된다 — 카드로 5만원을
     * 써도 총자산이 그대로다.
     */
    private void applyLink(Long memberId, LedgerAsset asset, LedgerAssetType type, Long linkedAssetId) {
        if (type != LedgerAssetType.DEBIT_CARD) {
            asset.updateLinkedAssetId(linkedAssetId);
            return;
        }
        if (linkedAssetId == null) {
            throw new CustomException(ErrorCode.LEDGER_DEBIT_CARD_LINK_REQUIRED);
        }
        LedgerAsset linked = requireAsset(memberId, linkedAssetId);
        if (!linked.getType().holdsBalance()) {
            // 카드에 카드를 물릴 수는 없다. 돈이 실제로 들어 있는 자산이어야 한다.
            throw new CustomException(ErrorCode.LEDGER_DEBIT_CARD_LINK_REQUIRED);
        }
        asset.updateLinkedAssetId(linkedAssetId);
    }

    private String linkedName(Long memberId, LedgerAsset asset) {
        if (asset.getLinkedAssetId() == null) {
            return null;
        }
        return assetRepository.findByIdAndMemberId(asset.getLinkedAssetId(), memberId)
                .map(LedgerAsset::getName)
                .orElse(null);
    }

    private LedgerAsset requireAsset(Long memberId, Long id) {
        return assetRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_ASSET_NOT_FOUND));
    }

    private LedgerAssetGroup requireGroup(Long memberId, Long id) {
        return groupRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_ASSET_NOT_FOUND));
    }
}
