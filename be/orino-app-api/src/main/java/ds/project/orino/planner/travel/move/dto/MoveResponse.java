package ds.project.orino.planner.travel.move.dto;

import ds.project.orino.domain.planner.travel.entity.TravelMode;
import ds.project.orino.domain.planner.travel.entity.TravelMove;

/**
 * 이동 한 건(§4.4, #1208). 보드 응답에 실려 일정 리스트에 항상 표시된다.
 *
 * <p><b>아직 아무것도 적지 않은 구간도 행으로 내려간다</b> — {@code mode}가 null이다. 값이 없는
 * 구간을 응답에서 빼면 화면에 "여기에 이동을 적을 수 있다"는 자리가 사라지고, 사용자는 어디를
 * 눌러야 하는지 알 수 없다. <b>빈 행이 곧 입력 지점이다.</b>
 *
 * <p>{@code fallback}·{@code crossCity}·{@code distanceM}은 없다. 계산하지 않으니 실패해 대체할
 * 값도, 계산을 막을 경계도 없다 — 도시를 넘는 이동이야말로 사용자가 미리 정해 두는 구간이다.
 *
 * @param fromActivityId  출발 일정
 * @param toActivityId    도착 일정. 사이에 장소 없는 일정이 끼면 건너뛴 결과다.
 *                        숙소로 가는 이동이면 null이다
 * @param toStayId        도착 숙소. 일정 사이 이동이면 null이다
 * @param mode            이동수단 분류. <b>null이면 아직 적지 않은 구간</b>이다
 * @param name            실제 이동수단 이름({@code 나리타 익스프레스 3호})
 * @param durationMinutes 소요 시간(분). 수단만 정하고 시간은 나중에 확인할 수 있어 null을 허용한다
 * @param url             예매·확인 링크
 * @param memo            좌석·플랫폼·예약번호
 */
public record MoveResponse(
        Long fromActivityId,
        Long toActivityId,
        Long toStayId,
        TravelMode mode,
        String name,
        Integer durationMinutes,
        String url,
        String memo
) {

    /** 일정 사이 이동 — 아직 아무것도 적지 않았다. */
    public static MoveResponse emptyBetween(Long fromActivityId, Long toActivityId) {
        return new MoveResponse(fromActivityId, toActivityId, null, null, null, null, null, null);
    }

    /** 숙소로 가는 이동 — 아직 아무것도 적지 않았다. */
    public static MoveResponse emptyToStay(Long fromActivityId, Long stayId) {
        return new MoveResponse(fromActivityId, null, stayId, null, null, null, null, null);
    }

    public static MoveResponse between(Long fromActivityId, Long toActivityId, TravelMove move) {
        return of(fromActivityId, toActivityId, null, move);
    }

    public static MoveResponse toStay(Long fromActivityId, Long stayId, TravelMove move) {
        return of(fromActivityId, null, stayId, move);
    }

    private static MoveResponse of(Long fromActivityId, Long toActivityId, Long toStayId,
                                   TravelMove move) {
        return new MoveResponse(fromActivityId, toActivityId, toStayId, move.getMode(),
                move.getName(), move.getDurationMinutes(), move.getUrl(), move.getMemo());
    }
}
