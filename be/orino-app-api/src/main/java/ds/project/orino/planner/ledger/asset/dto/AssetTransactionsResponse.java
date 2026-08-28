package ds.project.orino.planner.ledger.asset.dto;

import ds.project.orino.planner.ledger.transaction.dto.TransactionView;

import java.util.List;

/**
 * 그 자산의 내역. 통장 거래내역처럼 <b>줄마다 그 시점의 잔액</b>이 붙는다 —
 * 그게 있어야 "어디서부터 어긋났나"를 눈으로 따라갈 수 있다.
 */
public record AssetTransactionsResponse(List<Row> items) {

    /**
     * 내역 한 줄.
     *
     * @param runningBalance 이 거래까지 반영한 잔액. 신용카드면 그 시점의 미결제 사용액이다.
     *                       <b>체크카드는 {@code null}</b>이다 — 돈은 연결 계좌에서 빠지므로
     *                       카드 이름 옆에 잔액을 적으면 없는 돈을 있는 것처럼 보이게 한다(D-4).
     *                       예정({@code SCHEDULED}) 줄도 {@code null}이다: 아직 일어나지 않은 일은
     *                       잔액을 바꾸지 않는다
     */
    public record Row(
            TransactionView transaction,
            Long runningBalance
    ) {
    }
}
