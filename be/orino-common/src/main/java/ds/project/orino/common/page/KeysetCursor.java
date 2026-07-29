package ds.project.orino.common.page;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * keyset 페이지네이션 커서. 정렬 키 {@code (Instant, id)}를 base64로 인코딩한 불투명 문자열이다.
 * 복습 앞으로 목록은 {@code (scheduledAt, id)}, 완료 목록은 {@code (completedAt, id)},
 * 카드 목록은 {@code (createdAt, id)}를 담는다.
 * 형식은 내부 구현 세부사항이며 FE는 이전 응답의 {@code nextCursor}를 그대로 되돌려준다.
 */
public record KeysetCursor(Instant at, long id) {

    public String encode() {
        String raw = at.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static KeysetCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = raw.lastIndexOf('|');
            return new KeysetCursor(Instant.parse(raw.substring(0, sep)),
                    Long.parseLong(raw.substring(sep + 1)));
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }
}
