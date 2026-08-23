package ds.project.orino.planner.shortlink.dto;

import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 편집 요청. 보낸 필드만 바꾼다.
 *
 * <p><b>{@code slug}는 여기 없다.</b> 슬러그를 바꿀 수 있으면 이미 뿌린 주소가 죽고, 비어 버린
 * 옛 슬러그가 재발급 가능해져 영구 점유(§3.1)도 무너진다. 요청 본문에 {@code slug}가 실려
 * 와도 이 레코드에 자리가 없으므로 그냥 무시된다(명세 §5.2 · 결정 기록 D-5).
 *
 * <p>목적지 교체를 별도 엔드포인트로 나누지 않는다 — 나누면 "메모만 고치다가 목적지도 같이
 * 고치는" 흔한 경우에 요청이 둘로 갈린다. 대신 <b>값이 실제로 달라졌을 때만</b> 이력을 남긴다.
 *
 * <p>{@code expiresAt}·{@code password}가 {@link JsonNode}인 이유는 <b>"안 보냄"과 "null을
 * 보냄"을 구분해야 하기 때문이다</b> — 전자는 변경 없음, 후자는 만료·비밀번호 해제다.
 * 보통 타입으로 받으면 둘 다 null로 도착해 해제할 방법이 사라진다.
 *
 * @param targetChangeReason 목적지가 실제로 바뀔 때만 이력에 실린다
 */
public record ShortlinkUpdateRequest(
        @Size(max = 2048, message = "목적지 주소가 너무 깁니다.")
        String targetUrl,

        @Size(max = 255, message = "교체 사유는 255자를 넘을 수 없습니다.")
        String targetChangeReason,

        @Size(max = 255, message = "메모는 255자를 넘을 수 없습니다.")
        String memo,

        List<String> tags,

        JsonNode expiresAt,

        JsonNode password
) {
}
