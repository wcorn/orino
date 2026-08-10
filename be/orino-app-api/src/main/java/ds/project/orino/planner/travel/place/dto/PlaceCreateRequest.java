package ds.project.orino.planner.travel.place.dto;

import ds.project.orino.domain.planner.travel.entity.PlaceKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 직접 입력한 장소(S-06 "검색 결과가 없어요"). 구글에 없는 곳도 일정에 넣을 수 있어야 한다.
 *
 * <p>{@code kind = CITY}면 <b>기준 도시로 쓸 수 있는 도시 장소</b>를 만든다. 도시 검색이 못
 * 찾는 곳으로 가는 여행도 기준 도시가 없으면 타임존이 없어 시각 계산이 통째로 죽는다.
 * 이때는 구글이 알려줄 수 없으므로 {@code timezone}·{@code currency}를 함께 받는다.
 *
 * @param kind 생략하면 {@link PlaceKind#POI}
 */
public record PlaceCreateRequest(
        @NotBlank(message = "장소 이름을 입력해 주세요.")
        @Size(max = 200, message = "장소 이름은 200자를 넘을 수 없습니다.")
        String name,

        @Size(max = 500, message = "주소는 500자를 넘을 수 없습니다.")
        String address,

        BigDecimal lat,
        BigDecimal lng,

        PlaceKind kind,

        @Size(max = 64, message = "시간대가 너무 깁니다.")
        String timezone,

        @Size(max = 3, message = "통화 코드는 3자리입니다.")
        String currency
) {

    public boolean isCity() {
        return kind == PlaceKind.CITY;
    }
}
