package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.Pattern;

/**
 * 열 푸터 요약 함수 설정/해제 요청. {@code SUM|AVERAGE|COUNT|MIN|MAX} 또는 {@code null}(해제).
 *
 * <p>정렬(align)과 달리 {@code null}을 <b>해제</b>로 쓴다 — 요약은 "안 보냄"과 "해제"를 구분할
 * 필요가 없고, 열당 1개를 멱등하게 교체하는 것이라 한 PATCH로 설정·해제를 함께 다룬다.
 */
public record SetColumnSummaryRequest(
        @Pattern(regexp = DatasetColumn.ALLOWED_SUMMARY, message = "허용되지 않은 요약 함수입니다.")
        String summary
) {
}
