package ds.project.orino.planner.travel.route.dto;

import ds.project.orino.planner.travel.route.client.TravelMode;

/**
 * 연속한 두 일정 사이 이동(§4.4). 보드 응답에 실려 일정 리스트에 항상 표시된다.
 *
 * @param fromActivityId  출발 일정
 * @param toActivityId    도착 일정. 사이에 장소 없는 일정이 끼면 건너뛴 결과다
 * @param mode            앱이 정한 이동수단. {@code crossCity}면 null — 수단을 판정하지 않는다
 * @param durationMinutes 소요 시간(분). {@code fallback}·{@code crossCity}면 null
 * @param distanceM       거리(m). 경로 거리이고, {@code fallback}·{@code crossCity}면 직선거리다
 * @param fallback        Routes를 못 얻어 직선거리로 대체했다. FE는 {@code 약 N.Nkm}로 보여준다
 * @param crossCity       도시 경계를 넘어 <b>계산하지 않았다</b>(§3.4). FE는 {@code 도시 이동}으로
 *                        그리고, 탭하면 이동수단 시트 없이 곧바로 대중교통 딥링크로 나간다 —
 *                        도시를 넘는 이동에 도보/자동차를 물어볼 이유가 없다.
 *                        <b>거리는 참고값일 뿐 화면에 쓰지 않는다</b>
 */
public record TravelTimeResponse(
        Long fromActivityId,
        Long toActivityId,
        TravelMode mode,
        Integer durationMinutes,
        int distanceM,
        boolean fallback,
        boolean crossCity
) {

    /**
     * 도시 경계를 넘는 이동. 외부 호출 없이 만든다 — 오사카에서 도쿄까지 "자동차 6시간"은
     * 틀린 값이고, 틀린 값을 사느니 안 사는 게 낫다.
     */
    public static TravelTimeResponse crossCity(Long fromActivityId, Long toActivityId,
                                               int straightM) {
        return new TravelTimeResponse(fromActivityId, toActivityId, null, null,
                straightM, false, true);
    }
}
