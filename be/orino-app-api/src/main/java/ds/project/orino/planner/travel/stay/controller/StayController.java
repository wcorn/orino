package ds.project.orino.planner.travel.stay.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.travel.stay.dto.StayRequest;
import ds.project.orino.planner.travel.stay.dto.StayResponse;
import ds.project.orino.planner.travel.stay.service.StayService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 숙소(§4.5). 보드 응답에도 그날의 숙소가 실려 가지만, 목록·등록·수정은 <b>기간을 통째로</b>
 * 다뤄야 해서 따로 둔다 — 숙소는 날짜가 아니라 기간을 갖는다.
 */
@RestController
@RequestMapping("/api/travel")
public class StayController {

    private final StayService stayService;

    public StayController(StayService stayService) {
        this.stayService = stayService;
    }

    @GetMapping("/trips/{tripId}/stays")
    public ApiResponse<List<StayResponse>> list(@AuthenticationPrincipal Long memberId,
                                                @PathVariable Long tripId) {
        return ApiResponse.success(stayService.list(memberId, tripId));
    }

    /** 겹치는 기간이면 409 {@code TRAVEL-ERR-017} + 겹치는 숙소 정보. */
    @PostMapping("/trips/{tripId}/stays")
    public ApiResponse<StayResponse> create(@AuthenticationPrincipal Long memberId,
                                            @PathVariable Long tripId,
                                            @Valid @RequestBody StayRequest request) {
        return ApiResponse.success(stayService.create(memberId, tripId, request));
    }

    @PutMapping("/stays/{stayId}")
    public ApiResponse<StayResponse> update(@AuthenticationPrincipal Long memberId,
                                            @PathVariable Long stayId,
                                            @Valid @RequestBody StayRequest request) {
        return ApiResponse.success(stayService.update(memberId, stayId, request));
    }

    @DeleteMapping("/stays/{stayId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
                                    @PathVariable Long stayId) {
        stayService.delete(memberId, stayId);
        return ApiResponse.success();
    }
}
