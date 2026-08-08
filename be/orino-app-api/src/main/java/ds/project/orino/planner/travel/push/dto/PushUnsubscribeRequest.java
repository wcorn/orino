package ds.project.orino.planner.travel.push.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 해지 요청. <b>주소만</b> 받는다.
 *
 * <p>기기가 구독을 지우는 시점엔 키를 이미 버렸을 수 있다. 등록과 같은 형태를 요구하면
 * 해지할 방법이 없어진다.
 */
public record PushUnsubscribeRequest(
        @NotBlank(message = "구독 주소가 필요합니다.")
        String endpoint
) {
}
