package ds.project.orino.planner.dataset.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * 여러 셀에 서식을 한 번에 지정한다(선택 범위·행·열·표 전체 적용용). 셀마다 서식을 통째로
 * 교체하며(부분 갱신 아님), 한 셀의 bg·align이 모두 null이면 그 셀 서식을 지운다 —
 * 단건 {@link SetCellStyleRequest}와 같은 의미를 목록으로 확장한 것.
 *
 * <p>같은 배경색을 칠해도 셀마다 정렬이 다를 수 있어(그 반대도) 하나의 공통 style이 아니라
 * 셀별 전체 서식을 담는다. 클라이언트가 각 셀의 보존할 속성을 채워 보낸다.
 */
public record BulkCellStyleRequest(
        @NotEmpty(message = "대상 셀이 비어 있습니다.")
        @Valid
        List<Target> cells
) {
    public record Target(
            @NotNull(message = "행 인덱스가 필요합니다.")
            Integer rowIndex,
            @NotNull(message = "열 key가 필요합니다.")
            String colKey,
            @Pattern(regexp = "red|orange|yellow|green|blue|purple", message = "허용되지 않은 배경색입니다.")
            String bg,
            @Pattern(regexp = "left|center|right", message = "허용되지 않은 정렬입니다.")
            String align
    ) {
    }
}
