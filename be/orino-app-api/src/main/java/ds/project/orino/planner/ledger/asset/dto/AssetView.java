package ds.project.orino.planner.ledger.asset.dto;

import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerAssetType;
import ds.project.orino.domain.planner.ledger.entity.LedgerSavingsKind;

import java.time.LocalDate;

/**
 * 자산 한 줄.
 *
 * <p>{@code balance}와 {@code unpaidAmount}는 <b>둘 다 채워지지 않는다</b>. 잔액을 갖는
 * 자산이면 앞의 것, 신용카드면 뒤의 것이고, 체크카드는 <b>둘 다 {@code null}</b>이다 —
 * 체크카드에도 잔액을 주면 같은 돈이 두 자산에 잡혀 총자산이 부풀려진다(D-4).
 *
 * <p>대출은 {@code balance}도 {@code unpaidAmount}도 {@code null}이고 {@code principalRemaining}만
 * 채워진다 — 잔여 원금은 잔액이 아니라 부채이고, 셈법(기준값 + 기준일 이후 이체)도 다르다.
 *
 * @param balance            원장에서 파생한 잔액. 저장된 값이 아니다(D-8)
 * @param unpaidAmount       신용카드 미결제 사용액. 이건 잔액이 아니라 <b>부채</b>다
 * @param principalRemaining 대출 잔여 원금. 역시 <b>부채</b>다. 대출이 아니면 {@code null}
 * @param savingsKind       예·적금의 종류. {@code null}이면 일반 예·적금(또는 예·적금이 아님)
 * @param subscriptionCount 청약 인정 회차 <b>추정</b>. 청약이 아니거나 청약홈 기준값이 없으면
 *                          {@code null}이다 — 0회와 「모른다」는 다르다
 */
public record AssetView(
        Long id,
        Long groupId,
        String name,
        LedgerAssetType type,
        String accountLast4,
        int displayOrder,
        boolean hidden,
        String closedReason,
        LocalDate maturityDate,
        Long targetAmount,
        Long linkedAssetId,
        String linkedAssetName,
        Long balance,
        Long unpaidAmount,
        Long principalRemaining,
        LedgerSavingsKind savingsKind,
        Integer subscriptionCount
) {

    public static AssetView of(LedgerAsset asset, String linkedAssetName,
                               Long balance, Long unpaidAmount, Long principalRemaining,
                               Integer subscriptionCount) {
        return new AssetView(
                asset.getId(), asset.getGroupId(), asset.getName(), asset.getType(),
                asset.getAccountLast4(), asset.getDisplayOrder(), asset.isHidden(),
                asset.getClosedReason(), asset.getMaturityDate(), asset.getTargetAmount(),
                asset.getLinkedAssetId(), linkedAssetName, balance, unpaidAmount,
                principalRemaining, asset.getSavingsKind(), subscriptionCount);
    }
}
