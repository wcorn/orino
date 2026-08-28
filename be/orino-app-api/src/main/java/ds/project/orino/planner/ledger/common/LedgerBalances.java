package ds.project.orino.planner.ledger.common;

import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerAssetType;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository.AssetFlowTotal;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository.AssetTotal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 원장에서 파생한 잔액. <b>저장된 값이 아니다</b>(D-8).
 *
 * <p>어긋남을 감추는 컬럼보다 어긋남이 드러나는 합계가 낫다 — 저장한 잔액과 원장이 갈라지는
 * 순간이 수동 가계부가 신뢰를 잃는 전형적 경로다.
 *
 * <p>세 가지를 구분해서 담는다.
 * <ul>
 *   <li><b>잔액</b>({@link #balanceOf}) — 현금·입출금·저축·간편결제. 실제로 돈이 들어 있는 곳</li>
 *   <li><b>미결제 사용액</b>({@link #unpaidOf}) — 신용카드. 이건 잔액이 아니라 <b>부채</b>다.
 *       v1.5에서 청구서가 붙으면 여기에 이월·할부 잔여가 더해진다</li>
 *   <li>체크카드는 <b>둘 다 아니다</b>. 거래는 체크카드에 붙지만 돈은 연결 계좌에서 빠진다(D-4) —
 *       체크카드에도 잔액을 주면 같은 돈이 두 자산에 잡혀 총자산이 부풀려진다</li>
 * </ul>
 */
public final class LedgerBalances {

    private final Map<Long, Long> balanceByAsset;
    private final Map<Long, Long> unpaidByCard;

    private LedgerBalances(Map<Long, Long> balanceByAsset, Map<Long, Long> unpaidByCard) {
        this.balanceByAsset = balanceByAsset;
        this.unpaidByCard = unpaidByCard;
    }

    /**
     * 확정 거래 합계에서 잔액을 만든다.
     *
     * <p>{@code outgoing}은 거래가 붙은 자산 쪽, {@code incoming}은 이체받는 쪽이다. 한 질의로
     * 합치면 이체 한 건이 양쪽에서 같은 부호로 세어져 잔액이 두 배로 튄다.
     */
    public static LedgerBalances of(List<LedgerAsset> assets,
                                    List<AssetFlowTotal> outgoing,
                                    List<AssetTotal> incoming) {
        Map<Long, LedgerAsset> byId = new HashMap<>();
        for (LedgerAsset asset : assets) {
            byId.put(asset.getId(), asset);
        }

        Map<Long, Long> balances = new HashMap<>();
        Map<Long, Long> unpaid = new HashMap<>();
        // 자산이 하나라도 서 있으면 0으로라도 자리를 잡아 둔다 — 거래가 없는 통장의 잔액은
        // "모른다"가 아니라 0이다.
        for (LedgerAsset asset : assets) {
            if (asset.getType().holdsBalance()) {
                balances.put(asset.getId(), 0L);
            } else if (asset.getType() == LedgerAssetType.CREDIT_CARD) {
                unpaid.put(asset.getId(), 0L);
            }
        }

        for (AssetFlowTotal row : outgoing) {
            LedgerAsset asset = byId.get(row.getAssetId());
            if (asset == null) {
                continue;
            }
            if (asset.getType() == LedgerAssetType.CREDIT_CARD) {
                // 카드 사용은 빚이 늘고, 그 사용의 환불(INCOME)은 빚이 준다.
                long delta = switch (row.getType()) {
                    case EXPENSE -> row.getTotal();
                    case INCOME -> -row.getTotal();
                    case TRANSFER -> 0L;
                };
                unpaid.merge(asset.getId(), delta, Long::sum);
                continue;
            }
            Long target = asset.balanceBearingAssetId();
            long delta = switch (row.getType()) {
                case INCOME -> row.getTotal();
                // 이체는 나가는 쪽에서 줄고, 들어오는 쪽은 아래 incoming이 더한다.
                case EXPENSE, TRANSFER -> -row.getTotal();
            };
            balances.merge(target, delta, Long::sum);
        }

        for (AssetTotal row : incoming) {
            LedgerAsset asset = byId.get(row.getAssetId());
            if (asset == null) {
                continue;
            }
            if (asset.getType() == LedgerAssetType.CREDIT_CARD) {
                // 카드로 들어온 이체는 대금 납부다 — 빚이 그만큼 준다.
                unpaid.merge(asset.getId(), -row.getTotal(), Long::sum);
                continue;
            }
            balances.merge(asset.balanceBearingAssetId(), row.getTotal(), Long::sum);
        }

        return new LedgerBalances(balances, unpaid);
    }

    /** 잔액을 갖는 자산이 아니면 {@code null}이다 — 0과 「해당 없음」은 다르다. */
    public Long balanceOf(Long assetId) {
        return balanceByAsset.get(assetId);
    }

    /** 신용카드가 아니면 {@code null}. */
    public Long unpaidOf(Long assetId) {
        return unpaidByCard.get(assetId);
    }

    /** 총자산 = 현금 + 입출금 + 저축 + 간편결제(확정 명세 §5.3). */
    public long totalAssets() {
        return balanceByAsset.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * 부채 = 신용카드 미결제 잔액. v1에서는 <b>확정 전 사용분</b>까지다 —
     * 청구서 미납분·이월 잔액·할부 잔여 원금은 v1.5(#1262)에서 더해진다.
     */
    public long liabilities() {
        return unpaidByCard.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * 순자산 = 총자산 − 부채.
     *
     * <p>화면은 이 값을 <b>크게 혼자 보여주지 않는다</b>. "통장에 300만 있는데 카드값이 180만"인
     * 상태가 세 줄로 정직하게 보여야 한다(확정 명세 §5.3).
     */
    public long netWorth() {
        return totalAssets() - liabilities();
    }
}
