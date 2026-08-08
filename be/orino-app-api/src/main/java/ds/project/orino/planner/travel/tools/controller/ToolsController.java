package ds.project.orino.planner.travel.tools.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.travel.tools.dto.ExchangeRateResponse;
import ds.project.orino.planner.travel.tools.dto.WeatherResponse;
import ds.project.orino.planner.travel.tools.service.ExchangeRateService;
import ds.project.orino.planner.travel.tools.service.WeatherService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** S-08 도구 — 날씨·환율. 둘 다 무료 API의 프록시다. */
@RestController
@RequestMapping("/api/travel")
public class ToolsController {

    private final WeatherService weatherService;
    private final ExchangeRateService exchangeRateService;

    public ToolsController(WeatherService weatherService,
                           ExchangeRateService exchangeRateService) {
        this.weatherService = weatherService;
        this.exchangeRateService = exchangeRateService;
    }

    /**
     * 여행 기간의 예보. <b>예보 범위(16일) 밖 날짜는 아예 없다</b> — 빈 배열이 정상이고,
     * 화면이 "예보 범위 밖"으로 처리한다.
     */
    @GetMapping("/trips/{tripId}/weather")
    public ApiResponse<WeatherResponse> weather(@AuthenticationPrincipal Long memberId,
                                                @PathVariable Long tripId) {
        return ApiResponse.success(weatherService.forTrip(memberId, tripId));
    }

    /** 1 {@code base}당 {@code quote}. ECB 기준이라 실제 결제 환율과는 다르다(화면 고지). */
    @GetMapping("/fx")
    public ApiResponse<ExchangeRateResponse> fx(@RequestParam String base,
                                                @RequestParam String quote) {
        return ApiResponse.success(exchangeRateService.rate(base, quote));
    }
}
