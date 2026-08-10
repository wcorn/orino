package ds.project.orino.planner.travel.trip.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 구간 하나 — <b>도시 + 머무는 일수</b>. 목록의 순서가 곧 방문 순서다.
 *
 * <p>날짜가 아니라 일수를 받는다. 여행을 짜는 동안 기간은 계속 움직이는데, 구간마다 날짜를
 * 적어 두면 기간을 하루 늘릴 때마다 뒤 구간을 전부 고쳐야 한다.
 *
 * <p>도시는 <b>둘 중 하나</b>로 지정한다.
 * <ul>
 *   <li>{@code cityPlaceId} — 이미 담아 둔 도시 장소({@code place_kind = CITY})</li>
 *   <li>{@code cityGooglePlaceId} — 검색 결과를 그대로. 서버가 담고 도시로 승격한다</li>
 * </ul>
 *
 * <p>검색 결과를 그대로 받는 쪽이 있는 이유는 일정 담기와 같다 — 화면이 "고른 것"을 보내면
 * 서버가 id로 바꾼다. 고르기 전에 저장부터 하라고 하면 저장했다가 취소한 도시가 쌓인다.
 */
public record TripLegRequest(
        Long cityPlaceId,

        String cityGooglePlaceId,

        @NotNull(message = "구간의 일수를 입력해 주세요.")
        @Positive(message = "구간의 일수는 1일 이상이어야 합니다.")
        Integer days
) {

    /** 둘 다 없으면 도시를 알 수 없고, 둘 다 있으면 어느 쪽이 맞는지 서버가 정할 수 없다. */
    @JsonIgnore
    @AssertTrue(message = "구간의 도시는 cityPlaceId 또는 cityGooglePlaceId 중 하나로 지정해 주세요.")
    public boolean isCityGivenExactlyOnce() {
        boolean hasPlaceId = cityPlaceId != null;
        boolean hasGoogleId = cityGooglePlaceId != null && !cityGooglePlaceId.isBlank();
        return hasPlaceId ^ hasGoogleId;
    }
}
