package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * 채우기 핸들(세로 드래그) 요청. 소스 블록을 대상 행들에 타일링해 채운다.
 *
 * <p>{@code cols}는 채울 열들(소스·대상이 공유). {@code srcR0..srcR1}은 소스 행 범위(선택 블록),
 * {@code dstR0..dstR1}은 대상 행 범위다(모두 rowIndex, 포함). 대상은 소스와 겹치지 않고 바로
 * 위(dstR1 = srcR0-1) 또는 바로 아래(dstR0 = srcR1+1)로 인접해야 한다 — 세부 검증은 서비스에서.
 */
public record FillCellsRequest(
        @NotEmpty List<String> cols,
        @PositiveOrZero int srcR0,
        @PositiveOrZero int srcR1,
        @PositiveOrZero int dstR0,
        @PositiveOrZero int dstR1
) {
}
