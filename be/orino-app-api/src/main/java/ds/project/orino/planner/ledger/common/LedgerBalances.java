package ds.project.orino.planner.ledger.common;

import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerAssetType;
import ds.project.orino.domain.planner.ledger.repository.LedgerLoanRepository.LoanPrincipal;
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
 * <p>네 가지를 구분해서 담는다.
 * <ul>
 *   <li><b>잔액</b>({@link #balanceOf}) — 현금·입출금·저축·간편결제. 실제로 돈이 들어 있는 곳</li>
 *   <li><b>미결제 사용액</b>({@link #unpaidOf}) — 신용카드. 이건 잔액이 아니라 <b>부채</b>다.
 *       v1.5에서 청구서가 붙으면 여기에 이월·할부 잔여가 더해진다</li>
 *   <li><b>잔여 원금</b>({@link #principalOf}) — 대출. 역시 <b>부채</b>다. 이 묶음에만 기준일
 *       필터가 붙어 따로 센 값을 받는다(아키텍처 §10.3)</li>
 *   <li>체크카드는 <b>어느 것도 아니다</b>. 거래는 체크카드에 붙지만 돈은 연결 계좌에서 빠진다(D-4) —
 *       체크카드에도 잔액을 주면 같은 돈이 두 자산에 잡혀 총자산이 부풀려진다</li>
 * </ul>
 */
public final class LedgerBalances {

    private final Map<Long, Long> balanceByAsset;
    private final Map<Long, Long> unpaidByCard;
    private final Map<Long, Long> principalByLoan;

    private LedgerBalances(Map<Long, Long> balanceByAsset, Map<Long, Long> unpaidByCard,
                           Map<Long, Long> principalByLoan) {
        this.balanceByAsset = balanceByAsset;
        this.unpaidByCard = unpaidByCard;
        this.principalByLoan = principalByLoan;
    }

    /**
     * 잔여 원금 없이 만든다. <b>잔액만 읽는 곳</b>(예정의 「쓸 수 있는 돈」, 음수 경고, 카드 목록)이
     * 쓴다 — 대출은 잔액을 갖지 않아 이 값들에 영향이 없다. 부채·순자산을 말하는 곳은
     * 잔여 원금을 넘기는 쪽을 쓴다.
     */
    public static LedgerBalances of(List<LedgerAsset> assets,
                                    List<AssetFlowTotal> outgoing,
                                    List<AssetTotal> incoming) {
        return of(assets, outgoing, incoming, List.of());
    }

    /**
     * 확정 거래 합계에서 잔액을 만든다.
     *
     * <p>{@code outgoing}은 거래가 붙은 자산 쪽, {@code incoming}은 이체받는 쪽이다. 한 질의로
     * 합치면 이체 한 건이 양쪽에서 같은 부호로 세어져 잔액이 두 배로 튄다.
     *
     * @param loanPrincipals 대출별 잔여 원금 재료({@code LedgerLoanRepository.sumPrincipalUpTo})
     */
    public static LedgerBalances of(List<LedgerAsset> assets,
                                    List<AssetFlowTotal> outgoing,
                                    List<AssetTotal> incoming,
                                    List<LoanPrincipal> loanPrincipals) {
        Map<Long, LedgerAsset> byId = new HashMap<>();
        for (LedgerAsset asset : assets) {
            byId.put(asset.getId(), asset);
        }

        Map<Long, Long> balances = new HashMap<>();
        Map<Long, Long> unpaid = new HashMap<>();
        Map<Long, Long> principals = new HashMap<>();
        // 자산이 하나라도 서 있으면 0으로라도 자리를 잡아 둔다 — 거래가 없는 통장의 잔액은
        // "모른다"가 아니라 0이다.
        for (LedgerAsset asset : assets) {
            if (asset.getType().holdsBalance()) {
                balances.put(asset.getId(), 0L);
            } else if (asset.getType() == LedgerAssetType.CREDIT_CARD) {
                unpaid.put(asset.getId(), 0L);
            } else if (asset.getType() == LedgerAssetType.LOAN) {
                principals.put(asset.getId(), 0L);
            }
        }
        for (LoanPrincipal row : loanPrincipals) {
            if (principals.containsKey(row.getAssetId())) {
                principals.put(row.getAssetId(), row.principal());
            }
        }

        for (AssetFlowTotal row : outgoing) {
            LedgerAsset asset = byId.get(row.getAssetId());
            if (asset == null || asset.getType() == LedgerAssetType.LOAN) {
                // 대출 쪽 합계는 여기서 세지 않는다 — 기준일 이전 이체까지 섞여 잔여 원금이 틀린다.
                // 상대편(출금 계좌) 잔액은 그 계좌의 행에서 이미 움직였다.
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
            if (asset == null || asset.getType() == LedgerAssetType.LOAN) {
                continue;
            }
            if (asset.getType() == LedgerAssetType.CREDIT_CARD) {
                // 카드로 들어온 이체는 대금 납부다 — 빚이 그만큼 준다.
                unpaid.merge(asset.getId(), -row.getTotal(), Long::sum);
                continue;
            }
            balances.merge(asset.balanceBearingAssetId(), row.getTotal(), Long::sum);
        }

        return new LedgerBalances(balances, unpaid, principals);
    }

    /** 잔액을 갖는 자산이 아니면 {@code null}이다 — 0과 「해당 없음」은 다르다. */
    public Long balanceOf(Long assetId) {
        return balanceByAsset.get(assetId);
    }

    /** 신용카드가 아니면 {@code null}. */
    public Long unpaidOf(Long assetId) {
        return unpaidByCard.get(assetId);
    }

    /** 대출이 아니면 {@code null}. */
    public Long principalOf(Long assetId) {
        return principalByLoan.get(assetId);
    }

    /** 총자산 = 현금 + 입출금 + 저축 + 간편결제(확정 명세 §5.3). 대출은 여기 들어오지 않는다. */
    public long totalAssets() {
        return balanceByAsset.values().stream().mapToLong(Long::longValue).sum();
    }

    /** 신용카드 미결제 사용액의 합. 부채 브레이크다운의 한 줄이다. */
    public long cardLiabilities() {
        return unpaidByCard.values().stream().mapToLong(Long::longValue).sum();
    }

    /** 대출 잔여 원금의 합. 부채 브레이크다운의 한 줄이다(덧붙인 명세 §7). */
    public long loanLiabilities() {
        return principalByLoan.values().stream().mapToLong(Long::longValue).sum();
    }

    /** 부채 = 신용카드 미결제 사용액 + 대출 잔여 원금(덧붙인 명세 §7). */
    public long liabilities() {
        return cardLiabilities() + loanLiabilities();
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
