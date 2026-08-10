package ds.project.orino.planner.travel.day.dto;

import ds.project.orino.domain.planner.travel.entity.TravelPlace;

import java.math.BigDecimal;

/**
 * 날짜의 기준 도시. <b>그 날짜의 모든 파생값이 여기서 나온다</b> — 타임존·통화·날씨 좌표·
 * 검색 편향까지. 화면이 이 하나만 들고 있으면 그날의 시각 표시를 스스로 맞출 수 있다.
 */
public record BaseCityResponse(
        Long placeId,
        String name,
        String timezone,
        String currency,
        String countryCode,
        BigDecimal lat,
        BigDecimal lng
) {

    public static BaseCityResponse from(TravelPlace city) {
        return new BaseCityResponse(city.getId(),
                city.getCityName() != null ? city.getCityName() : city.getName(),
                city.getTimezone(), city.getCurrency(), city.getCountryCode(),
                city.getLat(), city.getLng());
    }
}
