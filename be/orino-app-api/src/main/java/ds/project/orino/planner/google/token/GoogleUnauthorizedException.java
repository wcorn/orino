package ds.project.orino.planner.google.token;

/**
 * Google API 호출이 401(access token 만료/무효)을 반환했음을 알리는 센티넬.
 *
 * <p>Calendar/Tasks 클라이언트(M1+)가 401 응답에서 던지면, {@link GoogleTokenProvider#executeWithRetry}
 * 가 access token을 1회 강제 갱신한 뒤 재시도한다.
 */
public class GoogleUnauthorizedException extends RuntimeException {

    public GoogleUnauthorizedException() {
        super("Google API responded 401 Unauthorized");
    }
}
