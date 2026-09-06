package ds.project.orino.planner.travel.prep.dto;

import java.util.List;

/**
 * 분류 안의 묶음 하나(#1358). 「캐리어」·「기내백」처럼 사용자가 적은 이름으로 묶인다.
 *
 * <p><b>{@code label}이 {@code null}인 묶음은 「묶음 없음」이고 항상 맨 앞이다.</b> 이름을
 * 붙이지 않은 항목이 분류의 기본 상태이므로, 이름 붙은 묶음 사이에 끼어들면 아무것도 안 한
 * 사람의 목록이 이유 없이 가운데로 밀린다.
 *
 * <p>이 묶음이 <b>비어 있으면 아예 내려가지 않는다.</b> 분류 카드와 다른 점이다 — 분류는
 * 넷으로 고정이라 빈 카드도 자리를 갖지만, 묶음은 항목이 만든 것이라 마지막 항목이 나가면
 * 그대로 사라진다.
 */
public record PrepSection(
        String label,
        int total,
        int done,
        List<PrepItemView> items
) {
}
