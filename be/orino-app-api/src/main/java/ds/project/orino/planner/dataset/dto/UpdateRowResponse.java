package ds.project.orino.planner.dataset.dto;

import java.util.List;

/**
 * 행 수정 결과.
 *
 * <p>{@code edited}는 방금 수정한 그 행이다. {@code affected}는 그 수정이 <b>다른 행</b>으로
 * 번져(집계 {@code SUM}·{@code SUMIF} 등, 다른 행을 가리키는 참조) 값이 바뀐 행들이다 —
 * 편집 행은 제외한다. 클라이언트는 {@code edited}와 {@code affected}를 모두 캐시에 반영해,
 * BE가 이미 다시 계산한 교차 행을 페이지 재조회 없이 즉시 보여준다(Epic #892 반응성).
 *
 * <p>{@code affected}는 행 번호 오름차순이며, 로드 범위 밖 행도 포함될 수 있다(클라가 로드한
 * 범위만 골라 반영하면 된다). 번진 곳이 없으면 빈 리스트다.
 */
public record UpdateRowResponse(
        RowView edited,
        List<RowView> affected
) {
}
