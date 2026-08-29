package ds.project.orino.planner.ledger.stats;

import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerInstallment;
import ds.project.orino.domain.planner.ledger.entity.LedgerInstallmentRound;
import ds.project.orino.domain.planner.ledger.entity.LedgerPerspective;
import ds.project.orino.domain.planner.ledger.entity.LedgerStatement;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransaction;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionSource;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;
import ds.project.orino.domain.planner.ledger.repository.LedgerInstallmentRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerInstallmentRoundRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerStatementRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.ledger.common.LedgerCategorySpending;
import ds.project.orino.planner.ledger.common.LedgerPeriods;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 같은 원장을 <b>두 관점</b>으로 읽는다(확정 명세 §10.1).
 *
 * <table>
 *   <tr><th></th><th>소비 기준</th><th>청구 기준</th></tr>
 *   <tr><td>언제로 세나</td><td>쓴 날</td><td>카드사가 청구하는 날</td></tr>
 *   <tr><td>할부</td><td>산 달에 <b>전액</b></td><td>달마다 <b>회차 금액</b></td></tr>
 * </table>
 *
 * <p><b>기본은 소비 기준이다</b> — 쓴 날 기준이 직관적이고 예산도 그렇게 맞물린다. 청구 기준은
 * 「카드값이 왜 이렇게 나왔나」를 볼 때 필요하고, 할부가 있으면 두 값이 크게 벌어진다.
 *
 * <p>그 벌어짐을 <b>화면이 계산하게 두지 않는다</b>. 두 곳에서 세면 어느 쪽이 맞는지 알 수 없고,
 * 그건 이 모듈에서 「원장이 틀어졌다」와 구분되지 않는다(D-13).
 *
 * <p><b>청구서·예정·예상 잔액 API는 이 관점을 받지 않는다.</b> 그쪽은 언제나 청구 기준이다 —
 * 「9월 14일에 얼마 빠지나」에 소비 관점이 낄 자리가 없다.
 */
@Component
public class LedgerPerspectiveSpending {

    /**
     * 청구 기준으로 셀 때 거슬러 볼 기간.
     *
     * <p>이번 달에 청구되는 카드 사용은 지난 사이클에 긁은 것이라 <b>구간 밖에 있다.</b>
     * 정산일이 늦은 카드까지 담으려면 두 달로는 모자란다.
     */
    private static final int BILLING_LOOKBACK_MONTHS = 4;

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerStatementRepository statementRepository;
    private final LedgerInstallmentRepository installmentRepository;
    private final LedgerInstallmentRoundRepository roundRepository;

    public LedgerPerspectiveSpending(LedgerTransactionRepository transactionRepository,
                                     LedgerStatementRepository statementRepository,
                                     LedgerInstallmentRepository installmentRepository,
                                     LedgerInstallmentRoundRepository roundRepository) {
        this.transactionRepository = transactionRepository;
        this.statementRepository = statementRepository;
        this.installmentRepository = installmentRepository;
        this.roundRepository = roundRepository;
    }

    /** 그 구간에 「쓴 돈」을 관점에 맞춰 카테고리별로 센다. */
    public List<LedgerCategorySpending.Bucket> byCategory(Long memberId,
                                                          LedgerPeriods.Period period,
                                                          LedgerPerspective perspective) {
        if (perspective == LedgerPerspective.SPEND) {
            // 소비 기준은 곧 「적힌 날」이라 질의 하나로 끝난다.
            return LedgerCategorySpending.netExpense(
                    transactionRepository.sumByCategoryAndFlow(
                            memberId, LedgerTransactionStatus.CONFIRMED,
                            period.start(), period.end()));
        }
        return billingBuckets(memberId, period, Grouping.CATEGORY);
    }

    /**
     * 같은 구간을 <b>자산별</b>로 센다.
     *
     * <p>카테고리와 같은 관점·같은 합계를 쓴다. 한쪽만 소비 기준으로 두면 청구 기준 화면에
     * 합계에 없는 돈이 섞이고, 비율의 분모가 제 것이 아니게 된다.
     *
     * <p>할부 회차는 <b>원 거래의 카드</b>로 센다 — 「어느 카드가 이 청구를 만들었나」가 질문이다.
     */
    public List<LedgerCategorySpending.Bucket> byAsset(Long memberId,
                                                       LedgerPeriods.Period period,
                                                       LedgerPerspective perspective) {
        if (perspective == LedgerPerspective.SPEND) {
            List<LedgerCategorySpending.Bucket> buckets = new ArrayList<>();
            for (LedgerTransactionRepository.AssetTotal row : transactionRepository
                    .sumExpenseByAsset(memberId, LedgerTransactionStatus.CONFIRMED,
                            period.start(), period.end())) {
                buckets.add(new LedgerCategorySpending.Bucket(
                        row.getAssetId(), row.getTotal(), 0));
            }
            return buckets;
        }
        return billingBuckets(memberId, period, Grouping.ASSET);
    }

    /** 무엇으로 묶는가. 청구 기준 집계는 묶는 열만 다르고 나머지 규칙은 똑같다. */
    private enum Grouping {
        CATEGORY,
        ASSET
    }

    /**
     * 청구 기준 집계.
     *
     * <p>세 갈래를 합친다.
     * <ol>
     *   <li><b>카드가 아닌 지출</b> — 청구라는 개념이 없다. 쓴 날이 곧 나간 날이다</li>
     *   <li><b>카드 사용</b> — 그 건이 편입된 청구서의 <b>결제일</b>로 옮겨 센다.
     *       할부 원 거래는 뺀다 — 회차로 따로 청구되기 때문이다</li>
     *   <li><b>할부 회차</b> — 청구서에 붙은 회차를 원 거래의 카테고리로 센다</li>
     * </ol>
     */
    private List<LedgerCategorySpending.Bucket> billingBuckets(Long memberId,
                                                                LedgerPeriods.Period period,
                                                                Grouping grouping) {
        LocalDate from = period.start().minusMonths(BILLING_LOOKBACK_MONTHS);
        List<LedgerTransaction> rows = transactionRepository
                .findAllByMemberIdAndDeletedAtIsNullAndOccurredOnBetweenOrderByOccurredOnDescIdDesc(
                        memberId, from, period.end());

        Map<Long, LocalDate> paymentDates = paymentDatesOf(rows);
        Map<Long, long[]> byCategory = new LinkedHashMap<>();

        for (LedgerTransaction row : rows) {
            if (row.getStatus() != LedgerTransactionStatus.CONFIRMED) {
                continue;
            }
            // 할부 원 거래는 청구 기준에서 회차로 대체된다 — 여기서 세면 두 번 잡힌다.
            if (row.getInstallmentId() != null) {
                continue;
            }
            LocalDate billedOn = row.getStatementId() == null
                    ? row.getOccurredOn()
                    : paymentDates.get(row.getStatementId());
            if (billedOn == null || billedOn.isBefore(period.start())
                    || billedOn.isAfter(period.end())) {
                continue;
            }
            add(byCategory, keyOf(row, grouping), signedAmount(row));
        }

        addInstallmentRounds(memberId, period, byCategory, grouping);
        return toBuckets(byCategory);
    }

    /**
     * 그 구간에 청구되는 할부 회차. <b>아직 어느 청구서에도 안 붙은 회차는 세지 않는다</b> —
     * 청구되지 않은 돈이라 청구 기준에 들어갈 자리가 없다.
     */
    private void addInstallmentRounds(Long memberId, LedgerPeriods.Period period,
                                      Map<Long, long[]> byCategory, Grouping grouping) {
        List<LedgerInstallment> installments = installmentRepository
                .findAllByMemberIdAndStatus(memberId, LedgerInstallment.Status.ACTIVE);
        if (installments.isEmpty()) {
            return;
        }
        Map<Long, Long> categoryByInstallment = new HashMap<>();
        for (LedgerInstallment installment : installments) {
            transactionRepository.findById(installment.getTransactionId())
                    .ifPresent(tx -> categoryByInstallment.put(
                            installment.getId(), keyOf(tx, grouping)));
        }

        List<LedgerStatement> statements = statementRepository.findAllByMemberIdAndPaymentDateBetween(
                memberId, period.start(), period.end());
        if (statements.isEmpty()) {
            return;
        }
        List<Long> statementIds = statements.stream().map(LedgerStatement::getId).toList();
        for (LedgerInstallmentRound round : roundRepository.findAllByStatementIdIn(statementIds)) {
            if (!categoryByInstallment.containsKey(round.getInstallmentId())) {
                continue;
            }
            add(byCategory, categoryByInstallment.get(round.getInstallmentId()), round.getAmount());
        }
    }

    /** 청구서 id → 결제일. 한 번에 읽어 둔다 — 거래마다 물으면 질의가 거래 수만큼 늘어난다. */
    private Map<Long, LocalDate> paymentDatesOf(List<LedgerTransaction> rows) {
        List<Long> ids = rows.stream()
                .map(LedgerTransaction::getStatementId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, LocalDate> dates = new HashMap<>();
        if (ids.isEmpty()) {
            return dates;
        }
        statementRepository.findAllById(ids)
                .forEach(statement -> dates.put(statement.getId(), statement.getPaymentDate()));
        return dates;
    }

    /**
     * 그 줄이 지출 합계를 얼마나 움직이는가.
     *
     * <p>환불은 <b>그 카테고리의 지출을 깎는다</b> — 「수입이 늘었다」가 아니다(§4.3).
     * 이체는 0이다. 소비 기준 질의와 같은 규칙이라 두 관점이 같은 뜻의 숫자를 낸다.
     */
    /** 미분류(카테고리 없음)는 {@code null}이지만, 자산은 언제나 있다(모든 거래가 자산에 붙는다). */
    private Long keyOf(LedgerTransaction row, Grouping grouping) {
        return grouping == Grouping.ASSET ? row.getAssetId() : row.getCategoryId();
    }

    private long signedAmount(LedgerTransaction row) {
        boolean refund = row.getSource() == LedgerTransactionSource.REFUND;
        if (refund) {
            return row.getType() == LedgerFlow.INCOME ? -row.getAmount() : 0;
        }
        return row.getType() == LedgerFlow.EXPENSE ? row.getAmount() : 0;
    }

    private void add(Map<Long, long[]> byCategory, Long categoryId, long amount) {
        if (amount == 0) {
            return;
        }
        long[] cell = byCategory.computeIfAbsent(categoryId, key -> new long[]{0, 0});
        cell[0] += amount;
        cell[1] += amount > 0 ? 1 : 0;
    }

    /** 많이 쓴 순. 상쇄로 0 이하가 된 칸은 뺀다 — 「−3,000원 썼다」는 읽을 수 없다. */
    private List<LedgerCategorySpending.Bucket> toBuckets(Map<Long, long[]> byCategory) {
        List<LedgerCategorySpending.Bucket> buckets = new ArrayList<>();
        for (Map.Entry<Long, long[]> entry : byCategory.entrySet()) {
            if (entry.getValue()[0] <= 0) {
                continue;
            }
            buckets.add(new LedgerCategorySpending.Bucket(
                    entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
        }
        buckets.sort(Comparator.comparingLong(LedgerCategorySpending.Bucket::amount).reversed());
        return buckets;
    }
}
