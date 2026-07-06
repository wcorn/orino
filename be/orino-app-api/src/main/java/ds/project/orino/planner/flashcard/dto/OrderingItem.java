package ds.project.orino.planner.flashcard.dto;

/**
 * 순서 카드의 항목. 배열의 순서 자체가 정답 순서이며, 정답을 별도로 저장하지 않는다.
 *
 * @param id   드래그 안정 키(FE 생성). 카드 내에서 유일해야 한다.
 * @param text 항목 표시 텍스트(1~1000자).
 */
public record OrderingItem(
        String id,
        String text
) {
}
