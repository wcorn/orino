package ds.project.orino.planner.travel.day.dto;

import ds.project.orino.domain.planner.travel.entity.TravelPlace;

import java.math.BigDecimal;

/**
 * 날짜의 기준 도시. <b>그 날짜의 모든 파생값이 여기서 나온다</b> — 타임존·통화·날씨 좌표·
 * 검색 편향까지. 화면이 이 하나만 들고 있으면 그날의 시각 표시를 스스로 맞출 수 있다.
 *
 * @param cityPlaceRef 도시 식별자. 장소의 같은 값과 맞춰 <b>도시 일치를 판정</b>한다 — 보관함
 *                     도시별 그룹과 담기 시트 정렬이 이 값으로 묶는다(이름으로 묶지 않는다).
 *                     직접 입력한 도시에는 없다
 */
public record BaseCityResponse(
        Long placeId,
        String name,
        String timezone,
        String currency,
        String countryCode,
        String cityPlaceRef,
        BigDecimal lat,
        BigDecimal lng
) {

    public static BaseCityResponse from(TravelPlace city) {
        return new BaseCityResponse(city.getId(),
                city.getCityName() != null ? city.getCityName() : city.getName(),
                city.getTimezone(), city.getCurrency(), city.getCountryCode(),
                city.getCityPlaceRef(), city.getLat(), city.getLng());
    }
}
