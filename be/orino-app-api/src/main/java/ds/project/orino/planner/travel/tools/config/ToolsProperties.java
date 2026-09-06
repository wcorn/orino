package ds.project.orino.planner.travel.tools.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 도구(날씨·환율) 설정.
 *
 * <p>둘 다 무료·무인증이라 키가 없다. 그래도 캐시를 두는 이유는 §4.7 —
 * 남의 무료 서비스를 필요 이상으로 두드리지 않는 것도 예의다.
 *
 * @param weatherTtl          예보 캐시가 <b>남아 있는</b> 기간. {@link #weatherFreshFor}보다
 *                            길게 잡는다 — 신선하지 않아도 <b>버리지 않고</b> 즉시 주면서
 *                            뒤에서 갱신하기 때문이다(#1357). 버리면 그 순간 콜드가 된다
 * @param weatherFreshFor     예보를 <b>다시 안 물어도 되는</b> 기간(§4.7 — 6시간). 이보다
 *                            오래된 값은 그대로 주되 갱신을 걸어 둔다
 * @param weatherBoardTimeout 보드가 날씨를 기다리는 <b>최대</b> 시간. 캐시가 아예 없을 때만
 *                            쓰인다 — 넘기면 날씨 없이 보드를 준다. 날씨는 부가 정보고, 받아
 *                            온 값은 캐시에 들어가므로 다음 열람은 따뜻하다
 * @param fxTtl               환율 캐시(§4.7 — 24시간). ECB는 하루 한 번 고시한다
 */
@ConfigurationProperties(prefix = "travel.tools")
public record ToolsProperties(
        String weatherBaseUrl,
        String fxUrl,
        Duration weatherTtl,
        Duration weatherFreshFor,
        Duration weatherBoardTimeout,
        Duration fxTtl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
