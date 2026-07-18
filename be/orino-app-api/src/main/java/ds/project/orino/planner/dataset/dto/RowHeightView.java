package ds.project.orino.planner.dataset.dto;

/**
 * 기본이 아닌 행 높이 하나. 행을 <b>행 번호</b>로 가리킨다(저장은 row_id, 표시는 번호).
 * 세로 병합 오버레이가 앵커 밖 행의 누적 높이를 알아야 해, 높이는 페이지가 아니라 dataset 단위로 온다.
 */
public record RowHeightView(
        int rowIndex,
        int height
) {
}
