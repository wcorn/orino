package ds.project.orino.planner.shortlink.og;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SSRF 방어(아키텍처 §5 · D-11).
 *
 * <p>여기서 하나라도 새면 BE가 <b>내부망 스캐너</b>가 된다. 그래서 막아야 할 대역을
 * 하나씩 이름으로 적어 고정한다 — 목록이 줄어드는 변경이 눈에 띄게.
 */
class SsrfGuardTest {

    private final SsrfGuard guard = new SsrfGuard(new OgPreviewProperties(
            true, Duration.ofSeconds(3), 1024, 3, List.of("10.244.0.0/16", "10.96.0.0/12")));

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1",        // 루프백 — MySQL·Redis가 여기 있다
            "0.0.0.0",          // 와일드카드
            "10.0.0.5",         // 사설 A
            "172.16.0.5",       // 사설 B
            "192.168.0.240",    // 사설 C — MetalLB VIP가 여기다
            "169.254.169.254",  // 링크로컬 — 클라우드 메타데이터
            "100.64.0.1",       // CGNAT
            "224.0.0.1",        // 멀티캐스트
            "10.244.1.7",       // k3s 파드
            "10.96.0.1"         // k3s 서비스
    })
    @DisplayName("사설·루프백·링크로컬·CGNAT·멀티캐스트·k3s 대역은 막는다")
    void blocksInternalIpv4(String ip) throws UnknownHostException {
        assertThat(guard.isBlocked(InetAddress.getByName(ip))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "::1",                  // IPv6 루프백
            "fe80::1",              // IPv6 링크로컬
            "fc00::1",              // IPv6 유니크 로컬
            "fd12:3456::1",         // 〃
            "::ffff:10.0.0.5",      // IPv4 매핑으로 감싼 사설 주소
            "2002:0a00:0005::1"     // 6to4로 감싼 10.0.0.5
    })
    @DisplayName("IPv6로 감싸도 뚫리지 않는다 — 안쪽 IPv4를 꺼내 다시 본다")
    void blocksInternalIpv6(String ip) throws UnknownHostException {
        assertThat(guard.isBlocked(InetAddress.getByName(ip))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "2001:4860:4860::8888"})
    @DisplayName("공개 주소는 통과시킨다")
    void allowsPublicAddresses(String ip) throws UnknownHostException {
        assertThat(guard.isBlocked(InetAddress.getByName(ip))).isFalse();
    }

    @Test
    @DisplayName("사설 IP로 해석되는 호스트는 이름이 멀쩡해도 막는다")
    void blocksHostResolvingToPrivateAddress() {
        // localhost는 이름만 보면 아무 문제가 없다. 해석 결과가 루프백이라 막힌다.
        assertThatThrownBy(() -> guard.resolveSafely("localhost"))
                .isInstanceOf(SsrfGuard.BlockedHostException.class);
    }

    @Test
    @DisplayName("해석되지 않는 호스트도 막는다")
    void blocksUnresolvableHost() {
        assertThatThrownBy(() -> guard.resolveSafely("no-such-host.invalid"))
                .isInstanceOf(SsrfGuard.BlockedHostException.class);
    }

    @Test
    @DisplayName("막힌 주소에는 사유가 실리지 않는다 — 응답으로 흘러갈 것이 없어야 한다")
    void doesNotCarryReason() {
        SsrfGuard.BlockedHostException exception = new SsrfGuard.BlockedHostException();

        assertThat(exception.getMessage()).isEqualTo("blocked");
        assertThat(exception.getCause()).isNull();
        // 스택트레이스도 담지 않는다(로그에 호스트가 남지 않게).
        assertThat(exception.getStackTrace()).isEmpty();
    }

    @Test
    @DisplayName("루프백 허용은 테스트 전용 생성자에만 있다")
    void loopbackAllowedOnlyInTestConstructor() throws UnknownHostException {
        SsrfGuard permissive = SsrfGuard.allowingLoopback(
                new OgPreviewProperties(true, Duration.ofSeconds(3), 1024, 3, List.of()));

        assertThat(permissive.isBlocked(InetAddress.getByName("127.0.0.1"))).isFalse();
        // 그래도 사설·링크로컬은 그대로 막힌다.
        assertThat(permissive.isBlocked(InetAddress.getByName("169.254.169.254"))).isTrue();
        assertThat(permissive.isBlocked(InetAddress.getByName("10.0.0.5"))).isTrue();
    }
}
