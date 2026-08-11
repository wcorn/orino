package ds.project.orino.planner.travel.tools.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.day.service.TripDayService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final TripDayService tripDayService;
    private final WeatherClient weatherClient;
    private final ToolsCacheRepository cacheRepository;
    private final ToolsProperties props;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public WeatherService(TripRepository tripRepository,
                          TripDayService tripDayService,
                          WeatherClient weatherClient,
                          ToolsCacheRepository cacheRepository,
                          ToolsProperties props,
                          ObjectMapper objectMapper,
                          Clock clock) {
        this.tripRepository = tripRepository;
        this.tripDayService = tripDayService;
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
     * 보드가 날짜 탭에 붙일 요약. <b>도시별로 한 번씩 조회해 날짜에 펼친다.</b>
     *
     * <p>날짜마다 조회하면 열흘짜리 여행이 열 번을 부른다. 도시 단위로 묶으면 오사카 3일
     * 여행은 1회, 도쿄 → 닛코 → 도쿄는 2회다(같은 도시는 캐시를 공유한다).
     *
     * <p>기준 도시 좌표가 없으면(직접 입력한 도시) 그 날짜만 날씨가 없다 — 다른 도시 날짜는
     * 멀쩡히 나온다.
     */
    public Map<LocalDate, WeatherResponse.DailyWeather> dailyByDate(
            Trip trip, Map<LocalDate, TravelPlace> cities) {
        Map<LocalDate, WeatherResponse.DailyWeather> byDate = new HashMap<>();
        walkByCity(cities, (date, city, forecast) -> dayOf(forecast, date)
                .ifPresent(day -> byDate.put(date, day.in(cityNameOf(city)))));
        return byDate;
    }

    /**
     * 날짜를 순서대로 훑으며 <b>그날 기준 도시의 예보</b>를 넘긴다. 도시별로 한 번만
     * 조회하도록 여기서 묶는다 — 도쿄 → 닛코 → 도쿄는 2회다.
     */
    private void walkByCity(Map<LocalDate, TravelPlace> cities, DayForecast consumer) {
        Map<Long, WeatherResponse> byCity = new HashMap<>();
        cities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    TravelPlace city = entry.getValue();
                    consumer.accept(entry.getKey(), city,
                            byCity.computeIfAbsent(city.getId(), id -> forecastOf(city)));
                });
    }

    private interface DayForecast {
        void accept(LocalDate date, TravelPlace city, WeatherResponse forecast);
    }

    private static Optional<WeatherResponse.DailyWeather> dayOf(WeatherResponse forecast,
                                                                LocalDate date) {
        return forecast.daily().stream().filter(day -> day.date().equals(date)).findFirst();
    }

    /** 도시 표시명. 도시 장소는 자기 이름이 곧 도시명이라 비어 있으면 이름으로 떨어진다. */
    private static String cityNameOf(TravelPlace city) {
        return city.getCityName() != null ? city.getCityName() : city.getName();
    }

    /** 그 도시의 예보. 좌표가 없으면 조회 자체를 하지 않는다. */
    private WeatherResponse forecastOf(TravelPlace city) {
        if (city.getLat() == null || city.getLng() == null) {
            return WeatherResponse.empty(clock.instant());
        }
        return cached(city);
    }

    /**
     * 도구 화면(§S-08)이 쓰는 여행 단위 예보. <b>날짜마다 그날 기준 도시로 본다</b>(v2.1 §3.7).
     *
     * <p>첫날 도시 하나로 보면 다구간 여행에서 교토 날짜에 도쿄 날씨가 뜬다 — 같은 나라
     * 안에서도 산간·해안은 몇 도씩 갈린다. 날짜 목록이 곧 기간이라 따로 잘라낼 것도 없다.
     */
    private WeatherResponse forecastOf(Trip trip) {
        List<WeatherResponse.DailyWeather> daily = new ArrayList<>();
        Map<LocalDate, List<WeatherResponse.HourlyWeather>> hourly = new HashMap<>();

        walkByCity(tripDayService.baseCitiesOf(trip.getId()), (date, city, forecast) -> {
            dayOf(forecast, date).ifPresent(day -> daily.add(day.in(cityNameOf(city))));
            List<WeatherResponse.HourlyWeather> hours = forecast.hourly().get(date);
            if (hours != null) {
                hourly.put(date, hours);
            }
        });
        return new WeatherResponse(WeatherResponse.SOURCE, WeatherResponse.LICENSE,
                clock.instant(), daily, hourly);
    }

    /**
     * 좌표·타임존이 키다. 기간은 넣지 않는다 — 어차피 오늘 기준 16일을 받아 오므로
     * 기간이 달라도 같은 응답이고, 넣으면 여행마다 캐시가 갈린다. <b>키가 도시에서 나오므로
     * 같은 도시를 오가는 여행(도쿄 → 닛코 → 도쿄)은 캐시를 공유한다.</b>
     */
    private WeatherResponse cached(TravelPlace city) {
        String key = "%s,%s:%s".formatted(city.getLat(), city.getLng(), city.getTimezone());
        Optional<String> hit = cacheRepository.findWeather(key);
        if (hit.isPresent()) {
            return objectMapper.readValue(hit.get(), new TypeReference<WeatherResponse>() { });
        }
        Optional<WeatherResponse> fresh =
                weatherClient.forecast(city.getLat(), city.getLng(), city.getTimezone());
        // 실패는 캐시하지 않는다 — 6시간 동안 날씨가 통째로 비어 보인다.
        fresh.ifPresent(response -> cacheRepository.saveWeather(
                key, objectMapper.writeValueAsString(response), props.weatherTtl()));
        return fresh.orElseGet(() -> WeatherResponse.empty(clock.instant()));
    }
}
