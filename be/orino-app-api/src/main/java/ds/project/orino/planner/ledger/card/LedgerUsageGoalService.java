package ds.project.orino.planner.ledger.card;

import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerCategory;
import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerInstallmentRound;
import ds.project.orino.domain.planner.ledger.entity.LedgerStatement;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransaction;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;
import ds.project.orino.domain.planner.ledger.entity.LedgerUsageGoalBasis;
import ds.project.orino.domain.planner.ledger.repository.LedgerCategoryRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerInstallmentRoundRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerStatementRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.ledger.common.LedgerClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 카드 실적(`LDG-037`·`LDG-038` · 확정 명세 §7.6).
 *
 * <p><b>집계 기준은 카드 속성이다.</b> 승인이냐 청구냐는 카드사·상품마다 다르고, 전역 설정으로
 * 두면 카드 두 장을 쓰는 순간 한쪽이 반드시 틀린다 — 그리고 틀린 쪽은 「채웠다고 믿었는데
 * 안 채워진」 형태로 드러난다.
 *
 * <p>둘이 갈리는 지점은 <b>할부</b>다. 승인은 긁은 날 전액, 청구는 그 달 회차 금액이다.
 *
 * <p>제외는 <b>카테고리의 「실적 제외」 플래그</b>가 정한다 — 세금·보험료처럼 카드사가 실적으로
 * 안 세는 것들이고, 그 목록은 사람마다 달라서 코드에 박을 수 없다.
 */
@Service
public class LedgerUsageGoalService {

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerCategoryRepository categoryRepository;
    private final LedgerStatementRepository statementRepository;
    private final LedgerInstallmentRoundRepository roundRepository;
    private final LedgerClock clock;

    public LedgerUsageGoalService(LedgerTransactionRepository transactionRepository,
                                  LedgerCategoryRepository categoryRepository,
                                  LedgerStatementRepository statementRepository,
                                  LedgerInstallmentRoundRepository roundRepository,
                                  LedgerClock clock) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.statementRepository = statementRepository;
        this.roundRepository = roundRepository;
        this.clock = clock;
    }

    /**
     * 이번 달 실적 진행 상황. 조건을 안 걸어 둔 카드는 {@code null}이다 —
     * 0%로 그리면 「하나도 못 채웠다」로 읽히는데 사실은 「조건이 없다」다.
     */
    @Transactional(readOnly = true)
    public LedgerCardDtos.UsageGoalView progressOf(LedgerAsset card) {
        if (!card.hasUsageGoal()) {
            return null;
        }
        YearMonth month = YearMonth.from(clock.today());
        long counted = card.getUsageGoalBasis() == LedgerUsageGoalBasis.APPROVAL
                ? approvalBased(card, month)
                : billingBased(card, month);
        long goal = card.getUsageGoalAmount();
        return new LedgerCardDtos.UsageGoalView(
                goal, card.getUsageGoalBasis(), counted,
                Math.max(goal - counted, 0),
                counted >= goal,
                month.toString());
    }

    /** 승인 기준 — 긁은 날, 긁은 금액 전액. 할부도 산 달에 전액이 잡힌다. */
    private long approvalBased(LedgerAsset card, YearMonth month) {
        Set<Long> excluded = excludedCategories(card.getMemberId());
        long sum = 0;
        for (LedgerTransaction row : transactionRepository
                .findAllByMemberIdAndDeletedAtIsNullAndOccurredOnBetweenOrderByOccurredOnDescIdDesc(
                        card.getMemberId(), month.atDay(1), month.atEndOfMonth())) {
            if (!countsForGoal(row, card, excluded)) {
                continue;
            }
            sum += row.getAmount();
        }
        return sum;
    }

    /**
     * 청구 기준 — 그 달 청구서에 실린 금액.
     *
     * <p>할부는 <b>회차 금액</b>만 센다. 원 거래는 이미 청구액에서 빠져 있고(#1262),
     * 여기서도 같은 규칙을 따라야 카드사가 보는 숫자와 맞는다.
     */
    private long billingBased(LedgerAsset card, YearMonth month) {
        Set<Long> excluded = excludedCategories(card.getMemberId());
        List<LedgerStatement> statements = statementRepository
                .findAllByMemberIdAndPaymentDateBetween(
                        card.getMemberId(), month.atDay(1), month.atEndOfMonth())
                .stream()
                .filter(statement -> statement.getCardAssetId().equals(card.getId()))
                .toList();
        if (statements.isEmpty()) {
            return 0;
        }

        long sum = 0;
        List<Long> statementIds = statements.stream().map(LedgerStatement::getId).toList();
        for (Long statementId : statementIds) {
            for (LedgerTransaction row : transactionRepository
                    .findAllByStatementIdAndDeletedAtIsNullOrderByOccurredOnAscIdAsc(statementId)) {
                if (!countsForGoal(row, card, excluded)) {
                    continue;
                }
                sum += row.getAmount();
            }
        }
        for (LedgerInstallmentRound round : roundRepository.findAllByStatementIdIn(statementIds)) {
            sum += round.getAmount();
        }
        return sum;
    }

    /**
     * 이 줄이 실적에 들어가는가.
     *
     * <p>할부 원 거래는 청구 기준에서 빠진다 — 회차로 따로 세기 때문이다. 승인 기준에서는
     * 반대로 원 거래가 전부이고 회차는 세지 않는다.
     */
    private boolean countsForGoal(LedgerTransaction row, LedgerAsset card, Set<Long> excluded) {
        if (!card.getId().equals(row.getAssetId())) {
            return false;
        }
        if (row.getType() != LedgerFlow.EXPENSE
                || row.getStatus() != LedgerTransactionStatus.CONFIRMED) {
            return false;
        }
        if (card.getUsageGoalBasis() == LedgerUsageGoalBasis.BILLING
                && row.getInstallmentId() != null) {
            return false;
        }
        return row.getCategoryId() == null || !excluded.contains(row.getCategoryId());
    }

    /** 실적에서 빼기로 한 카테고리들. 세금·보험료처럼 카드사가 안 세는 것들이다. */
    private Set<Long> excludedCategories(Long memberId) {
        Set<Long> excluded = new HashSet<>();
        for (LedgerCategory category : categoryRepository
                .findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId)) {
            if (category.isExcludeFromCardGoal()) {
                excluded.add(category.getId());
            }
        }
        return excluded;
    }

    /** 여러 카드의 진행 상황을 한 번에. 목록 화면이 카드마다 부르지 않게 한다. */
    @Transactional(readOnly = true)
    public Map<Long, LedgerCardDtos.UsageGoalView> progressOf(List<LedgerAsset> cards) {
        Map<Long, LedgerCardDtos.UsageGoalView> byCard = new HashMap<>();
        for (LedgerAsset card : cards) {
            LedgerCardDtos.UsageGoalView view = progressOf(card);
            if (view != null) {
                byCard.put(card.getId(), view);
            }
        }
        return byCard;
    }

    /** 화면이 「며칠 남았나」를 계산하지 않도록 오늘을 여기서 준다. */
    public LocalDate today() {
        return clock.today();
    }
}
