package ds.project.orino.planner.travel.place.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.travel.place.dto.CityRefreshResponse;
import ds.project.orino.planner.travel.place.dto.CityResponse;
import ds.project.orino.planner.travel.place.dto.PlaceCreateRequest;
import ds.project.orino.planner.travel.place.dto.PlaceDetail;
import ds.project.orino.planner.travel.place.dto.PlaceSearchResult;
import ds.project.orino.planner.travel.place.service.PlaceService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 장소 프록시. 브라우저가 Google API를 직접 부르지 않는다(키 비노출 + 캐시 + 응답 형태 통제). */
@RestController
@RequestMapping("/api/travel/places")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    /** S-03 목적지 검색. 타임존·통화를 확정해서 준다. */
    @GetMapping("/cities")
    public ApiResponse<List<CityResponse>> cities(@RequestParam String q) {
        return ApiResponse.success(placeService.searchCities(q));
    }

    /**
     * S-06 장소 검색.
     *
     * @param tripId 주면 그 여행의 도시 주변을 우선한다
     * @param city   (v2.1) 기준 도시 칩의 도시 {@code placeId}. 주면 <b>그 도시</b>로 편향하고,
     *               없으면 첫날 도시로 떨어진다
     */
    @GetMapping("/search")
    public ApiResponse<List<PlaceSearchResult>> search(
            @AuthenticationPrincipal Long memberId,
            @RequestParam String q,
            @RequestParam(required = false) Long tripId,
            @RequestParam(required = false) Long city) {
        return ApiResponse.success(placeService.searchPlaces(memberId, q, tripId, city));
    }

    @GetMapping("/{placeId}")
    public ApiResponse<PlaceDetail> detail(@AuthenticationPrincipal Long memberId,
                                           @PathVariable Long placeId) {
        return ApiResponse.success(placeService.detail(memberId, placeId));
    }

    /** 직접 입력(검색 결과가 없을 때). */
    @PostMapping
    public ApiResponse<PlaceDetail> create(@AuthenticationPrincipal Long memberId,
                                           @Valid @RequestBody PlaceCreateRequest request) {
        return ApiResponse.success(placeService.createManual(memberId, request));
    }

    /**
     * 도시 이름 새로고침(#1375). 광역 행정구역을 아직 못 채운 장소를 <b>한 묶음만</b> 다시
     * 받아 온다.
     *
     * <p>장소마다 유료 호출 한 번이라 <b>자동으로 돌지 않는다</b> — 사용자가 누른 만큼만
     * 나가고, 남은 개수를 함께 돌려줘 몇 번 더 눌러야 하는지 보이게 한다.
     */
    @PostMapping("/refresh-city")
    public ApiResponse<CityRefreshResponse> refreshCity(
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(placeService.refreshAdminAreas(memberId));
    }
}
