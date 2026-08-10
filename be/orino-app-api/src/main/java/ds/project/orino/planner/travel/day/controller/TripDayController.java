package ds.project.orino.planner.travel.day.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.travel.day.dto.CityLegResponse;
import ds.project.orino.planner.travel.day.dto.DayUpdateRequest;
import ds.project.orino.planner.travel.day.dto.TripDayResponse;
import ds.project.orino.planner.travel.day.service.BaseCityChangeService;
import ds.project.orino.planner.travel.day.service.TripDayQueryService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 날짜와 구간(§4.5). 보드 응답에도 같은 내용이 들어가지만, 구간 편집기(S-03)와 담기 시트처럼
 * <b>일정 없이 날짜만</b> 필요한 화면이 따로 부른다.
 */
@RestController
@RequestMapping("/api/travel")
public class TripDayController {

    private final TripDayQueryService queryService;
    private final BaseCityChangeService baseCityChangeService;

    public TripDayController(TripDayQueryService queryService,
                             BaseCityChangeService baseCityChangeService) {
        this.queryService = queryService;
        this.baseCityChangeService = baseCityChangeService;
    }

    @GetMapping("/trips/{tripId}/days")
    public ApiResponse<List<TripDayResponse>> days(@AuthenticationPrincipal Long memberId,
                                                   @PathVariable Long tripId) {
        return ApiResponse.success(queryService.days(memberId, tripId));
    }

    /**
     * 기준 도시 변경 · 도시 메모. 응답은 기간 전체의 날짜다 — 하루를 바꾸면 구간이 다시
     * 나뉘어 앞뒤 날짜의 표시까지 달라진다.
     */
    @PutMapping("/days/{dayId}")
    public ApiResponse<List<TripDayResponse>> updateDay(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long dayId,
            @Valid @RequestBody DayUpdateRequest request) {
        return ApiResponse.success(baseCityChangeService.update(memberId, dayId, request));
    }

    /** 파생 구간. 저장된 것이 아니라 날짜에서 매번 계산한다. */
    @GetMapping("/trips/{tripId}/city-legs")
    public ApiResponse<List<CityLegResponse>> cityLegs(@AuthenticationPrincipal Long memberId,
                                                       @PathVariable Long tripId) {
        return ApiResponse.success(queryService.cityLegs(memberId, tripId));
    }
}
