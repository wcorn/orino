package ds.project.orino.planner.holiday;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 공휴일 동기화 설정. 한국천문연구원 특일정보 API(공공데이터포털)를 호출한다.
 *
 * @param serviceKey   디코딩 일반 인증키(운영=env/SealedSecret). 비어 있으면 동기화를 건너뛴다.
 * @param baseUrl      특일정보 서비스 base URL
 * @param syncYears    올해부터 동기화할 연도 수(2 = 올해+내년)
 */
@ConfigurationProperties(prefix = "holiday")
public record HolidayProperties(
        String serviceKey,
        String baseUrl,
        int syncYears,
        Duration connectTimeout,
        Duration readTimeout
) {
}
