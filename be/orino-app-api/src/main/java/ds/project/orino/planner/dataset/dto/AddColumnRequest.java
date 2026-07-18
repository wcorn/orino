package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.Size;

/**
 * 열 추가 요청. key는 서버가 발급한다.
 *
 * <p>{@code label}은 선택이다. 비우면 서버가 유일한 기본 이름을 발급한다 —
 * 클라이언트가 열 개수로 이름을 지으면 열을 지운 뒤 중복이 생긴다.
 * 값을 주면 기존 열과 겹칠 때 거부한다.
 *
 * <p>{@code atIndex}는 선택이다. 주면 그 위치에 삽입하고(범위를 넘으면 클램프), 비우면 끝에 추가한다.
 * cells가 key 맵이라 위치 삽입도 columns_json 순서만 바꾸는 O(1)이다(기존 행 무손상).
 */
public record AddColumnRequest(
        @Size(max = 255, message = "label은 255자 이하여야 합니다.")
        String label,
        Integer atIndex
) {
}
