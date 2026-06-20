package ds.project.orino.planner.google.routine;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.routine.repository.RoutineCheckRepository;
import ds.project.orino.planner.google.calendar.dto.EventRequest;
import ds.project.orino.planner.google.calendar.dto.GoogleEventsResponse.GoogleEventItem;
import ds.project.orino.planner.google.calendar.dto.PlannerEvent;
import ds.project.orino.planner.google.client.GoogleCalendarClient;
import ds.project.orino.planner.google.recurrence.RecurrenceRule;
import ds.project.orino.planner.google.recurrence.RecurrenceRuleFactory;
import ds.project.orino.planner.google.routine.dto.RoutineEditRequest;
import ds.project.orino.planner.google.routine.dto.RoutineSeriesSummary;
import ds.project.orino.redis.planner.google.GoogleCalendarCacheRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * 루틴 시리즈 편집/삭제 오케스트레이션 (3-scope: all / following / instance).
 *
 * <p>Google이 진실 원천이라 단일 호출이 없는 "이후 모두"는 ①마스터 RRULE {@code UNTIL}=분할 전날 절단 →
 * ②새 시리즈 insert(태그 복사)의 2-step으로 처리한다. ②는 분할 마커로 멱등 복구되며(재시도 시 중복 생성 방지),
 * 그 후 {@code routine_check}를 분할 경계로 이관/정리한다. 쓰기 후 통합 피드 캐시를 무효화한다.
 */
@Service
public class RoutineSeriesService {

    private final GoogleCalendarClient calendarClient;
    private final RoutineCheckRepository routineCheckRepository;
    private final GoogleCalendarCacheRepository cacheRepository;

    public RoutineSeriesService(GoogleCalendarClient calendarClient,
                                RoutineCheckRepository routineCheckRepository,
                                GoogleCalendarCacheRepository cacheRepository) {
        this.calendarClient = calendarClient;
        this.routineCheckRepository = routineCheckRepository;
        this.cacheRepository = cacheRepository;
    }

    @Transactional
    public RoutineSeriesSummary edit(Long memberId, String eventId, RoutineScope scope,
                                     LocalDate instanceDate, RoutineEditRequest request, ZoneId zone) {
        requireInstanceDate(scope, instanceDate);
        GoogleEventItem master = calendarClient.getEvent(memberId, eventId);
        RoutineType type = requireRoutineType(master);
        RecurrenceRule rule = RoutineRecurrenceMapper.toRule(request.recurrence());
        EventRequest event = toEventRequest(request);

        RoutineSeriesSummary summary = switch (scope) {
            case ALL -> {
                PlannerEvent updated =
                        calendarClient.patchRoutineEvent(memberId, eventId, event, rule, type, zone);
                yield summary(eventId, updated.title(), updated.allDay(),
                        updated.start(), updated.end(), type, rule);
            }
            case INSTANCE -> {
                String instanceId = resolveInstanceId(memberId, eventId, instanceDate, zone);
                PlannerEvent updated = calendarClient.patchEvent(memberId, instanceId, event, zone);
                yield summary(eventId, updated.title(), updated.allDay(),
                        updated.start(), updated.end(), type, rule);
            }
            case FOLLOWING -> editFollowing(memberId, eventId, master, type, instanceDate, event, rule, zone);
        };

        cacheRepository.evictAll(memberId);
        return summary;
    }

    @Transactional
    public void delete(Long memberId, String eventId, RoutineScope scope,
                       LocalDate instanceDate, ZoneId zone) {
        requireInstanceDate(scope, instanceDate);

        switch (scope) {
            case ALL -> {
                calendarClient.deleteEvent(memberId, eventId);
                routineCheckRepository.deleteByMemberIdAndRecurringEventId(memberId, eventId);
            }
            case INSTANCE -> {
                String instanceId = resolveInstanceId(memberId, eventId, instanceDate, zone);
                calendarClient.deleteEvent(memberId, instanceId);
                routineCheckRepository.deleteByMemberIdAndRecurringEventIdAndInstanceDate(
                        memberId, eventId, instanceDate);
            }
            case FOLLOWING -> {
                truncateMaster(memberId, eventId, instanceDate, zone);
                routineCheckRepository.deleteByMemberIdAndRecurringEventIdAndInstanceDateGreaterThanEqual(
                        memberId, eventId, instanceDate);
            }
            default -> throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        cacheRepository.evictAll(memberId);
    }

    /**
     * 이후 모두 편집: ①마스터 UNTIL 절단 → ②새 시리즈 fork(마커로 멱등) → 체크 {@code >= instanceDate} 이관.
     */
    private RoutineSeriesSummary editFollowing(Long memberId, String masterId, GoogleEventItem master,
                                               RoutineType type, LocalDate instanceDate,
                                               EventRequest event, RecurrenceRule newRule, ZoneId zone) {
        truncateMaster(memberId, masterId, master, instanceDate, zone);

        String marker = RoutineTag.splitMarker(masterId, instanceDate.toString());
        String newId = calendarClient.findForkedSeries(memberId, marker)
                .map(GoogleEventItem::id)
                .orElseGet(() -> calendarClient
                        .insertForkedSeries(memberId, event, newRule, type, marker, zone).id());

        routineCheckRepository.migrateFollowing(memberId, masterId, newId, instanceDate);

        return summary(newId, event.title(), event.allDay(), event.start(), event.end(), type, newRule);
    }

    /** 마스터 recurrence의 UNTIL을 instanceDate 전날로 절단(idempotent). */
    private void truncateMaster(Long memberId, String masterId, LocalDate instanceDate, ZoneId zone) {
        truncateMaster(memberId, masterId, calendarClient.getEvent(memberId, masterId), instanceDate, zone);
    }

    private void truncateMaster(Long memberId, String masterId, GoogleEventItem master,
                                LocalDate instanceDate, ZoneId zone) {
        RecurrenceRule current = parseMasterRule(master);
        RecurrenceRule truncated = new RecurrenceRule(current.freq(), current.interval(),
                current.byDay(), current.byMonthDay(), instanceDate.minusDays(1));
        calendarClient.patchEventRecurrence(memberId, masterId, truncated, zone);
    }

    private String resolveInstanceId(Long memberId, String masterId, LocalDate instanceDate, ZoneId zone) {
        Instant timeMin = instanceDate.atStartOfDay(zone).toInstant();
        Instant timeMax = instanceDate.plusDays(1).atStartOfDay(zone).toInstant();
        return calendarClient.listInstances(memberId, masterId, timeMin, timeMax, zone).stream()
                .filter(e -> e.start() != null && e.start().startsWith(instanceDate.toString()))
                .findFirst()
                .map(PlannerEvent::id)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private RecurrenceRule parseMasterRule(GoogleEventItem master) {
        if (master.recurrence() == null || master.recurrence().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return RecurrenceRuleFactory.parse(master.recurrence().get(0), ZoneId.of("UTC"));
    }

    private RoutineType requireRoutineType(GoogleEventItem master) {
        Map<String, String> priv = master.extendedProperties() == null
                ? null
                : master.extendedProperties().privateProperties();
        RoutineType type = RoutineTag.routineType(priv);
        if (type == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return type;
    }

    private void requireInstanceDate(RoutineScope scope, LocalDate instanceDate) {
        if (scope.requiresInstanceDate() && instanceDate == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }

    private EventRequest toEventRequest(RoutineEditRequest request) {
        return new EventRequest(request.title(), request.allDay(), request.start(), request.end(),
                null, request.memo());
    }

    private RoutineSeriesSummary summary(String recurringEventId, String title, boolean allDay,
                                         String start, String end, RoutineType type, RecurrenceRule rule) {
        return new RoutineSeriesSummary(
                recurringEventId,
                type.code(),
                title,
                allDay,
                start,
                allDay ? null : end,
                RoutineRecurrenceMapper.toDto(rule),
                RecurrenceTextFormatter.toKorean(rule),
                null);
    }
}
