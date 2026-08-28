package ds.project.orino.planner.ledger.transaction.dto;

import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;

/**
 * 내용 자동완성 후보. 같은 가맹점을 다시 적을 때 <b>지난번의 카테고리·자산·금액</b>을 딸려 보낸다 —
 * 30초 입력(확정 명세 §4.2)은 이런 것들이 모여야 성립한다.
 */
public record SuggestionView(
        String title,
        LedgerFlow type,
        Long categoryId,
        String categoryName,
        Long assetId,
        String assetName,
        long amount
) {
}
