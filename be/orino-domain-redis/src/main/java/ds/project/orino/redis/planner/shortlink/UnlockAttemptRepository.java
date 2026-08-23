package ds.project.orino.redis.planner.shortlink;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * 비밀번호 <b>실패</b> 횟수(명세 §10). 슬러그당 분당 10회.
 *
 * <p>성공한 시도는 세지 않는다 — 세션을 만들지 않기로 했으니 아는 사람도 열 때마다 입력하고,
 * 성공까지 세면 그 사람이 스스로 잠긴다.
 *
 * <p>메모리가 아니라 Redis에 두는 이유는 둘이다 — 파드가 늘면 메모리 카운터는 배수로 느슨해지고,
 * 재시작하면 시도 기록이 통째로 사라져 재시작을 반복하는 것만으로 제한을 우회할 수 있다.
 *
 * <p>키는 <b>슬러그 + 분</b>이다. IP를 키로 쓰지 않는다 — 이 모듈은 IP를 저장하지도 세지도
 * 않는다(명세 §8.1). 그 대가로 한 사람이 다른 사람의 시도 창을 소모시킬 수 있지만,
 * 그건 비밀번호를 아는 사람이 1분 뒤 다시 여는 정도의 불편이다.
 */
@Repository
public class UnlockAttemptRepository {

    private static final String KEY_PREFIX = "shortlink:unlock:";
    /** 키 자체가 1분 창이라 TTL도 1분이면 된다. 조금 넉넉히 잡아 경계에서 사라지지 않게 한다. */
    private static final Duration TTL = Duration.ofSeconds(90);

    private final StringRedisTemplate redisTemplate;

    public UnlockAttemptRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 그 분의 실패 횟수. 아직 없으면 0이다. */
    public long count(String slug, long minuteWindow) {
        String value = redisTemplate.opsForValue().get(key(slug, minuteWindow));
        return value == null ? 0L : Long.parseLong(value);
    }

    /**
     * 실패 한 번을 세고 <b>그 분의 누적 횟수</b>를 돌려준다.
     *
     * @param minuteWindow 분 단위 창 식별자(epoch minute)
     */
    public long increment(String slug, long minuteWindow) {
        String key = key(slug, minuteWindow);
        Long count = redisTemplate.opsForValue().increment(key);
        // 첫 시도에만 TTL을 건다. 매번 걸면 창이 계속 밀려 1분이 영원히 끝나지 않는다.
        if (count != null && count == 1L) {
            redisTemplate.expire(key, TTL);
        }
        return count == null ? 1L : count;
    }

    private String key(String slug, long minuteWindow) {
        return KEY_PREFIX + slug + ":" + minuteWindow;
    }
}
