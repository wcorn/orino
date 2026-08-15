package ds.project.orino.planner.travel.move.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.travel.move.dto.MoveResponse;
import ds.project.orino.planner.travel.move.dto.MoveWriteRequest;
import ds.project.orino.planner.travel.move.service.MoveWriteService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이동 저장·삭제(#1208).
 *
 * <p><b>조회가 없다.</b> 이동은 보드 응답에 함께 실려 온다 — 화면이 이동을 따로 받아야 하면
 * 오프라인 캐시가 응답 하나로 성립하지 않는다(§S-04).
 */
@RestController
@RequestMapping("/api/travel/trips/{tripId}/moves")
public class MoveController {

    private final MoveWriteService moveWriteService;

    public MoveController(MoveWriteService moveWriteService) {
        this.moveWriteService = moveWriteService;
    }

    /** 등록·수정이 같은 요청이다. 한 구간에 이동은 하나라 덮어쓴다. */
    @PutMapping
    public ApiResponse<MoveResponse> save(@AuthenticationPrincipal Long memberId,
                                          @PathVariable Long tripId,
                                          @Valid @RequestBody MoveWriteRequest request) {
        return ApiResponse.success(moveWriteService.save(memberId, tripId, request));
    }

    /**
     * 이동을 지운다. 도착지는 {@code to}(일정) 또는 {@code toStay}(숙소) 중 하나만 준다.
     */
    @DeleteMapping
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
                                    @PathVariable Long tripId,
                                    @RequestParam Long from,
                                    @RequestParam(required = false) Long to,
                                    @RequestParam(required = false) Long toStay) {
        moveWriteService.delete(memberId, tripId, from, to, toStay);
        return ApiResponse.success();
    }
}
