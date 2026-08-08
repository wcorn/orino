package ds.project.orino.planner.travel.tools.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.tools.client.WeatherClient;
import ds.project.orino.planner.travel.tools.config.ToolsProperties;
import ds.project.orino.planner.travel.tools.dto.WeatherResponse;
import ds.project.orino.redis.planner.travel.ToolsCacheRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 여행 기간의 날씨(§S-08).
 *
 * <p>Open-Meteo는 <b>오늘부터 16일</b>만 준다. 여행이 그보다 멀면 아무 날짜도 안 나오는데,
 * 그건 오류가 아니라 정상이다 — 화면이 "예보 범위 밖"으로 처리한다.
 */
@Service
@Transactional(readOnly = true)
public class WeatherService {

    private final TripRepository tripRepository;
    private final WeatherClient weatherClient;
    private final ToolsCacheRepository cacheRepository;
    private final ToolsProperties props;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public WeatherService(TripRepository tripRepository,
                          WeatherClient weatherClient,
                          ToolsCacheRepository cacheRepository,
                          ToolsProperties props,
                          ObjectMapper objectMapper,
                          Clock clock) {
        this.tripRepository = tripRepository;
        this.weatherClient = weatherClient;
        this.cacheRepository = cacheRepository;
        this.props = props;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public WeatherResponse forTrip(Long memberId, Long tripId) {
        Trip trip = tripRepository.findByIdAndMemberId(tripId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_TRIP_NOT_FOUND));
        return forecastOf(trip);
    }

    /**
     * 보드가 날짜 탭에 붙일 요약. 여행 좌표가 없으면(직접 입력한 목적지) 날씨도 없다.
     */
    public Map<LocalDate, WeatherResponse.DailyWeather> dailyByDate(Trip trip) {
        return forecastOf(trip).daily().stream()
                .collect(Collectors.toMap(WeatherResponse.DailyWeather::date, day -> day));
    }

    private WeatherResponse forecastOf(Trip trip) {
        if (trip.getLat() == null || trip.getLng() == null) {
            return WeatherResponse.empty(clock.instant());
        }
        WeatherResponse forecast = cached(trip);
        return clampToTrip(forecast, trip);
    }

    /** 예보 전체에서 <b>여행 기간</b>만 남긴다. 그 밖의 날짜는 화면이 쓸 일이 없다. */
    private static WeatherResponse clampToTrip(WeatherResponse forecast, Trip trip) {
        List<WeatherResponse.DailyWeather> daily = forecast.daily().stream()
                .filter(day -> !day.date().isBefore(trip.getStartDate())
                        && !day.date().isAfter(trip.getEndDate()))
                .toList();
        Map<LocalDate, List<WeatherResponse.HourlyWeather>> hourly = forecast.hourly().entrySet()
                .stream()
                .filter(entry -> !entry.getKey().isBefore(trip.getStartDate())
                        && !entry.getKey().isAfter(trip.getEndDate()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return new WeatherResponse(forecast.source(), forecast.license(),
                forecast.fetchedAt(), daily, hourly);
    }

    /**
     * 좌표·타임존이 키다. 기간은 넣지 않는다 — 어차피 오늘 기준 16일을 받아 오므로
     * 기간이 달라도 같은 응답이고, 넣으면 여행마다 캐시가 갈린다.
     */
    private WeatherResponse cached(Trip trip) {
        String key = "%s,%s:%s".formatted(trip.getLat(), trip.getLng(), trip.getTimezone());
        Optional<String> hit = cacheRepository.findWeather(key);
        if (hit.isPresent()) {
            return objectMapper.readValue(hit.get(), new TypeReference<WeatherResponse>() { });
        }
        Optional<WeatherResponse> fresh =
                weatherClient.forecast(trip.getLat(), trip.getLng(), trip.getTimezone());
        // 실패는 캐시하지 않는다 — 6시간 동안 날씨가 통째로 비어 보인다.
        fresh.ifPresent(response -> cacheRepository.saveWeather(
                key, objectMapper.writeValueAsString(response), props.weatherTtl()));
        return fresh.orElseGet(() -> WeatherResponse.empty(clock.instant()));
    }
}
