package ds.project.orino.planner.ledger.asset.dto;

import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerAssetType;

import java.time.LocalDate;

/**
 * 자산 한 줄.
 *
 * <p>{@code balance}와 {@code unpaidAmount}는 <b>둘 다 채워지지 않는다</b>. 잔액을 갖는
 * 자산이면 앞의 것, 신용카드면 뒤의 것이고, 체크카드는 <b>둘 다 {@code null}</b>이다 —
 * 체크카드에도 잔액을 주면 같은 돈이 두 자산에 잡혀 총자산이 부풀려진다(D-4).
 *
 * @param balance      원장에서 파생한 잔액. 저장된 값이 아니다(D-8)
 * @param unpaidAmount 신용카드 미결제 사용액. 이건 잔액이 아니라 <b>부채</b>다
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
        Long unpaidAmount
) {

    public static AssetView of(LedgerAsset asset, String linkedAssetName,
                               Long balance, Long unpaidAmount) {
        return new AssetView(
                asset.getId(), asset.getGroupId(), asset.getName(), asset.getType(),
                asset.getAccountLast4(), asset.getDisplayOrder(), asset.isHidden(),
                asset.getClosedReason(), asset.getMaturityDate(), asset.getTargetAmount(),
                asset.getLinkedAssetId(), linkedAssetName, balance, unpaidAmount);
    }
}
