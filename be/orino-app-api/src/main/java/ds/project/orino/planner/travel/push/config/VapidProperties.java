package ds.project.orino.planner.travel.push.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * VAPID 설정.
 *
 * <p>공개키는 브라우저({@code pushManager.subscribe})에 그대로 나가므로 <b>비밀이 아니다</b>.
 * 비밀은 개인키뿐이라 그것만 SealedSecret에 있다.
 *
 * <p>키가 없으면 알림 기능만 꺼지고 앱은 정상 기동한다 — 로컬에서 키 없이 나머지를 개발할 수
 * 있어야 한다.
 *
 * @param publicKey  65바이트 비압축 점(base64url)
 * @param privateKey 32바이트 스칼라(base64url)
 * @param subject    푸시 서비스가 문제 시 연락할 곳(RFC 8292 — mailto: 또는 https)
 */
@ConfigurationProperties(prefix = "travel.vapid")
public record VapidProperties(String publicKey, String privateKey, String subject) {

    public boolean enabled() {
        return publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank();
    }
}
