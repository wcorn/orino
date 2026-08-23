package ds.project.orino.planner.shortlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 발급 요청. 기본 경로는 <b>URL 하나만 넣고 Enter</b>다(명세 §4.1) — 나머지는 전부 선택이고,
 * 옵션을 늘리는 요구는 "발급 3초"와 겨뤄야 한다.
 *
 * @param slug     비우면 자동 5자. 넣으면 그 슬러그를 그대로 쓰되 <b>이후 바꿀 수 없다</b>(명세 §5.2)
 * @param password 있으면 BCrypt로 저장한다. 확인 화면은 #1244에서 붙는다
 */
public record ShortlinkCreateRequest(
        @NotBlank(message = "목적지 주소를 입력해 주세요.")
        @Size(max = 2048, message = "목적지 주소가 너무 깁니다.")
        String targetUrl,

        @Size(max = 32, message = "주소는 32자를 넘을 수 없습니다.")
        String slug,

        @Size(max = 255, message = "메모는 255자를 넘을 수 없습니다.")
        String memo,

        List<String> tags,

        Instant expiresAt,

        @Size(max = 72, message = "비밀번호가 너무 깁니다.")
        String password
) {
}
