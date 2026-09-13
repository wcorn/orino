package ds.project.orino.planner.ledger.asset.dto;

import ds.project.orino.domain.planner.ledger.entity.LedgerAssetGroupKind;

import java.util.List;

/**
 * 자산 목록.
 *
 * <p>총자산·부채·순자산을 <b>세 줄로</b> 내려보낸다. 순자산만 크게 보여주면 "통장에 300만
 * 있는데 카드값이 180만"인 상태가 가려진다(확정 명세 §5.3).
 *
 * @param hidden              숨긴 자산. 목록 본문에서는 빠지되 사라지지는 않는다 — 해지한 카드의
 *                            지난 내역은 그대로 살아 있어야 한다
 * @param liabilities         부채 합계. <b>숫자 그대로 둔다</b> — 화면·설치된 옛 번들이 이 값을
 *                            숫자로 읽는다
 * @param liabilityBreakdown  부채가 무엇으로 이뤄졌는지(덧붙인 명세 §7). 「96,870,400」만 있으면
 *                            카드값인지 대출인지 알 수 없다
 */
public record AssetListResponse(
        List<GroupView> groups,
        List<AssetView> hidden,
        long totalAssets,
        long liabilities,
        long netWorth,
        LiabilityBreakdown liabilityBreakdown
) {

    /**
     * 부채 브레이크다운.
     *
     * @param card 신용카드 미결제 사용액의 합
     * @param loan 대출 잔여 원금의 합
     */
    public record LiabilityBreakdown(long card, long loan) {
    }

    /**
     * 그룹 한 묶음.
     *
     * @param id       {@code null}이면 그룹 없는 자산들의 「그 외」 묶음이다.
     *                 그룹은 표시 수단이지 소속 조건이 아니라, 없는 상태가 정상이다
     * @param subtotal 그 그룹의 잔액 합계. 카드 그룹이면 미결제 사용액의 합이다
     */
    public record GroupView(
            Long id,
            String name,
            LedgerAssetGroupKind kind,
            int displayOrder,
            boolean collapsed,
            long subtotal,
            List<AssetView> assets
    ) {
    }
}
