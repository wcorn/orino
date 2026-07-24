package ds.project.orino.planner.google.oauth;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.google.oauth.dto.GoogleAuthUrlResponse;
import ds.project.orino.planner.google.oauth.dto.GoogleStatusResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Google 연동 OAuth/상태 API. 콜백(/oauth/callback)만 permitAll(브라우저 top-level redirect, JWT 헤더 없음),
 * 나머지는 JWT 인증.
 */
@RestController
@RequestMapping("/api/integrations/google")
public class GoogleOAuthController {

    private final GoogleOAuthService googleOAuthService;

    public GoogleOAuthController(GoogleOAuthService googleOAuthService) {
        this.googleOAuthService = googleOAuthService;
    }

    @GetMapping("/oauth/url")
    public ApiResponse<GoogleAuthUrlResponse> authorizationUrl(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(
                new GoogleAuthUrlResponse(googleOAuthService.createAuthorizationUrl(memberId)));
    }

    @GetMapping("/oauth/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        String redirectUrl = googleOAuthService.handleCallback(code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }

    @GetMapping("/status")
    public ApiResponse<GoogleStatusResponse> status(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(googleOAuthService.getStatus(memberId));
    }

    @PostMapping("/disconnect")
    public ApiResponse<Void> disconnect(@AuthenticationPrincipal Long memberId) {
        googleOAuthService.disconnect(memberId);
        return ApiResponse.success();
    }
}
