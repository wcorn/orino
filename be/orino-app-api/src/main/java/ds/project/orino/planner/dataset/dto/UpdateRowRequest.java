package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * 행 수정 요청.
 *
 * <p>{@code tableRefs}는 표간 참조({@code ={요약!환율}1})의 <b>표 이름 → 대상 표 id</b> 맵이다.
 * 표 이름은 노트 안에서만 유일하고 BE는 노트 스코프를 모르므로, 이 수식이 참조하는 표들을 FE가
 * 노트 기준으로 해석해 넘긴다. 표간 참조가 없으면 null이어도 된다. 대상 표가 같은 회원 것인지는
 * BE가 검증한다(남의 표 참조 차단).
 */
public record UpdateRowRequest(
        @NotNull(message = "cells는 필수입니다.")
        List<String> cells,
        Map<String, Long> tableRefs
) {
}
