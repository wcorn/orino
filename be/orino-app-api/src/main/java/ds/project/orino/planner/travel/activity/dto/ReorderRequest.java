package ds.project.orino.planner.travel.activity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * 드래그 결과를 한 번에 반영한다. 순서 변경과 날짜 이동이 같은 요청이다.
 *
 * <p>영향받은 날짜의 <b>전체 순서</b>를 보낸다. 부분 갱신을 허용하면 보내지 않은 일정의
 * {@code sortOrder}가 보낸 것과 충돌해 순서가 비결정적이 된다.
 *
 * @param moves 날짜별 배열. {@code date}가 {@code null}이면 미배정 보관함이다
 */
public record ReorderRequest(
        @NotNull(message = "이동 정보를 보내주세요.")
        @Valid
        List<Move> moves
) {

    /**
     * @param date        옮겨갈 날짜. {@code null}이면 보관함
     * @param activityIds 그 날짜에 놓일 일정 id를 화면 순서 그대로. 이 순서가 곧 0..n-1이 된다
     */
    public record Move(
            LocalDate date,

            @NotNull(message = "일정 목록을 보내주세요.")
            List<Long> activityIds
    ) {
    }
}
