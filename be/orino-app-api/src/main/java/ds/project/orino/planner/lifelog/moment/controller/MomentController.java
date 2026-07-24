package ds.project.orino.planner.lifelog.moment.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.lifelog.moment.dto.FeedResponse;
import ds.project.orino.planner.lifelog.moment.dto.MomentCard;
import ds.project.orino.planner.lifelog.moment.dto.MomentWriteRequest;
import ds.project.orino.planner.lifelog.moment.service.MomentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/lifelog/moments")
public class MomentController {

    private final MomentService momentService;

    public MomentController(MomentService momentService) {
        this.momentService = momentService;
    }

    @PostMapping
    public ApiResponse<MomentCard> create(@AuthenticationPrincipal Long memberId,
                                          @Valid @RequestBody MomentWriteRequest request) {
        return ApiResponse.success(momentService.create(memberId, request));
    }

    /** 역시간순 피드(커서 페이지네이션). 선택 필터: tag·기간(from/to, ISO-8601). */
    @GetMapping
    public ApiResponse<FeedResponse> feed(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.success(momentService.feed(memberId, cursor, size, tag, from, to));
    }

    @GetMapping("/{id}")
    public ApiResponse<MomentCard> findOne(@AuthenticationPrincipal Long memberId,
                                           @PathVariable Long id) {
        return ApiResponse.success(momentService.findOne(memberId, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<MomentCard> update(@AuthenticationPrincipal Long memberId,
                                          @PathVariable Long id,
                                          @Valid @RequestBody MomentWriteRequest request) {
        return ApiResponse.success(momentService.update(memberId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
                                    @PathVariable Long id) {
        momentService.delete(memberId, id);
        return ApiResponse.success();
    }
}
