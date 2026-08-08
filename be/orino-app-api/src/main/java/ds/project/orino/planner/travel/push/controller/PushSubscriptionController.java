package ds.project.orino.planner.travel.push.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.travel.push.config.VapidProperties;
import ds.project.orino.planner.travel.push.dto.PushSubscriptionRequest;
import ds.project.orino.planner.travel.push.dto.PushUnsubscribeRequest;
import ds.project.orino.planner.travel.push.dto.VapidPublicKeyResponse;
import ds.project.orino.planner.travel.push.service.NotificationDispatchService;
import ds.project.orino.planner.travel.push.service.PushSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 웹푸시 구독(§6). 기기가 등록·해지하고, 구독에 필요한 공개키를 받아간다. */
@RestController
@RequestMapping("/api/travel/push")
public class PushSubscriptionController {

    private final PushSubscriptionService subscriptionService;
    private final NotificationDispatchService dispatchService;
    private final VapidProperties vapidProperties;

    public PushSubscriptionController(PushSubscriptionService subscriptionService,
                                      NotificationDispatchService dispatchService,
                                      VapidProperties vapidProperties) {
        this.subscriptionService = subscriptionService;
        this.dispatchService = dispatchService;
        this.vapidProperties = vapidProperties;
    }

    /**
     * 구독에 쓸 서버 공개키. 키가 없으면 {@code null}을 준다 —
     * FE는 그걸 보고 알림 UI를 감춘다(오류가 아니라 "아직 없음"이다).
     */
    @GetMapping("/public-key")
    public ApiResponse<VapidPublicKeyResponse> publicKey() {
        return ApiResponse.success(new VapidPublicKeyResponse(
                vapidProperties.enabled() ? vapidProperties.publicKey() : null));
    }

    @PostMapping("/subscriptions")
    public ApiResponse<Void> subscribe(@AuthenticationPrincipal Long memberId,
                                       @Valid @RequestBody PushSubscriptionRequest request) {
        subscriptionService.subscribe(memberId, request);
        return ApiResponse.success(null);
    }

    /**
     * 해지. 지울 대상을 {@code endpoint}로 지정한다 — 기기가 아는 것은 자기 endpoint뿐이고,
     * 내부 id를 굳이 알려줄 이유가 없다.
     */
    @DeleteMapping("/subscriptions")
    public ApiResponse<Void> unsubscribe(@AuthenticationPrincipal Long memberId,
                                         @Valid @RequestBody PushUnsubscribeRequest request) {
        subscriptionService.unsubscribe(memberId, request.endpoint());
        return ApiResponse.success(null);
    }

    /**
     * 즉시 발송(S-09). 실기기 검증은 이것 없이 시작할 수 없다 — 일정을 만들고 시각이
     * 오기를 기다릴 수는 없다.
     *
     * @return 실제로 전달된 구독 수. 0이면 구독이 없거나 전부 실패한 것이다
     */
    @PostMapping("/test")
    public ApiResponse<Integer> test(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(dispatchService.sendTest(memberId));
    }
}
