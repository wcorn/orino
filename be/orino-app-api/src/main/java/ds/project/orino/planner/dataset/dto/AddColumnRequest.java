package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.Size;

/**
 * 열 추가 요청. key는 서버가 발급한다.
 *
 * <p>{@code label}은 선택이다. 비우면 서버가 유일한 기본 이름을 발급한다 —
 * 클라이언트가 열 개수로 이름을 지으면 열을 지운 뒤 중복이 생긴다.
 * 값을 주면 기존 열과 겹칠 때 거부한다.
 */
public record AddColumnRequest(
        @Size(max = 255, message = "label은 255자 이하여야 합니다.")
        String label
) {
}
