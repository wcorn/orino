package ds.project.orino.planner.ledger.transaction.dto;

/**
 * 내역 복사(`LDG-014`).
 *
 * @param useToday 기본 {@code true}. 대개는 「같은 걸 오늘 또 썼다」라서 오늘로 적는다.
 *                 원본 날짜를 쓰려면 명시적으로 {@code false}를 보낸다
 */
public record DuplicateRequest(Boolean useToday) {
}
