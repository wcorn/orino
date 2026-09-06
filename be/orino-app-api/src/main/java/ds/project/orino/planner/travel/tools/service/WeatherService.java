package ds.project.orino.planner.travel.tools.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.day.service.TripDayService;
import ds.project.orino.planner.travel.metrics.ExternalApiMetrics;
import ds.project.orino.planner.travel.tools.client.WeatherClient;
import ds.project.orino.planner.travel.tools.config.ToolsProperties;
import ds.project.orino.planner.travel.tools.dto.WeatherResponse;
import ds.project.orino.redis.planner.travel.ToolsCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 여행 기간의 날씨(§S-08).
 *
 * <p>Open-Meteo는 <b>오늘부터 16일</b>만 준다. 여행이 그보다 멀면 아무 날짜도 안 나오는데,
 * 그건 오류가 아니라 정상이다 — 화면이 "예보 범위 밖"으로 처리한다.
 *
 * <p><b>보드는 날씨를 기다리지 않는다</b>(#1357). 예전에는 캐시가 비면 도시마다 순서대로
 * 외부를 부르느라 보드 응답이 1~2초였다. 지금은 세 갈래다.
 *
 * <ul>
 *   <li><b>신선하면</b>({@code weatherFreshFor} 안) 그대로 준다 — 외부 호출 0
 *   <li><b>만료됐으면 그대로 주고 뒤에서 갱신한다.</b> 6시간 지난 예보는 여전히 쓸모가 있고,
 *       「어제 예보를 잠깐 보는 것」이 「1초 기다리는 것」보다 낫다
 *   <li><b>아예 없으면</b> {@code weatherBoardTimeout}까지만 기다린다. 넘기면 날씨 없이
 *       보드를 주고, 받아 온 값은 캐시에 들어가 다음 열람이 따뜻하다
 * </ul>
 *
 * <p>도구 화면({@link #forTrip})은 마감시한을 걸지 않는다 — 거기서는 날씨가 부가 정보가
 * 아니라 본문이라, 빈 화면을 주느니 기다리는 편이 맞다.
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
    private final ExternalApiMetrics metrics;
    /** 도구 화면이 쓰는 「기다린다」. 실질적인 상한은 클라이언트의 read-timeout이다. */
    private static final Duration NO_DEADLINE = Duration.ofDays(1);

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final ExecutorService weatherExecutor;
    /**
     * 지금 갱신 중인 캐시 키. 보드를 연달아 열면 같은 도시에 갱신이 몇 번씩 걸린다 —
     * 하나만 나가게 막는다.
     *
     * <p>인스턴스 하나 안에서만 유효하다. 여러 대로 늘면 대수만큼 나갈 수 있는데, 6시간에
     * 한 번 도시당 몇 번이라 그때 가서도 문제가 아니다. 분산 락을 쓰면 그 락이 다시
     * 요청 경로 위의 외부 의존이 된다.
     */
    private final Set<String> refreshing = ConcurrentHashMap.newKeySet();

    public WeatherService(TripRepository tripRepository,
                          TripDayService tripDayService,
                          WeatherClient weatherClient,
                          ToolsCacheRepository cacheRepository,
                          ToolsProperties props,
                          ObjectMapper objectMapper,
                          Clock clock,
                          ExternalApiMetrics metrics,
                          ExecutorService weatherExecutor) {
        this.tripRepository = tripRepository;
        this.tripDayService = tripDayService;
        this.weatherClient = weatherClient;
        this.cacheRepository = cacheRepository;
        this.props = props;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.metrics = metrics;
        this.weatherExecutor = weatherExecutor;
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
     * <p><b>도시가 바뀌는 날은 두 도시를 준다</b>(D-25) — 오전을 보낸 도시의 날씨도 알아야
     * 아침에 뭘 입을지 정한다. 떠나온 도시는 <b>반드시 다른 날짜의 기준 도시</b>이므로 예보를
     * 이미 읽어 둔 상태고, 그래서 이 값 때문에 조회가 늘지 않는다.
     *
     * <p>기준 도시 좌표가 없으면(직접 입력한 도시) 그 날짜만 날씨가 없다 — 다른 도시 날짜는
     * 멀쩡히 나온다.
     *
     * @param departedCities 도시가 바뀌는 날 → 떠나온 도시. 그 외 날짜는 키가 없다
     */
    public DailyForecasts dailyByDate(Map<LocalDate, TravelPlace> cities,
                                      Map<LocalDate, TravelPlace> departedCities) {
        // 두 갈래가 같은 조회 결과를 나눠 쓴다 — 따로 돌면 떠나온 도시를 한 번 더 읽는다.
        // 도시를 먼저 전부 모아 한 번에 채운다. 여기서 마감시한이 걸린다(#1357).
        Map<Long, WeatherResponse> byCity =
                prefetch(cities, departedCities, props.weatherBoardTimeout());
        return new DailyForecasts(dayByDate(cities, byCity),
                dayByDate(departedCities, byCity));
    }

    /**
     * 이 화면이 쓸 도시들의 예보를 <b>한 번에</b> 채운다.
     *
     * <p>신선한 것과 만료된 것은 캐시에서 곧바로 나온다(만료는 갱신을 걸어 두고 옛 값을
     * 쓴다). <b>아예 없는 도시만</b> 외부로 나가고, 그 기다림에 마감시한이 걸린다 —
     * 도시마다 걸면 6도시 여행이 6배가 되므로 <b>전체에 하나</b>다.
     *
     * <p>마감시한을 넘긴 도시는 그냥 빠진다. 그 도시 날짜만 날씨가 없고 보드는 그대로 뜬다.
     * 던져 둔 조회는 계속 돌아 캐시를 채우므로 다음 열람은 따뜻하다.
     */
    private Map<Long, WeatherResponse> prefetch(Map<LocalDate, TravelPlace> cities,
                                                Map<LocalDate, TravelPlace> departedCities,
                                                Duration deadline) {
        Map<Long, TravelPlace> distinct = new LinkedHashMap<>();
        cities.values().forEach(city -> distinct.putIfAbsent(city.getId(), city));
        departedCities.values().forEach(city -> distinct.putIfAbsent(city.getId(), city));

        Map<Long, WeatherResponse> byCity = new HashMap<>();
        Map<Long, Future<Optional<WeatherResponse>>> pending = new LinkedHashMap<>();
        for (TravelPlace city : distinct.values()) {
            if (!hasCoordinates(city)) {
                continue;
            }
            String key = keyOf(city);
            Optional<WeatherResponse> cachedValue = readCache(key);
            if (cachedValue.isPresent()) {
                byCity.put(city.getId(), useOrRefresh(key, city, cachedValue.get()));
                continue;
            }
            // 캐시가 아예 없는 도시만 외부로 나간다. 전부 동시에 던진다.
            pending.put(city.getId(), weatherExecutor.submit(() -> fetchAndCache(key, city)));
        }
        return awaitPending(byCity, pending, deadline);
    }

    /**
     * 던져 둔 조회를 <b>전체 마감시한 안에서</b> 거둔다. 못 거둔 도시는 그냥 빠진다 —
     * 조회 자체는 계속 돌아 캐시를 채운다.
     */
    private static Map<Long, WeatherResponse> awaitPending(
            Map<Long, WeatherResponse> byCity,
            Map<Long, Future<Optional<WeatherResponse>>> pending, Duration deadline) {
        long remaining = deadline.toNanos();
        for (Map.Entry<Long, Future<Optional<WeatherResponse>>> entry : pending.entrySet()) {
            long started = System.nanoTime();
            try {
                entry.getValue().get(Math.max(remaining, 0), TimeUnit.NANOSECONDS)
                        .ifPresent(response -> byCity.put(entry.getKey(), response));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return byCity;
            } catch (Exception e) {
                // 시간이 됐거나 조회가 실패했다. 둘 다 "이 도시는 날씨 없이 간다"로 같다.
                log.debug("예보를 제때 못 받았다: {}", e.toString());
            }
            remaining -= System.nanoTime() - started;
        }
        return byCity;
    }

    /**
     * 캐시에 있던 값을 쓰되, <b>신선도가 지났으면 갱신을 걸어 둔다.</b>
     *
     * <p>기다리지 않는다는 것이 요점이다. 6시간 지난 예보는 여전히 쓸모가 있고, 여기서
     * 기다리면 그게 예전에 보드를 1초 붙잡던 바로 그 자리다.
     */
    private WeatherResponse useOrRefresh(String key, TravelPlace city, WeatherResponse cached) {
        // 신선하든 아니든 이 요청은 캐시가 답했다 — 나간 호출이 없으므로 hit이다.
        metrics.record(ExternalApiMetrics.Api.WEATHER, ExternalApiMetrics.Result.HIT);
        if (isFresh(cached)) {
            return cached;
        }
        if (refreshing.add(key)) {
            weatherExecutor.submit(() -> {
                try {
                    fetchAndCache(key, city);
                } finally {
                    refreshing.remove(key);
                }
            });
        }
        return cached;
    }

    /** {@code fetchedAt} 이후 {@code weatherFreshFor}가 안 지났으면 다시 안 물어도 된다. */
    private boolean isFresh(WeatherResponse cached) {
        Instant fetchedAt = cached.fetchedAt();
        if (fetchedAt == null) {
            return false;
        }
        return Duration.between(fetchedAt, clock.instant())
                .compareTo(props.weatherFreshFor()) < 0;
    }

    private Optional<WeatherResponse> readCache(String key) {
        return cacheRepository.findWeather(key).map(
                json -> objectMapper.readValue(json, new TypeReference<WeatherResponse>() { }));
    }

    /** 외부로 나가 받아 오고 캐시에 넣는다. 실패는 캐시하지 않는다. */
    private Optional<WeatherResponse> fetchAndCache(String key, TravelPlace city) {
        Optional<WeatherResponse> fresh =
                weatherClient.forecast(city.getLat(), city.getLng(), city.getTimezone());
        metrics.recordFetch(ExternalApiMetrics.Api.WEATHER, fresh.isPresent());
        // 실패는 캐시하지 않는다 — TTL 동안 날씨가 통째로 비어 보인다.
        fresh.ifPresent(response -> cacheRepository.saveWeather(
                key, objectMapper.writeValueAsString(response), props.weatherTtl()));
        return fresh;
    }

    private static boolean hasCoordinates(TravelPlace city) {
        return city.getLat() != null && city.getLng() != null;
    }

    /**
     * 좌표·타임존이 키다. 기간은 넣지 않는다 — 어차피 오늘 기준 16일을 받아 오므로
     * 기간이 달라도 같은 응답이고, 넣으면 여행마다 캐시가 갈린다. <b>키가 도시에서 나오므로
     * 같은 도시를 오가는 여행(도쿄 → 닛코 → 도쿄)은 캐시를 공유한다.</b>
     */
    private static String keyOf(TravelPlace city) {
        return "%s,%s:%s".formatted(city.getLat(), city.getLng(), city.getTimezone());
    }

    /**
     * 날짜를 순서대로 훑으며 그날 그 도시의 예보를 뽑는다. 도시별로 한 번만 조회하도록
     * {@code byCity}에 묶어 둔다 — 도쿄 → 닛코 → 도쿄는 2회다.
     */
    private Map<LocalDate, WeatherResponse.DailyWeather> dayByDate(
            Map<LocalDate, TravelPlace> cities, Map<Long, WeatherResponse> byCity) {
        Map<LocalDate, WeatherResponse.DailyWeather> byDate = new HashMap<>();
        cities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> dayIn(entry.getKey(), entry.getValue(), byCity)
                        .ifPresent(day -> byDate.put(entry.getKey(), day)));
        return byDate;
    }

    /**
     * 그날 그 도시의 예보 한 줄. 조회는 {@link #prefetch}가 이미 끝냈고 여기서는 <b>채워 둔
     * 지도를 읽기만</b> 한다 — 여기서 조회하면 날짜 수만큼 외부로 나간다.
     */
    private Optional<WeatherResponse.DailyWeather> dayIn(LocalDate date, TravelPlace city,
                                                         Map<Long, WeatherResponse> byCity) {
        WeatherResponse forecast = byCity.get(city.getId());
        if (forecast == null) {
            // 좌표가 없거나 마감시한을 넘긴 도시다. 그 날짜만 날씨가 비고 화면은 그대로 뜬다.
            return Optional.empty();
        }
        return dayOf(forecast, date).map(day -> day.in(cityNameOf(city)));
    }

    /**
     * 날짜 탭의 날씨.
     *
     * @param arrived  그날 기준 도시(도시가 바뀌는 날이면 도착한 쪽)의 날씨
     * @param departed 도시가 바뀌는 날의 <b>떠나온 도시</b> 날씨. 그 외 날짜는 키가 없다
     */
    public record DailyForecasts(
            Map<LocalDate, WeatherResponse.DailyWeather> arrived,
            Map<LocalDate, WeatherResponse.DailyWeather> departed
    ) {
    }

    private static Optional<WeatherResponse.DailyWeather> dayOf(WeatherResponse forecast,
                                                                LocalDate date) {
        return forecast.daily().stream().filter(day -> day.date().equals(date)).findFirst();
    }

    /** 도시 표시명. 도시 장소는 자기 이름이 곧 도시명이라 비어 있으면 이름으로 떨어진다. */
    private static String cityNameOf(TravelPlace city) {
        return city.getCityName() != null ? city.getCityName() : city.getName();
    }

    /**
     * 도구 화면(§S-08)이 쓰는 여행 단위 예보. <b>날짜마다 그날 기준 도시로 본다</b>(v2.1 §3.7).
     *
     * <p>첫날 도시 하나로 보면 다구간 여행에서 교토 날짜에 도쿄 날씨가 뜬다 — 같은 나라
     * 안에서도 산간·해안은 몇 도씩 갈린다. 날짜 목록이 곧 기간이라 따로 잘라낼 것도 없다.
     *
     * <p><b>도시가 바뀌는 날은 줄이 둘이다</b>(D-25) — 떠나온 도시가 먼저, 도착한 도시가
     * 다음이다. 화면의 날씨 행에 도시명 열이 있어 두 줄이 그대로 읽힌다. 시간대별 예보는
     * 그날의 기준 도시(도착한 쪽) 하나만 둔다 — 하루에 시계가 둘일 수는 없다.
     */
    private WeatherResponse forecastOf(Trip trip) {
        Map<LocalDate, TravelPlace> cities = tripDayService.baseCitiesOf(trip.getId());
        Map<LocalDate, TravelPlace> departed = tripDayService.departedCitiesOf(trip.getId());
        List<WeatherResponse.DailyWeather> daily = new ArrayList<>();
        Map<LocalDate, List<WeatherResponse.HourlyWeather>> hourly = new HashMap<>();
        // 도구 화면은 마감시한을 걸지 않는다 — 여기서는 날씨가 부가 정보가 아니라 본문이라,
        // 빈 화면을 주느니 기다리는 편이 맞다. 도시별 동시 조회는 그대로 받는다.
        Map<Long, WeatherResponse> byCity = prefetch(cities, departed, NO_DEADLINE);

        cities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    LocalDate date = entry.getKey();
                    TravelPlace city = entry.getValue();
                    TravelPlace from = departed.get(date);
                    if (from != null) {
                        dayIn(date, from, byCity).ifPresent(daily::add);
                    }
                    dayIn(date, city, byCity).ifPresent(daily::add);
                    WeatherResponse forecast = byCity.get(city.getId());
                    List<WeatherResponse.HourlyWeather> hours =
                            forecast == null ? null : forecast.hourly().get(date);
                    if (hours != null) {
                        hourly.put(date, hours);
                    }
                });
        return new WeatherResponse(WeatherResponse.SOURCE, WeatherResponse.LICENSE,
                clock.instant(), daily, hourly);
    }

}
