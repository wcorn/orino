package ds.project.orino.planner.travel.activity.dto;

import ds.project.orino.domain.planner.travel.entity.TripActivityLog;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 기록 저장 요청(§8). 사진과 분리된 요청이다.
 *
 * <p><b>둘 다 null을 허용한다.</b> 별을 해제하거나 메모를 지우는 것도 정당한 입력이라,
 * "빈 값이면 무시"로 만들면 한 번 매긴 별을 되돌릴 방법이 없어진다.
 */
public record ActivityLogRequest(

        @Min(TripActivityLog.MIN_RATING)
        @Max(TripActivityLog.MAX_RATING)
        Integer rating,

        @Size(max = TripActivityLog.MAX_MEMO_LENGTH)
        String memo
) {
}
