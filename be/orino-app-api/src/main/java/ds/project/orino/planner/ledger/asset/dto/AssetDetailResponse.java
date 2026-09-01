package ds.project.orino.planner.ledger.asset.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 자산 상세 — 잔액 · 추이 · 카테고리 분포.
 *
 * @param deletable    지울 수 있는가 — <b>화면이 버튼을 열기 전에</b> 알아야 하는 값이라
 *                     상세에 실어 보낸다. 청구서는 세지 않는다(사이클 자리표라 함께 지워진다)
 * @param deleteBlockers 지울 수 없다면 <b>무엇 때문인지</b>. 「안 됩니다」만으로는 무엇을
 *                     치워야 하는지 알 수 없고, 이미 해지한 자산에게 해지를 권하게 된다
 * @param range  추이의 눈금. {@code day}는 최근 30일, {@code month}는 최근 12개월,
 *               {@code year}는 최근 5년이다
 * @param trend  구간 끝 시점의 잔액. <b>원장을 처음부터 따라가며 만든 값</b>이라
 *               어느 시점을 찍어도 그때의 원장과 일치한다
 */
public record AssetDetailResponse(
        boolean deletable,
        List<DeleteBlocker> deleteBlockers,
        AssetView asset,
        Range range,
        List<TrendPoint> trend,
        List<CategoryShare> categoryShare
) {

    /** 삭제를 막는 것. 화면은 이 이름으로 「무엇을 치워야 하는지」를 적는다. */
    public enum DeleteBlocker {
        /** 살아 있는 거래가 붙어 있다. */
        TRANSACTION,
        /** 지운 거래만 남아 있다 — 되돌릴 수 있게 행을 남겨 두기 때문이다. */
        DELETED_TRANSACTION,
        RECURRING,
        TEMPLATE,
        /** 다른 자산이 이 자산을 연결 계좌·결제 계좌로 물고 있다. */
        LINKED_ASSET
    }

    public enum Range {
        DAY,
        MONTH,
        YEAR
    }

    /**
     * 추이 한 점.
     *
     * @param balance 그 구간이 끝난 시점의 잔액(신용카드면 미결제 사용액)
     */
    public record TrendPoint(
            LocalDate date,
            long balance,
            long income,
            long expense
    ) {
    }

    /**
     * 카테고리 분포 한 칸.
     *
     * @param categoryId {@code null}이면 <b>미분류</b>다. 빼지 않는다 —
     *                   안 보이면 정리하지 않는다
     */
    public record CategoryShare(
            Long categoryId,
            String categoryName,
            long amount,
            long count
    ) {
    }
}
