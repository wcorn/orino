package ds.project.orino.planner.lifelog.moment.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.lifelog.moment.service.MomentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 태그 자동완성. 멤버가 이미 쓴 태그 중 접두어로 시작하는 것들을 돌려준다.
 */
@RestController
@RequestMapping("/api/lifelog/tags")
public class MomentTagController {

    private final MomentService momentService;

    public MomentTagController(MomentService momentService) {
        this.momentService = momentService;
    }

    @GetMapping
    public ApiResponse<List<String>> autocomplete(@AuthenticationPrincipal Long memberId,
                                                  @RequestParam(value = "q", required = false) String query) {
        return ApiResponse.success(momentService.autocompleteTags(memberId, query));
    }
}
