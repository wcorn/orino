package ds.project.orino.planner.shortlink.og;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * SSRF 방어(아키텍처 §5 · 결정 기록 D-11). <b>이 모듈에서 가장 위험한 코드의 관문이다.</b>
 *
 * <p>BE는 note1/note2 클러스터 안에서 MySQL·Redis·MinIO와 같은 망에 있다. 방어 없이 임의 URL을
 * fetch하면 그 순간 <b>내부망 스캐너</b>가 된다.
 *
 * <p>핵심은 <b>호스트명이 아니라 해석된 IP로 판정</b>하고, <b>그 IP로 직접 연결</b>하는 것이다.
 * 문자열만 보면 {@code http://internal.example.com}이 사설 IP로 해석되는 것을 못 잡고,
 * 검사한 뒤 다시 해석하면 그 사이에 답이 바뀌는 DNS 재바인딩을 못 막는다.
 * 그래서 이 클래스는 <b>검사에 통과한 주소 목록을 그대로 돌려주고</b>, 연결은 그 목록으로만 한다
 * ({@link OgPreviewClient}의 {@code Dns}).
 *
 * <p><b>실패 사유를 밖으로 내보내지 않는다.</b> "연결 거부"와 "타임아웃"을 구분해 주면
 * 그게 곧 내부망 포트 스캐너다 — 호출자는 통과/차단만 안다.
 */
@Component
public class SsrfGuard {

    /** CGNAT. {@code isSiteLocalAddress()}가 잡지 않는다. */
    private static final byte CGNAT_FIRST_OCTET = 100;
    private static final int CGNAT_SECOND_MIN = 64;
    private static final int CGNAT_SECOND_MAX = 127;

    private final List<Cidr> blockedCidrs;
    private final boolean allowLoopback;

    /**
     * <b>운영에서 쓰는 유일한 입구다 — 루프백은 언제나 막힌다.</b>
     *
     * <p>{@code @Autowired}가 붙은 이유는 아래에 생성자가 하나 더 있기 때문이다. 선언된
     * 생성자가 둘이면 스프링은 어느 쪽인지 묻지 않고 기본 생성자를 찾다가 실패한다.
     */
    @Autowired
    public SsrfGuard(OgPreviewProperties properties) {
        this(properties, false);
    }

    private SsrfGuard(OgPreviewProperties properties, boolean allowLoopback) {
        this.blockedCidrs = properties.blockedCidrs().stream().map(Cidr::parse).toList();
        this.allowLoopback = allowLoopback;
    }

    /**
     * 테스트 전용. 로컬 목 서버에 붙어야 리다이렉트·본문 상한을 진짜 HTTP로 확인할 수 있다.
     * <b>루프백 한 겹만 연다</b> — 사설·링크로컬은 그대로 막힌 채다.
     */
    static SsrfGuard allowingLoopback(OgPreviewProperties properties) {
        return new SsrfGuard(properties, true);
    }

    /**
     * 호스트를 해석하고 <b>전부</b> 안전한지 본다.
     *
     * <p>하나라도 막힌 대역이면 통째로 거절한다 — 안전한 IP 하나가 섞여 있다고 통과시키면,
     * 공격자는 안전한 A 레코드를 하나 끼워 넣는 것만으로 우회할 수 있다.
     *
     * @return 검사를 통과한 주소들. 연결은 <b>이 목록으로만</b> 한다
     * @throws BlockedHostException 해석 실패 · 막힌 대역 · 빈 결과
     */
    public List<InetAddress> resolveSafely(String host) {
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new BlockedHostException();
        }
        if (resolved.length == 0) {
            throw new BlockedHostException();
        }
        List<InetAddress> safe = new ArrayList<>(resolved.length);
        for (InetAddress address : resolved) {
            if (isBlocked(address)) {
                throw new BlockedHostException();
            }
            safe.add(address);
        }
        return List.copyOf(safe);
    }

    boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isMulticastAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            // 링크로컬에 169.254.169.254(클라우드 메타데이터)가 포함된다.
            return true;
        }
        if (address.isLoopbackAddress()) {
            return !allowLoopback;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return isCgnat(bytes) || matchesBlockedCidr(bytes);
        }
        if (address instanceof Inet6Address ipv6) {
            // fc00::/7 — IPv6 유니크 로컬. isSiteLocalAddress()는 폐기된 fec0::/10만 본다.
            if ((bytes[0] & 0xFE) == 0xFC) {
                return true;
            }
            byte[] embedded = embeddedIpv4(ipv6, bytes);
            if (embedded != null) {
                // ::ffff:10.0.0.1 · 2002:0a00::/16(6to4) 같은 우회를 IPv4로 되돌려 다시 본다.
                try {
                    return isBlocked(InetAddress.getByAddress(embedded));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }
        return matchesBlockedCidr(bytes);
    }

    /**
     * IPv6 안에 숨은 IPv4를 꺼낸다 — IPv4 매핑/호환 주소와 6to4({@code 2002::/16}).
     * Teredo({@code 2001:0::/32})는 안쪽 IPv4가 비트 반전으로 들어 있어 여기서 다루지 않는다.
     */
    private byte[] embeddedIpv4(Inet6Address address, byte[] bytes) {
        if (address.isIPv4CompatibleAddress()) {
            return new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
        }
        if (bytes[0] == 0x20 && bytes[1] == 0x02) {
            return new byte[]{bytes[2], bytes[3], bytes[4], bytes[5]};
        }
        return null;
    }

    private boolean isCgnat(byte[] bytes) {
        int second = bytes[1] & 0xFF;
        return bytes[0] == CGNAT_FIRST_OCTET
                && second >= CGNAT_SECOND_MIN && second <= CGNAT_SECOND_MAX;
    }

    private boolean matchesBlockedCidr(byte[] bytes) {
        for (Cidr cidr : blockedCidrs) {
            if (cidr.contains(bytes)) {
                return true;
            }
        }
        return false;
    }

    /** 막힌 주소. <b>사유를 담지 않는다</b> — 호출자가 밖으로 흘릴 것이 없어야 한다. */
    public static class BlockedHostException extends RuntimeException {
        public BlockedHostException() {
            super("blocked", null, false, false);
        }
    }

    /** 설정으로 받은 추가 차단 대역. */
    private record Cidr(byte[] network, int prefixBits) {

        static Cidr parse(String value) {
            String[] parts = value.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("CIDR 형식이 아닙니다: " + value);
            }
            try {
                return new Cidr(InetAddress.getByName(parts[0]).getAddress(),
                        Integer.parseInt(parts[1]));
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("CIDR 형식이 아닙니다: " + value, e);
            }
        }

        boolean contains(byte[] address) {
            if (address.length != network.length) {
                return false;
            }
            int fullBytes = prefixBits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixBits % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
