package ds.project.orino.planner.ledger.common;

import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionSource;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository.CategoryFlowTotal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 카테고리별 <b>순 지출</b>. 자산 상세의 분포와 통계 화면이 같은 규칙을 쓴다.
 *
 * <p>규칙은 둘이다.
 * <ul>
 *   <li><b>이체는 들어가지 않는다.</b> 카드 대금 납부가 지출로 새는 구멍을 여기서도 막는다</li>
 *   <li><b>환불은 그 카테고리의 지출을 깎는다.</b> 상쇄 거래는 수입 방향으로 적히지만 원 거래의
 *       카테고리를 물려받는다 — 「수입이 늘었다」가 아니라 「지출이 줄었다」다(확정 명세 §4.3)</li>
 * </ul>
 *
 * <p>두 화면이 각자 세면 같은 달의 「식비」가 두 값이 된다. 그건 이 모듈에서 가장 하지 말아야
 * 할 일이다.
 */
public final class LedgerCategorySpending {

    private LedgerCategorySpending() {
    }

    /** 카테고리 한 칸. {@code categoryId}가 {@code null}이면 미분류다. */
    public record Bucket(Long categoryId, long amount, long count) {
    }

    /** 많이 쓴 순. 상쇄로 0 이하가 된 칸은 빼 준다 — 「−3,000원 썼다」는 읽을 수 없다. */
    public static List<Bucket> netExpense(List<CategoryFlowTotal> rows) {
        Map<Long, long[]> byCategory = new LinkedHashMap<>();
        for (CategoryFlowTotal row : rows) {
            boolean refund = row.getSource() == LedgerTransactionSource.REFUND;
            LedgerFlow bucket = refund ? opposite(row.getType()) : row.getType();
            if (bucket != LedgerFlow.EXPENSE) {
                continue;
            }
            long[] cell = byCategory.computeIfAbsent(
                    row.getCategoryId(), key -> new long[]{0, 0});
            cell[0] += refund ? -row.getTotal() : row.getTotal();
            cell[1] += row.getCount();
        }

        List<Bucket> buckets = new ArrayList<>();
        for (Map.Entry<Long, long[]> entry : byCategory.entrySet()) {
            if (entry.getValue()[0] <= 0) {
                continue;
            }
            buckets.add(new Bucket(entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
        }
        buckets.sort(Comparator.comparingLong(Bucket::amount).reversed());
        return buckets;
    }

    /** 순 지출 합계. */
    public static long total(List<Bucket> buckets) {
        return buckets.stream().mapToLong(Bucket::amount).sum();
    }

    private static LedgerFlow opposite(LedgerFlow type) {
        return switch (type) {
            case EXPENSE -> LedgerFlow.INCOME;
            case INCOME -> LedgerFlow.EXPENSE;
            case TRANSFER -> LedgerFlow.TRANSFER;
        };
    }
}
