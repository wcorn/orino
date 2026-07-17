package ds.project.orino.planner.dataset.dto;

import java.util.List;

/**
 * 행 조회 결과.
 *
 * <p>{@code id}는 행의 안정적 식별자다. {@code rowIndex}는 정렬·페이지네이션용 순번이라
 * 삽입·삭제 때마다 밀리지만 {@code id}는 바뀌지 않는다. 수식이 다른 행을 참조할 때
 * 묶어야 할 대상은 {@code rowIndex}가 아니라 {@code id}다.
 */
public record RowView(Long id, int rowIndex, List<String> cells) {
}
