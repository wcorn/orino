package ds.project.orino.planner.google.routine;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.planner.google.calendar.dto.EventRequest;
import ds.project.orino.planner.google.calendar.dto.PlannerEvent;
import ds.project.orino.planner.google.client.GoogleCalendarClient;
import ds.project.orino.planner.google.recurrence.RecurrenceRule;
import ds.project.orino.planner.google.routine.dto.RoutineCreateRequest;
import ds.project.orino.planner.google.routine.dto.RoutineSeriesSummary;
import ds.project.orino.redis.planner.google.GoogleCalendarCacheRepository;
import org.springframework.stereotype.Service;

import java.time.ZoneId;

/**
 * 루틴(반복 이벤트) 쓰기 오케스트레이션. 규칙→RRULE+태그로 Google {@code events.insert}를 프록시하고
 * 쓰기 후 통합 피드 캐시를 무효화한다.
 *
 * <p>미연동이면 {@link GoogleCalendarClient} 토큰 공급 단계에서 GOOGLE_NOT_CONNECTED(409)가 발생한다.
 */
@Service
public class RoutineService {

    private final GoogleCalendarClient calendarClient;
    private final GoogleCalendarCacheRepository cacheRepository;

    public RoutineService(GoogleCalendarClient calendarClient,
                          GoogleCalendarCacheRepository cacheRepository) {
        this.calendarClient = calendarClient;
        this.cacheRepository = cacheRepository;
    }

    /** 루틴 시리즈를 생성하고 요약을 반환한다. */
    public RoutineSeriesSummary create(Long memberId, RoutineCreateRequest request, ZoneId zone) {
        RoutineType type = parseType(request.type());
        RecurrenceRule rule = RoutineRecurrenceMapper.toRule(request.recurrence());

        EventRequest eventRequest = new EventRequest(
                request.title(), request.allDay(), request.start(), request.end(),
                null, request.memo());

        PlannerEvent created = calendarClient.insertRoutineEvent(memberId, eventRequest, rule, type, zone);
        cacheRepository.evictAll(memberId);

        return toSummary(created, type, rule, request.color());
    }

    private RoutineType parseType(String type) {
        try {
            return RoutineType.fromCode(type);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, e);
        }
    }

    private RoutineSeriesSummary toSummary(PlannerEvent event, RoutineType type,
                                           RecurrenceRule rule, String color) {
        return new RoutineSeriesSummary(
                event.id(),
                type.code(),
                event.title(),
                event.allDay(),
                event.start(),
                event.allDay() ? null : event.end(),
                RoutineRecurrenceMapper.toDto(rule),
                RecurrenceTextFormatter.toKorean(rule),
                color);
    }
}
