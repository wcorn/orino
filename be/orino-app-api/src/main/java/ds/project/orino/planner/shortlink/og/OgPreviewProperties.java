package ds.project.orino.planner.shortlink.og;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * OG 프리뷰 fetch 설정(아키텍처 §5).
 *
 * <p>기본값은 전부 <b>작다</b>. 이 코드는 사용자가 입력한 임의 URL을 우리 클러스터 안에서
 * 여는 유일한 경로라, 넉넉하게 잡을 이유가 없다.
 *
 * @param timeout       한 요청 전체 시한. 프리뷰는 확인용이고 발급을 막지 않으므로 짧게 끊는다
 * @param maxBodyBytes  본문 상한. 넘으면 거기서 끊고 읽은 데까지만 파싱한다
 * @param maxRedirects  리다이렉트 홉 수. <b>매 홉마다 검사를 다시 한다</b>
 * @param enabled       발급 뒤 프리뷰를 실제로 긁을지. <b>끄면 외부로 나가는 요청이 0이 된다</b> —
 *                      테스트는 이 값을 꺼서 망을 타지 않고, 운영에서 문제가 생기면 여기서 끈다
 * @param blockedCidrs  사설·루프백 등 기본 차단에 <b>더해서</b> 막을 대역.
 *                      k3s 파드({@code 10.244.0.0/16})·서비스({@code 10.96.0.0/12})가 여기 들어간다.
 *                      둘 다 10/8 안이라 이미 막히지만, 무엇을 막고 있는지 설정에서 보이게 둔다
 */
@ConfigurationProperties(prefix = "shortlink.og-preview")
public record OgPreviewProperties(
        boolean enabled,
        Duration timeout,
        long maxBodyBytes,
        int maxRedirects,
        List<String> blockedCidrs
) {

    public OgPreviewProperties {
        timeout = timeout == null ? Duration.ofSeconds(3) : timeout;
        maxBodyBytes = maxBodyBytes <= 0 ? 1024 * 1024 : maxBodyBytes;
        maxRedirects = maxRedirects <= 0 ? 3 : maxRedirects;
        blockedCidrs = blockedCidrs == null ? List.of() : List.copyOf(blockedCidrs);
    }
}
