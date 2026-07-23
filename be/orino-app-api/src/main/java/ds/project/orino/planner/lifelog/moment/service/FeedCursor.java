package ds.project.orino.planner.lifelog.moment.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * 피드 커서. 마지막 항목의 {@code (occurredAt, id)}를 불투명 문자열로 인코딩한다.
 * occurred_at은 DATETIME(6) micros 그대로 담아 정렬 tie-break가 정확히 이어지게 한다.
 */
public record FeedCursor(Instant occurredAt, long id) {

    /** {@code {isoInstant}|{id}}를 base64url로. */
    public String encode() {
        String raw = occurredAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** null/blank면 첫 페이지(null). 형식이 깨졌으면 400. */
    public static FeedCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = raw.lastIndexOf('|');
            if (sep < 0) {
                throw new IllegalArgumentException("커서 구분자 없음");
            }
            Instant at = Instant.parse(raw.substring(0, sep));
            long id = Long.parseLong(raw.substring(sep + 1));
            return new FeedCursor(at, id);
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, e);
        }
    }
}
