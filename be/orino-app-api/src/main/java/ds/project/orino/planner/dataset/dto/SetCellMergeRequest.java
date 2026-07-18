package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 셀 병합 요청. 앵커(경로의 {@code rowIndex}·{@code colKey}) 기준으로 {@code rowSpan × colSpan}
 * 영역을 병합한다. 병합 해제는 별도 API(DELETE)다.
 *
 * <p>슬라이스 1은 가로 병합만이라 {@code rowSpan}은 1이어야 한다(그 외는 서비스가 400).
 * 상한(경계 초과·겹침)은 열 구성을 알아야 하므로 서비스에서 검증한다.
 */
public record SetCellMergeRequest(
        @NotNull(message = "rowSpan은 필수입니다.")
        @Min(value = 1, message = "rowSpan은 1 이상이어야 합니다.")
        @Max(value = MAX_SPAN, message = "rowSpan이 너무 큽니다.")
        Integer rowSpan,
        @NotNull(message = "colSpan은 필수입니다.")
        @Min(value = 1, message = "colSpan은 1 이상이어야 합니다.")
        @Max(value = MAX_SPAN, message = "colSpan이 너무 큽니다.")
        Integer colSpan
) {
    /** span 상한(폭주 방지). 실제 경계는 열/행 수로 서비스가 다시 막는다. */
    public static final int MAX_SPAN = 1000;
}
