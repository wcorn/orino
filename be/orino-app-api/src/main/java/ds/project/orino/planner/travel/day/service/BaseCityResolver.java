package ds.project.orino.planner.travel.day.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.PlaceKind;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.repository.TravelPlaceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 이름·타임존·통화로 들어온 목적지를 <b>도시 장소</b>로 바꾼다.
 *
 * <p>v2.1에서 타임존·통화의 주인은 여행이 아니라 도시다. 그런데 화면은 아직 목적지 하나를
 * 보내고(구간 입력은 #1121), 그 목적지가 검색으로 고른 장소일 수도 직접 친 이름일 수도 있다.
 * 두 경우 모두 기준 도시로 쓸 행이 있어야 하므로 여기서 승격하거나 만들어 준다.
 *
 * <p>같은 멤버가 같은 도시를 다시 쓰면 <b>행을 새로 만들지 않는다.</b> 이름만 같은 도시 행이
 * 여럿이면 도시 일치 판정({@code cityPlaceRef})이 여행마다 갈려, 같은 오사카인데 어떤 여행에서만
 * "다른 도시"로 표시된다.
 */
@Component
public class BaseCityResolver {

    private final TravelPlaceRepository placeRepository;

    public BaseCityResolver(TravelPlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    /**
     * 기준 도시로 쓸 장소를 찾아 반환한다. 필요하면 만들고, 검색으로 고른 장소면 도시로
     * 승격한다.
     *
     * @param placeId 검색으로 고른 장소. {@code null}이면 이름으로 도시를 만든다
     */
    @Transactional
    public TravelPlace resolve(Long memberId, Long placeId, String name,
                               String timezone, String currency,
                               BigDecimal lat, BigDecimal lng) {
        TravelPlace city = placeId != null
                ? promote(memberId, placeId, name, timezone, currency)
                : findOrCreateManualCity(memberId, name, timezone, currency);

        // 좌표는 날씨 조회의 기준점이다. 검색으로 고른 도시는 이미 갖고 있으므로 덮지 않는다.
        if (city.getLat() == null && lat != null && lng != null) {
            city.updateCoordinates(lat, lng);
        }
        return placeRepository.save(city);
    }

    private TravelPlace promote(Long memberId, Long placeId, String name,
                                String timezone, String currency) {
        TravelPlace place = placeRepository.findByIdAndMemberId(placeId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_PLACE_NOT_FOUND));
        // 목적지로 고른 장소는 곧 그 여행의 도시다 — v2.0의 destination_place_id가
        // 그런 의미였고, 마이그레이션도 같은 판단으로 CITY 표시를 달았다.
        place.promoteToCity(name, timezone, currency);
        return place;
    }

    private TravelPlace findOrCreateManualCity(Long memberId, String name,
                                               String timezone, String currency) {
        return placeRepository
                .findFirstByMemberIdAndNameAndPlaceKindAndGooglePlaceIdIsNull(
                        memberId, name, PlaceKind.CITY)
                .map(existing -> {
                    // 같은 이름의 도시라도 타임존·통화는 이번 입력이 최신이다.
                    existing.promoteToCity(name, timezone, currency);
                    return existing;
                })
                .orElseGet(() -> TravelPlace.manualCity(memberId, name, timezone, currency));
    }
}
