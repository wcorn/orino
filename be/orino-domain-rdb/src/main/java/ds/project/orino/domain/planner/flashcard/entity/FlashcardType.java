package ds.project.orino.domain.planner.flashcard.entity;

/**
 * 플래시카드 종류.
 *
 * <ul>
 *     <li>{@link #BASIC} — 앞/뒷면 Q&amp;A 카드. {@code back} 필수, {@code items} 없음.</li>
 *     <li>{@link #ORDERING} — 순서 배열 카드. {@code items}(정답 순서) 필수, {@code back} 없음.</li>
 * </ul>
 */
public enum FlashcardType {
    BASIC,
    ORDERING
}
