package ds.project.orino.planner.travel.activity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import ds.project.orino.domain.planner.travel.entity.TripActivity;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 일정 하나. 보드 목록과 상세가 같은 형태를 쓴다.
 *
 * @param activityDate null이면 미배정 보관함에 있는 일정이다
 * @param startTime    여행 타임존의 벽시계 시각. {@code HH:mm}로 직렬화된다
 * @param sortOrder    같은 날짜(또는 보관함) 안에서의 순서. 정렬의 유일한 기준이다
 * @param hasLog       사후 기록(평점·메모) 존재 여부. 기록 테이블이 4단계라 지금은 항상 false
 */
public record ActivityResponse(
        Long id,
        Long tripId,
        String title,
        LocalDate activityDate,

        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        ActivityPlace place,
        String memo,
        String url,
        boolean notifyEnabled,
        Integer notifyMinutes,
        boolean departureNotifyEnabled,
        int sortOrder,
        boolean hasLog
) {

    public static ActivityResponse of(TripActivity activity, ActivityPlace place) {
        return new ActivityResponse(activity.getId(), activity.getTripId(), activity.getTitle(),
                activity.getActivityDate(), activity.getStartTime(), place,
                activity.getMemo(), activity.getUrl(), activity.isNotifyEnabled(),
                activity.getNotifyMinutes(), activity.isDepartureNotifyEnabled(),
                activity.getSortOrder(), false);
    }
}
