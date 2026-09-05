package ds.project.orino.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러의 <b>방아쇠</b>. 빈이 아니라 「언제 도는가」만 여기서 켠다.
 *
 * <p><b>테스트에서는 끈다.</b> {@code @EnableScheduling}이 앱 클래스에 붙어 있는 동안에는
 * 통합 테스트에서도 폴러가 살아 돌았다 — 웹푸시 폴러는 30초마다 도는데, 그 틱이 테스트의
 * 준비와 단언 사이에 들어가면 테스트가 부르지도 않은 발송이 일어난다. 실제로 준비 알림
 * 테스트가 그렇게 한 번 빨개졌고(#1327), 30초에 한 번뿐이라 다시 재현되지 않았다.
 *
 * <p>빈까지 없애지는 않는다. 여러 스케줄러가 테스트에서 <b>직접 주입받아 본문을 호출</b>하는
 * 방식으로 검증되고 있어서, 빈이 사라지면 그 테스트들이 컨텍스트 로딩부터 실패한다.
 * 검증해야 할 것은 「시간이 되면 무슨 일이 일어나는가」이지 「타이머가 도는가」가 아니다.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
