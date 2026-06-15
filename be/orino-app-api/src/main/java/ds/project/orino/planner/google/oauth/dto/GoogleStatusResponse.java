package ds.project.orino.planner.google.oauth.dto;

import java.time.Instant;
import java.util.List;

/**
 * Google 연동 상태. 미연동(또는 revoked)이면 connected=false, 나머지 필드는 null.
 */
public record GoogleStatusResponse(
        boolean connected,
        String googleEmail,
        List<String> scopes,
        Instant connectedAt
) {

    public static GoogleStatusResponse disconnected() {
        return new GoogleStatusResponse(false, null, null, null);
    }

    public static GoogleStatusResponse connected(String googleEmail, List<String> scopes, Instant connectedAt) {
        return new GoogleStatusResponse(true, googleEmail, scopes, connectedAt);
    }
}
