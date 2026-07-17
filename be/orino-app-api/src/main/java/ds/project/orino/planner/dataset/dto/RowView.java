package ds.project.orino.planner.dataset.dto;

import java.util.List;
import java.util.Map;

/**
 * 행 조회 결과.
 *
 * <p>{@code id}는 행의 안정적 식별자다. {@code rowIndex}는 정렬·페이지네이션용 순번이라
 * 삽입·삭제 때마다 밀리지만 {@code id}는 바뀌지 않는다. 수식이 다른 행을 참조할 때
 * 묶어야 할 대상은 {@code rowIndex}가 아니라 {@code id}다.
 *
 * <p>{@code cells}엔 <b>계산된 값</b>이, {@code formulas}엔 수식이 있는 셀의 <b>원본</b>이
 * 열 key로 담긴다(수식 없는 셀은 아예 없다). 클라이언트는 값을 보여주되 행을 수정할 땐
 * 수식 셀에 이 원본을 그대로 돌려줘야 한다 — 계산된 값을 돌려주면 서버가 리터럴로 보고
 * 수식을 지운다.
 *
 * <p>{@code formulas}는 <b>표시형</b>(열 이름·행 번호)이다. 저장형(key·행 id)은 서버 안에만 있다.
 */
public record RowView(
        Long id,
        int rowIndex,
        List<String> cells,
        Map<String, String> formulas
) {
}
