package ds.project.orino.planner.google.calendar;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.google.repository.GoogleAccountRepository;
import ds.project.orino.planner.google.calendar.dto.GoogleEventsView;
import ds.project.orino.planner.google.calendar.dto.PlannerEvent;
import ds.project.orino.planner.google.client.GoogleCalendarClient;
import ds.project.orino.redis.planner.google.GoogleCalendarCacheRepository;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * 일정 조회: 미연동 처리 + 단기 캐시(~60초) + 라이브 프록시. 통합 피드(#479)가 이 결과를 합류시킨다.
 *
 * <p>Google이 유일한 진실이라 로컬 미러를 두지 않고, 뷰 전환의 중복 호출만 Redis로 흡수한다.
 */
@Service
public class GoogleEventQueryService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final TypeReference<List<PlannerEvent>> EVENT_LIST = new TypeReference<>() {
    };

    private final GoogleAccountRepository accountRepository;
    private final GoogleCalendarClient calendarClient;
    private final GoogleCalendarCacheRepository cacheRepository;
    private final ObjectMapper objectMapper;

    public GoogleEventQueryService(GoogleAccountRepository accountRepository,
                                   GoogleCalendarClient calendarClient,
                                   GoogleCalendarCacheRepository cacheRepository,
                                   ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.calendarClient = calendarClient;
        this.cacheRepository = cacheRepository;
        this.objectMapper = objectMapper;
    }

    /** [from, to] 날짜 구간의 일정을 조회한다. 미연동이면 connected=false + 빈 목록. */
    public GoogleEventsView getEvents(Long memberId, LocalDate from, LocalDate to, ZoneId zone) {
        if (!isConnected(memberId)) {
            return GoogleEventsView.notConnected();
        }

        Optional<String> cached = cacheRepository.find(memberId, from.toString(), to.toString());
        if (cached.isPresent()) {
            return GoogleEventsView.connected(deserialize(cached.get()));
        }

        Instant timeMin = from.atStartOfDay(zone).toInstant();
        Instant timeMax = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<PlannerEvent> events = calendarClient.listEvents(memberId, timeMin, timeMax, zone);

        cacheRepository.save(memberId, from.toString(), to.toString(), serialize(events), CACHE_TTL);
        return GoogleEventsView.connected(events);
    }

    private boolean isConnected(Long memberId) {
        return accountRepository.findByMemberId(memberId)
                .filter(account -> !account.isRevoked())
                .isPresent();
    }

    private String serialize(List<PlannerEvent> events) {
        try {
            return objectMapper.writeValueAsString(events);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.GOOGLE_API_FAILED, e);
        }
    }

    private List<PlannerEvent> deserialize(String json) {
        try {
            return objectMapper.readValue(json, EVENT_LIST);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.GOOGLE_API_FAILED, e);
        }
    }
}
