package ds.project.orino.planner.dataset.dto;

/**
 * 병합 하나의 표시형. 앵커를 <b>행 번호(rowIndex)</b>로 가리킨다 — 저장은 행 id로 하지만
 * FE는 위치로 그리므로 조회 시 번호로 변환해 준다(행이 밀리면 FE가 다시 받는다).
 *
 * <p>세로 병합(rowSpan&gt;1)은 페이징과 무관하게 전체 리스트가 있어야 그릴 수 있어, 병합은
 * 행 단위가 아니라 dataset 단위({@link MergesResponse})로 내려간다.
 */
public record MergeView(
        int rowIndex,
        String colKey,
        int rowSpan,
        int colSpan
) {
}
