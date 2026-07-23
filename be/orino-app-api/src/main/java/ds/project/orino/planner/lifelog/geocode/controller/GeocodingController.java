package ds.project.orino.planner.lifelog.geocode.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.lifelog.geocode.GeocodePlace;
import ds.project.orino.planner.lifelog.geocode.service.GeocodingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 일상기록 지오코딩. 좌표↔장소명 변환을 BE가 Nominatim으로 프록시하고 Redis 캐시로 흡수한다.
 * 장소명 표시 편의 기능이라 실패해도 기록 저장 자체는 막지 않는다(FE가 위치 없이 저장).
 */
@RestController
@RequestMapping("/api/lifelog/geocode")
public class GeocodingController {

    private final GeocodingService geocodingService;

    public GeocodingController(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    /** 좌표 → 장소명. */
    @GetMapping("/reverse")
    public ApiResponse<GeocodePlace> reverse(@RequestParam double lat, @RequestParam double lng) {
        return ApiResponse.success(geocodingService.reverse(lat, lng));
    }

    /** 검색어 → 후보 장소(최대 10). */
    @GetMapping("/search")
    public ApiResponse<List<GeocodePlace>> search(@RequestParam("q") String query,
                                                  @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(geocodingService.search(query, limit));
    }
}
