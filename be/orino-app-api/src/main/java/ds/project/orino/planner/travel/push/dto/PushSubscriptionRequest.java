package ds.project.orino.planner.travel.push.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 브라우저의 {@code PushSubscription.toJSON()}을 <b>그대로</b> 받는다.
 *
 * <p>모양을 바꾸지 않는 이유는 FE가 변환하지 않게 하려는 것이다 — 구독 객체를 손으로 풀어
 * 옮기다 키 하나를 빠뜨리면, 등록은 성공하고 알림만 조용히 안 온다.
 *
 * @param endpoint  푸시 서비스가 준 발송 주소
 * @param keys      구독 키. 페이로드 암호화에 쓴다
 * @param userAgent 기기 구분용(설정 화면에서 "어느 기기"인지 보여준다). 없어도 된다
 */
public record PushSubscriptionRequest(
        @NotBlank(message = "구독 주소가 필요합니다.")
        String endpoint,

        @NotNull(message = "구독 키가 필요합니다.")
        @Valid
        Keys keys,

        String userAgent
) {

    /**
     * @param p256dh 구독 공개키(65바이트 비압축 점, base64url)
     * @param auth   구독 인증 비밀(16바이트, base64url)
     */
    public record Keys(
            @NotBlank(message = "구독 공개키가 필요합니다.")
            String p256dh,

            @NotBlank(message = "구독 인증 비밀이 필요합니다.")
            String auth
    ) {
    }
}
