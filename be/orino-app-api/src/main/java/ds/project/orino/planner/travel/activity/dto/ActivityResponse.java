package ds.project.orino.planner.travel.activity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import ds.project.orino.domain.planner.travel.entity.TripActivity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * 일정 하나. 보드 목록과 상세가 같은 형태를 쓴다.
 *
 * @param activityDate null이면 미배정 보관함에 있는 일정이다
 * @param startTime    여행 타임존의 벽시계 시각. {@code HH:mm}로 직렬화된다
 * @param sortOrder    같은 날짜(또는 보관함) 안에서의 순서. 정렬의 유일한 기준이다
 * @param log          사후 기록(평점·메모). 아직 기록이 없으면 null
 * @param hasLog       기록 존재 여부. 목록은 {@code log}를 다 펼치지 않고 이 표시만 쓴다
 * @param outOfBaseCity      (v2.1) 그날 <b>있어도 되는 어느 도시에도</b> 속하지 않는 장소다 →
 *                           화면이 경고색으로 도시명을 덧붙인다. 판정은
 *                           {@code place.cityPlaceRef}로만 한다. 도시가 바뀌는 날은 떠나온
 *                           도시도 함께 통과시킨다(D-25) — 이동일 오전 일정은 잘못 담은 것이
 *                           아니다
 * @param canDepartureNotify (v2.1) 출발 알림을 켤 수 있는가. <b>직전에 장소 있는 일정이 있고</b>
 *                           그 사이가 <b>도시를 넘지 않아야</b> 한다 — 도시를 넘는 이동은 계산
 *                           대상이 아니라 언제 나서야 하는지 정할 수 없다(§3.4)
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
        ActivityLogResponse log,
        boolean hasLog,
        boolean outOfBaseCity,
        boolean canDepartureNotify
) {

    public static ActivityResponse of(TripActivity activity, ActivityPlace place,
                                      ActivityLogResponse log) {
        return new ActivityResponse(activity.getId(), activity.getTripId(), activity.getTitle(),
                activity.getActivityDate(), activity.getStartTime(), place,
                activity.getMemo(), activity.getUrl(), activity.isNotifyEnabled(),
                activity.getNotifyMinutes(), activity.isDepartureNotifyEnabled(),
                activity.getSortOrder(), log, log != null, false, false);
    }

    /** 기록이 아직 없는(또는 필요 없는) 자리에서 쓴다. */
    public static ActivityResponse of(TripActivity activity, ActivityPlace place) {
        return of(activity, place, null);
    }

    /**
     * 그날 있어도 되는 도시들과 견줘 도시 이탈 여부를 붙인다. 보드만 아는 값이라(상세 화면에는
     * "그날"이 없다) 조립을 마친 뒤 덧씌운다.
     */
    public ActivityResponse withBaseCities(Set<String> baseCityPlaceRefs) {
        return new ActivityResponse(id, tripId, title, activityDate, startTime, place,
                memo, url, notifyEnabled, notifyMinutes, departureNotifyEnabled,
                sortOrder, log, hasLog, isOutOf(baseCityPlaceRefs), canDepartureNotify);
    }

    /**
     * 출발 알림을 켤 수 있는지를 덧씌운다. 판정에는 <b>같은 날 앞뒤 일정</b>이 필요해서(§3.4)
     * 일정 하나만 봐서는 알 수 없다 — 목록을 아는 쪽이 계산해 넘긴다.
     */
    public ActivityResponse withCanDepartureNotify(boolean can) {
        return new ActivityResponse(id, tripId, title, activityDate, startTime, place,
                memo, url, notifyEnabled, notifyMinutes, departureNotifyEnabled,
                sortOrder, log, hasLog, outOfBaseCity, can);
    }

    /**
     * <b>양쪽 식별자를 다 알 때만</b> 다른 도시로 본다. 장소에 도시 식별자가 없거나 그날의
     * 도시를 하나도 모르면(집합이 비면) 판정하지 않는다 — 모르는 것을 "다르다"로 답하면
     * 멀쩡한 일정에 경고가 붙는다(D-23).
     */
    private boolean isOutOf(Set<String> baseCityPlaceRefs) {
        String ref = place == null ? null : place.cityPlaceRef();
        return ref != null && !baseCityPlaceRefs.isEmpty() && !baseCityPlaceRefs.contains(ref);
    }
}
