package ds.project.orino.planner.travel.route.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.travel.route.client.TravelMode;
import ds.project.orino.planner.travel.route.dto.TravelTimeResponse;
import ds.project.orino.planner.travel.route.service.TravelTimeQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이동수단 시트(§S-04)가 여는 단건 조회.
 *
 * <p>보드는 직선거리로 정한 한쪽 수단만 내려준다. 다른 쪽을 보려면 여기로 온다 —
 * 보드에서 둘 다 계산해 두면 아무도 안 열어 볼 값까지 미리 사게 된다(호출당 과금).
 */
@RestController
@RequestMapping("/api/travel/trips/{tripId}/travel-time")
public class TravelTimeController {

    private final TravelTimeQueryService travelTimeQueryService;

    public TravelTimeController(TravelTimeQueryService travelTimeQueryService) {
        this.travelTimeQueryService = travelTimeQueryService;
    }

    @GetMapping
    public ApiResponse<TravelTimeResponse> travelTime(@AuthenticationPrincipal Long memberId,
                                                      @PathVariable Long tripId,
                                                      @RequestParam Long from,
                                                      @RequestParam Long to,
                                                      @RequestParam TravelMode mode) {
        return ApiResponse.success(
                travelTimeQueryService.travelTime(memberId, tripId, from, to, mode));
    }
}
