package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.Pattern;

/**
 * 열 숫자 서식 설정/해제 요청. {@code KRW|USD|JPY|THOUSANDS|DECIMAL1|DECIMAL2} 또는 {@code null}(해제).
 *
 * <p>표시 전용이라 값·수식은 건드리지 않는다 — 화면에만 이 서식으로 포맷한다(FE). 요약(summary)과
 * 같은 규칙으로 null을 해제로 쓴다(멱등 교체).
 */
public record SetColumnFormatRequest(
        @Pattern(regexp = DatasetColumn.ALLOWED_FORMAT, message = "허용되지 않은 숫자 서식입니다.")
        String format
) {
}
