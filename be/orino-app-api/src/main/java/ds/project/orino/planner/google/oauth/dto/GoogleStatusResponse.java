package ds.project.orino.planner.google.oauth.dto;

import java.time.Instant;
import java.util.List;

/**
 * Google 연동 상태. 미연동(또는 revoked)이면 connected=false, 나머지 필드는 null/false.
 *
 * @param reviewMirrorEnabled 복습 → 보조 캘린더 미러 on/off (미연동이면 false)
 */
public record GoogleStatusResponse(
        boolean connected,
        String googleEmail,
        List<String> scopes,
        Instant connectedAt,
        boolean reviewMirrorEnabled
) {

    public static GoogleStatusResponse disconnected() {
        return new GoogleStatusResponse(false, null, null, null, false);
    }

    public static GoogleStatusResponse connected(String googleEmail, List<String> scopes,
                                                 Instant connectedAt, boolean reviewMirrorEnabled) {
        return new GoogleStatusResponse(true, googleEmail, scopes, connectedAt, reviewMirrorEnabled);
    }
}
