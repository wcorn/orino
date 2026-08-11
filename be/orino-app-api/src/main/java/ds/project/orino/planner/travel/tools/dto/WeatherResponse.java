package ds.project.orino.planner.travel.tools.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 날씨 예보(§S-08).
 *
 * <p>출처·라이선스를 응답에 실어 보낸다 — Open-Meteo는 <b>표기가 필수</b>고, 화면이 빠뜨릴 수
 * 없게 하려면 데이터와 함께 다니는 게 맞다.
 *
 * @param daily  일자별 요약. <b>예보 범위 밖 날짜는 아예 없다</b> — 화면이 "예보 범위 밖"으로 처리한다
 * @param hourly 날짜별 시간대별 예보
 */
public record WeatherResponse(
        String source,
        String license,
        Instant fetchedAt,
        List<DailyWeather> daily,
        Map<LocalDate, List<HourlyWeather>> hourly
) {

    public static final String SOURCE = "Open-Meteo";
    public static final String LICENSE = "CC BY 4.0";

    /** 예보를 못 얻었을 때. 오류가 아니라 "아직 모름"이다 — 화면은 그대로 뜬다. */
    public static WeatherResponse empty(Instant fetchedAt) {
        return new WeatherResponse(SOURCE, LICENSE, fetchedAt, List.of(), Map.of());
    }

    /**
     * @param cityName          (v2.1) <b>그 날짜의 기준 도시</b>. 날짜마다 다른 도시의 예보가
     *                          섞여 오므로, 어느 도시 것인지가 값과 함께 다녀야 한다 —
     *                          화면이 날짜만 보고 짐작하면 도쿄 예보에 "교토"를 붙이게 된다
     * @param precipProbability 강수확률(%). 60% 이상 강조는 <b>화면 규칙</b>이라 여기서 판정하지 않는다
     */
    public record DailyWeather(
            LocalDate date,
            String cityName,
            WeatherIcon icon,
            Integer tempMax,
            Integer tempMin,
            Integer precipProbability
    ) {

        /** 도시를 아는 자리에서 이름을 덧씌운다. 예보 자체는 도시를 모른 채 만들어진다. */
        public DailyWeather in(String city) {
            return new DailyWeather(date, city, icon, tempMax, tempMin, precipProbability);
        }
    }

    /** @param time 여행 타임존의 벽시계 시각("09:00") */
    public record HourlyWeather(String time, WeatherIcon icon, Integer temp) {
    }
}
