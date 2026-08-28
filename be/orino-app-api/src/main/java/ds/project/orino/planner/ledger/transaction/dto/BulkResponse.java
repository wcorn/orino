package ds.project.orino.planner.ledger.transaction.dto;

/**
 * 일괄 처리 결과.
 *
 * @param affected 실제로 바뀐 건수. 남의 거래나 이미 삭제된 거래는 조용히 빠지므로
 *                 요청한 {@code ids}의 크기와 다를 수 있다
 */
public record BulkResponse(int affected) {
}
