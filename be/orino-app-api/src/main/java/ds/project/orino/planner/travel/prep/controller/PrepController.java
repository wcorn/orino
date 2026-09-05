package ds.project.orino.planner.travel.prep.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.travel.prep.dto.PrepItemMutation;
import ds.project.orino.planner.travel.prep.dto.PrepRequests;
import ds.project.orino.planner.travel.prep.dto.PrepResponse;
import ds.project.orino.planner.travel.prep.service.PrepService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 준비(v2.2 §11~§13 · API §10). 출발까지 아직 안 한 게 뭔지에 답하는 화면 하나가 쓴다.
 *
 * <p>목록은 여행 아래(<code>/trips/{tripId}/prep</code>)에, 항목 하나를 다루는 것은
 * 여행 밖(<code>/prep/items/{itemId}</code>)에 둔다 — 숙소·일정과 같은 자리 규칙이다.
 * 항목 id 하나로 어느 여행인지가 이미 정해지므로, 경로에 여행을 또 적으면 둘이 어긋난
 * 요청이 만들어질 수 있다.
 */
@RestController
@RequestMapping("/api/travel")
public class PrepController {

    private final PrepService prepService;

    public PrepController(PrepService prepService) {
        this.prepService = prepService;
    }

    /** 화면 한 벌. 분류 4개는 항목이 없어도 전부 내려간다. */
    @GetMapping("/trips/{tripId}/prep")
    public ApiResponse<PrepResponse> get(@AuthenticationPrincipal Long memberId,
                                         @PathVariable Long tripId) {
        return ApiResponse.success(prepService.get(memberId, tripId));
    }

    /** 붙박이 입력줄이 엔터 한 번으로 보내는 요청. {@code title}만 필수다. */
    @PostMapping("/trips/{tripId}/prep/items")
    public ApiResponse<PrepItemMutation> create(@AuthenticationPrincipal Long memberId,
                                                @PathVariable Long tripId,
                                                @Valid @RequestBody PrepRequests.Create request) {
        return ApiResponse.success(prepService.create(memberId, tripId, request));
    }

    /** 체크 토글도 여기다. 응답에 갱신된 집계가 함께 실린다. */
    @PatchMapping("/prep/items/{itemId}")
    public ApiResponse<PrepItemMutation> patch(@AuthenticationPrincipal Long memberId,
                                               @PathVariable Long itemId,
                                               @Valid @RequestBody PrepRequests.Patch request) {
        return ApiResponse.success(prepService.patch(memberId, itemId, request));
    }

    /** 즉시 삭제. 되돌리기는 FE의 5초 대기라 여기까지 오면 되돌릴 뜻이 없다. */
    @DeleteMapping("/prep/items/{itemId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
                                    @PathVariable Long itemId) {
        prepService.delete(memberId, itemId);
        return ApiResponse.success();
    }

    /** 한 분류 안의 전체 순서. 분류를 넘는 이동은 {@link #patch}의 {@code category}다. */
    @PutMapping("/trips/{tripId}/prep/order")
    public ApiResponse<Void> reorder(@AuthenticationPrincipal Long memberId,
                                     @PathVariable Long tripId,
                                     @Valid @RequestBody PrepRequests.Order request) {
        prepService.reorder(memberId, tripId, request);
        return ApiResponse.success();
    }
}
