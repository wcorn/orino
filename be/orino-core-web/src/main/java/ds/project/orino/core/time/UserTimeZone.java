package ds.project.orino.core.time;

import java.time.ZoneId;

/**
 * 요청 단위 사용자 시간대를 보관하는 ThreadLocal 컨텍스트.
 * <p>
 * 저장은 UTC(Instant)로 하되, 복습 스케줄의 "새벽 4시 롤오버" 같은 사용자 로컬
 * 기준 계산과 응답 직렬화(offset 포함)를 위해 요청의 시간대를 전파한다.
 * {@link UserTimeZoneInterceptor}가 {@code X-Timezone} 헤더로부터 설정한다.
 */
public final class UserTimeZone {

    /** 헤더가 없거나 유효하지 않을 때 사용할 기본 시간대. */
    public static final ZoneId DEFAULT = ZoneId.of("Asia/Seoul");

    private static final ThreadLocal<ZoneId> HOLDER = new ThreadLocal<>();

    private UserTimeZone() {
    }

    public static void set(ZoneId zoneId) {
        HOLDER.set(zoneId);
    }

    public static ZoneId get() {
        ZoneId zoneId = HOLDER.get();
        return zoneId != null ? zoneId : DEFAULT;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
