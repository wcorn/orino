package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.Pattern;

/**
 * 셀 서식 지정 요청. 필드가 null이면 "그 서식 없음"으로 저장한다(부분 갱신이 아니라 전체 교체).
 *
 * <p>둘 다 null이면 서식이 없어진 것이므로 서버가 그 셀의 서식 행을 지운다 — 서식 초기화는
 * 별도 API가 아니라 빈 요청으로 표현된다.
 */
public record SetCellStyleRequest(
        @Pattern(regexp = "red|orange|yellow|green|blue|purple", message = "허용되지 않은 배경색입니다.")
        String bg,
        @Pattern(regexp = "left|center|right", message = "허용되지 않은 정렬입니다.")
        String align
) {
}
