package ds.project.orino.planner.shortlink.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.shortlink.dto.CreatedLink;
import ds.project.orino.planner.shortlink.dto.FavoriteResponse;
import ds.project.orino.planner.shortlink.dto.LinkStatsResponse;
import ds.project.orino.planner.shortlink.dto.ListStatusFilter;
import ds.project.orino.planner.shortlink.dto.ShortlinkCreateRequest;
import ds.project.orino.planner.shortlink.dto.ShortlinkDetail;
import ds.project.orino.planner.shortlink.dto.ShortlinkListResponse;
import ds.project.orino.planner.shortlink.dto.ShortlinkSummaryResponse;
import ds.project.orino.planner.shortlink.dto.ShortlinkUpdateRequest;
import ds.project.orino.planner.shortlink.dto.SlugAvailableResponse;
import ds.project.orino.planner.shortlink.dto.TagCount;
import ds.project.orino.planner.shortlink.dto.ToggleResponse;
import ds.project.orino.planner.shortlink.service.ShortlinkService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 링크 관리 API. <b>경로 키가 id가 아니라 slug다</b> — 슬러그는 불변이고(명세 §5.2) 사용자가
 * 실제로 보고 부르는 식별자다(결정 기록 D-5). FE 라우트도 {@code /links/{slug}}로 맞춘다.
 *
 * <p>공개 리다이렉트({@code /r/**}, #1237)는 이 컨트롤러에 들어오지 않는다. 그쪽은 인증도
 * envelope도 없고 응답이 302·404 HTML이다. 두 표면을 섞지 않는다(API 설계 머리말).
 */
@RestController
@RequestMapping("/api/shortlinks")
public class ShortlinkController {

    private final ShortlinkService shortlinkService;

    public ShortlinkController(ShortlinkService shortlinkService) {
        this.shortlinkService = shortlinkService;
    }

    /** {@code /select} 링크 카드의 메타 줄. */
    @GetMapping("/summary")
    public ApiResponse<ShortlinkSummaryResponse> summary(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(shortlinkService.summary(memberId));
    }

    @GetMapping
    public ApiResponse<ShortlinkListResponse> list(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) ListStatusFilter status,
            @RequestParam(required = false) String tag) {
        return ApiResponse.success(shortlinkService.list(memberId, query, status, tag));
    }

    @PostMapping
    public ApiResponse<CreatedLink> create(@AuthenticationPrincipal Long memberId,
                                           @Valid @RequestBody ShortlinkCreateRequest request) {
        return ApiResponse.success(shortlinkService.create(memberId, request));
    }

    /** 사이드바 태그 + 개수. {@code /{slug}}보다 먼저 매칭되도록 위에 둔다. */
    @GetMapping("/tags")
    public ApiResponse<List<TagCount>> tags(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(shortlinkService.tags(memberId));
    }

    /** 커스텀 슬러그 중복 검사. FE는 디바운스해서 부른다. */
    @GetMapping("/slug-available")
    public ApiResponse<SlugAvailableResponse> slugAvailable(@RequestParam String slug) {
        return ApiResponse.success(shortlinkService.slugAvailable(slug));
    }

    @GetMapping("/{slug}")
    public ApiResponse<ShortlinkDetail> detail(@AuthenticationPrincipal Long memberId,
                                               @PathVariable String slug) {
        return ApiResponse.success(shortlinkService.detail(memberId, slug));
    }

    /**
     * 방문 통계. {@code range}는 {@code 7d}·{@code 30d}처럼 준다(기본 30일).
     *
     * <p>범위는 <b>유입 경로·기기·국가에만</b> 걸린다 — 총 방문과 일별 추이는 집계 테이블에서
     * 나오므로 범위 제한이 없다(명세 §8.3).
     */
    @GetMapping("/{slug}/stats")
    public ApiResponse<LinkStatsResponse> stats(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String slug,
            @RequestParam(required = false, defaultValue = "30d") String range) {
        return ApiResponse.success(shortlinkService.stats(memberId, slug, range));
    }

    /** 목적지·메모·태그·만료·비밀번호를 바꾼다. <b>슬러그는 받지 않는다</b>(명세 §5.2). */
    @PatchMapping("/{slug}")
    public ApiResponse<ShortlinkDetail> update(@AuthenticationPrincipal Long memberId,
                                               @PathVariable String slug,
                                               @Valid @RequestBody ShortlinkUpdateRequest request) {
        return ApiResponse.success(shortlinkService.update(memberId, slug, request));
    }

    @PostMapping("/{slug}/toggle")
    public ApiResponse<ToggleResponse> toggle(@AuthenticationPrincipal Long memberId,
                                              @PathVariable String slug) {
        return ApiResponse.success(shortlinkService.toggle(memberId, slug));
    }

    @PostMapping("/{slug}/favorite")
    public ApiResponse<FavoriteResponse> favorite(@AuthenticationPrincipal Long memberId,
                                                  @PathVariable String slug) {
        return ApiResponse.success(shortlinkService.toggleFavorite(memberId, slug));
    }

    /** 소프트 삭제. 이후 그 슬러그는 <b>영구히 점유된다</b>(명세 §3.1). */
    @DeleteMapping("/{slug}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
                                    @PathVariable String slug) {
        shortlinkService.delete(memberId, slug);
        return ApiResponse.success();
    }
}
