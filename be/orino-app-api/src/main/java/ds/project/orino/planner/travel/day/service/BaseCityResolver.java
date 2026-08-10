package ds.project.orino.planner.travel.day.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.repository.TravelPlaceRepository;
import ds.project.orino.planner.travel.place.service.PlaceService;
import ds.project.orino.planner.travel.trip.dto.TripLegRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 구간이 가리키는 도시를 <b>기준 도시로 쓸 수 있는 장소</b>로 바꾼다.
 *
 * <p>입력은 두 가지다 — 이미 담아 둔 도시({@code cityPlaceId})거나, 검색 결과 그대로
 * ({@code cityGooglePlaceId})다. 뒤쪽을 받는 이유는 일정 담기와 같다: 고르기 전에 저장부터
 * 하라고 하면 저장했다가 취소한 도시가 쌓인다.
 *
 * <p><b>도시가 아닌 장소는 거절한다.</b> "오사카성"을 기준 도시로 받으면 타임존은 우연히
 * 맞지만 도시 일치 판정({@code cityPlaceRef})이 그날 일정을 전부 "다른 도시"로 만든다.
 */
@Component
public class BaseCityResolver {

    private final TravelPlaceRepository placeRepository;
    private final PlaceService placeService;

    public BaseCityResolver(TravelPlaceRepository placeRepository, PlaceService placeService) {
        this.placeRepository = placeRepository;
        this.placeService = placeService;
    }

    /** 구간 목록의 도시를 순서대로 해석한다. 같은 도시가 여러 번 나오면 같은 행을 가리킨다. */
    @Transactional
    public List<TravelPlace> resolveAll(Long memberId, List<TripLegRequest> legs) {
        return legs.stream().map(leg -> resolve(memberId, leg)).toList();
    }

    @Transactional
    public TravelPlace resolve(Long memberId, TripLegRequest leg) {
        if (leg.cityPlaceId() != null) {
            return requireCity(placeRepository
                    .findByIdAndMemberId(leg.cityPlaceId(), memberId)
                    .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_PLACE_NOT_FOUND)));
        }
        return requireCity(
                placeService.upsertCityFromGoogle(memberId, leg.cityGooglePlaceId().trim()));
    }

    /**
     * 도시로 쓸 수 있는지 확인한다. 타임존이 없는 도시도 막는다 — 저장은 되지만 그 날짜의
     * 시각 계산이 전부 기기 타임존으로 떨어져, 화면은 멀쩡한데 알림만 엉뚱한 시각에 간다.
     */
    private TravelPlace requireCity(TravelPlace place) {
        if (!place.isCity() || place.getTimezone() == null) {
            throw new CustomException(ErrorCode.TRAVEL_NOT_A_CITY);
        }
        return place;
    }
}
