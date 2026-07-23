package ds.project.orino.planner.google.client;

import ds.project.orino.planner.google.calendar.dto.EventRequest;
import ds.project.orino.planner.google.calendar.dto.GoogleCalendarResource;
import ds.project.orino.planner.google.calendar.dto.GoogleCalendarWriteBody;
import ds.project.orino.planner.google.calendar.dto.GoogleEventWriteBody;
import ds.project.orino.planner.google.calendar.dto.GoogleEventWriteBody.GoogleEventTime;
import ds.project.orino.planner.google.calendar.dto.GoogleEventWriteBody.GoogleReminders;
import ds.project.orino.planner.google.calendar.dto.GoogleEventsResponse;
import ds.project.orino.planner.google.calendar.dto.GoogleEventsResponse.GoogleEventDateTime;
import ds.project.orino.planner.google.calendar.dto.GoogleEventsResponse.GoogleEventItem;
import ds.project.orino.planner.google.calendar.dto.GoogleExtendedProperties;
import ds.project.orino.planner.google.calendar.dto.PlannerEvent;
import ds.project.orino.planner.google.calendar.dto.RoutineMeta;
import ds.project.orino.planner.google.config.GoogleOAuthProperties;
import ds.project.orino.planner.google.recurrence.RecurrenceRule;
import ds.project.orino.planner.google.recurrence.RecurrenceRuleFactory;
import ds.project.orino.planner.google.routine.RoutineTag;
import ds.project.orino.planner.google.routine.RoutineType;
import ds.project.orino.planner.google.token.GoogleTokenProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Google Calendar API 래퍼. {@code events.list}를 라이브 프록시하고 응답을 사용자 시간대로 정규화한다.
 *
 * <p>{@code singleEvents=true&orderBy=startTime}로 RRULE 반복을 기간 내 인스턴스로 펼쳐 받아
 * orino는 RRULE을 직접 구현하지 않는다. access token은 {@link GoogleTokenProvider#executeWithRetry}로
 * 공급·401 재시도한다(만료 시 1회 자동 갱신).
 */
@Component
public class GoogleCalendarClient {

    /** 사용자 TZ 로컬 datetime 포맷(오프셋 없이 "2026-06-10T14:00:00"). */
    private static final DateTimeFormatter LOCAL_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String PRIMARY_CALENDAR_ID = "primary";

    private final GoogleTokenProvider tokenProvider;
    private final RestClient googleRestClient;
    private final GoogleOAuthProperties oauthProperties;

    public GoogleCalendarClient(GoogleTokenProvider tokenProvider,
                                RestClient googleRestClient,
                                GoogleOAuthProperties oauthProperties) {
        this.tokenProvider = tokenProvider;
        this.googleRestClient = googleRestClient;
        this.oauthProperties = oauthProperties;
    }

    /** [timeMin, timeMax) 구간의 primary 캘린더 일정을 조회해 사용자 시간대로 정규화한다. */
    public List<PlannerEvent> listEvents(Long memberId, Instant timeMin, Instant timeMax, ZoneId zone) {
        URI uri = eventsBuilder(PRIMARY_CALENDAR_ID)
                .queryParam("timeMin", timeMin.toString())
                .queryParam("timeMax", timeMax.toString())
                .queryParam("singleEvents", "true")
                .queryParam("orderBy", "startTime")
                .queryParam("maxResults", "2500")
                .build()
                .toUri();

        GoogleEventsResponse response = tokenProvider.executeWithRetry(memberId, accessToken ->
                googleRestClient.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .retrieve()
                        .body(GoogleEventsResponse.class));

        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items().stream()
                .map(item -> normalize(item, zone))
                .toList();
    }

    /**
     * 단일 이벤트를 원본(raw) 형태로 조회한다. extendedProperties·recurrence를 그대로 노출해 호출부가
     * 루틴 종류 판별 등에 사용한다. 없는 id면 404(RESOURCE_NOT_FOUND).
     */
    public GoogleEventItem getEvent(Long memberId, String eventId) {
        URI uri = eventUri(eventId);
        return tokenProvider.executeWithRetry(memberId, accessToken ->
                googleRestClient.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .retrieve()
                        .body(GoogleEventItem.class));
    }

    /** 일정 생성. 생성된 이벤트를 정규화해 반환한다. */
    public PlannerEvent insertEvent(Long memberId, EventRequest request, ZoneId zone) {
        URI uri = eventsBuilder(PRIMARY_CALENDAR_ID).build().toUri();

        GoogleEventWriteBody body = toWriteBody(request, zone);
        GoogleEventItem item = tokenProvider.executeWithRetry(memberId, accessToken ->
                googleRestClient.post()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(GoogleEventItem.class));
        return normalize(item, zone);
    }

    /** 일정 단일 인스턴스 수정(patch). 없는 id면 404(RESOURCE_NOT_FOUND). */
    public PlannerEvent patchEvent(Long memberId, String eventId, EventRequest request, ZoneId zone) {
        URI uri = eventUri(eventId);
        GoogleEventWriteBody body = toWriteBody(request, zone);
        GoogleEventItem item = tokenProvider.executeWithRetry(memberId, accessToken ->
                googleRestClient.patch()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(GoogleEventItem.class));
        return normalize(item, zone);
    }

    /**
     * 루틴(반복) 이벤트 생성. {@code recurrence}(RRULE)와 루틴 식별 태그를 함께 기록한다.
     * 생성된 마스터 이벤트를 정규화해 반환한다.
     */
    public PlannerEvent insertRoutineEvent(Long memberId, EventRequest request,
                                           RecurrenceRule rule, RoutineType type, ZoneId zone) {
        URI uri = eventsBuilder(PRIMARY_CALENDAR_ID).build().toUri();

        GoogleEventWriteBody body = toRoutineWriteBody(request, rule, RoutineTag.privateProperties(type), zone);
        GoogleEventItem item = tokenProvider.executeWithRetry(memberId, accessToken ->
                googleRestClient.post()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(GoogleEventItem.class));
        return normalize(item, zone);
    }

    /**
     * 루틴 마스터 이벤트 수정(patch). 반복 규칙·내용·태그를 갱신한다.
     * {@code rule}이 null이면 recurrence는 건드리지 않는다(내용만 patch).
     */
    public PlannerEvent patchRoutineEvent(Long memberId, String eventId, EventRequest request,
                                          RecurrenceRule rule, RoutineType type, ZoneId zone) {
        URI uri = eventUri(eventId);
        GoogleEventWriteBody body = toRoutineWriteBody(request, rule, RoutineTag.privateProperties(type), zone);
        GoogleEventItem item = tokenProvider.executeWithRetry(memberId, accessToken ->
                googleRestClient.patch()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(GoogleEventItem.class));
        return normalize(item, zone);
    }

    /**
     * 마스터의 recurrence(RRULE)만 patch한다. start/end/내용은 건드리지 않는다(Google patch 병합).
     * following 분할 시 마스터 UNTIL 절단에 쓴다.
     */
    public PlannerEvent patchEventRecurrence(Long memberId, String eventId, RecurrenceRule rule, ZoneId zone) {
        URI uri = eventUri(eventId);
        GoogleEventWriteBody body = new GoogleEventWriteBody(
                null, null, null, null, null,
                List.of(RecurrenceRuleFactory.toRRule(rule, zone)), null);
        GoogleEventItem item = tokenProvider.executeWithRetry(memberId, accessToken ->
                googleRestClient.patch()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(GoogleEventItem.class));
        return normalize(item, zone);
    }

    /**
     * following 분할로 새 루틴 시리즈를 생성한다. 루틴 태그 + 분할 출처 마커({@code orinoRoutineSplitOf})를 함께
     * 기록해 재시도 시 {@link #findForkedSeries}로 중복 생성을 막는다.
     */
    public PlannerEvent insertForkedSeries(Long memberId, EventRequest request, RecurrenceRule rule,
                                           RoutineType type, String splitMarker, ZoneId zone) {
        URI uri = eventsBuilder(PRIMARY_CALENDAR_ID).build().toUri();

        GoogleEventWriteBody body =
                toRoutineWriteBody(request, rule, RoutineTag.splitProperties(type, splitMarker), zone);
        GoogleEventItem item = tokenProvider.executeWithRetry(memberId, accessToken ->
                googleRestClient.post()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(GoogleEventItem.class));
        return normalize(item, zone);
    }

    /** 주어진 분할 마커로 이미 생성된 forked 시리즈를 찾는다(idempotent 복구용). */
    public Optional<GoogleEventItem> findForkedSeries(Long memberId, String splitMarker) {
        return listRoutineMasters(memberId).stream()
                .filter(item -> splitMarker.equals(splitMarkerOf(item)))
                .findFirst();
    }

    private static String splitMarkerOf(GoogleEventItem item) {
        if (item.extendedProperties() == null
                || item.extendedProperties().privateProperties() == null) {
            return null;
        }
        return item.extendedProperties().privateProperties().get(RoutineTag.KEY_SPLIT_OF);
    }

    /**
     * 루틴 마스터 시리즈 목록 조회. {@code singleEvents=false}로 RRULE을 펼치지 않고 마스터 이벤트를,
     * {@code privateExtendedProperty=orinoRoutine=1}로 루틴만 필터링해 받는다.
     *
     * <p>마스터의 {@code recurrence}·{@code extendedProperties}를 그대로 노출하므로 호출부(시리즈 목록 API)가
     * RRULE 역파싱과 종류 판별을 수행한다.
     */
    public List<GoogleEventItem> listRoutineMasters(Long memberId) {
        URI uri = eventsBuilder(PRIMARY_CALENDAR_ID)
                .queryParam("singleEvents", "false")
                .queryParam("privateExtendedProperty", RoutineTag.LIST_FILTER)
                .queryParam("maxResults", "2500")
                .build()
                .toUri();

        GoogleEventsResponse response = tokenProvider.executeWithRetry(memberId, accessToken ->
                googleRestClient.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .retrieve()
                        .body(GoogleEventsResponse.class));

        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items();
    }

    /**
     * 단일 반복 이벤트({@code recurringEventId})를 [timeMin, timeMax) 구간 인스턴스로 펼쳐 조회한다.
     * events.instances 엔드포인트를 사용하며 응답을 사용자 시간대로 정규화한다.
     */
    public List<PlannerEvent> listInstances(Long memberId, String eventId,
                                            Instant timeMin, Instant timeMax, ZoneId zone) {
        URI uri = eventsBuilder(PRIMARY_CALENDAR_ID)
                .pathSegment(eventId, "instances")
                .queryParam("timeMin", timeMin.toString())
                .queryParam("timeMax", timeMax.toString())
                .queryParam("maxResults", "2500")
                .build()
                .toUri();

        GoogleEventsResponse response = tokenProvider.executeWithRetry(memberId, accessToken ->
                googleRestClient.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .retrieve()
                        .body(GoogleEventsResponse.class));

        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items().stream()
                .map(item -> normalize(item, zone))
                .toList();
    }

    /** primary 캘린더 일정 삭제. 없는 id면 404(RESOURCE_NOT_FOUND). */
    public void deleteEvent(Long memberId, String eventId) {
        deleteEvent(memberId, PRIMARY_CALENDAR_ID, eventId);
    }

    /** 지정 캘린더의 일정 삭제. 없는 id면 404(RESOURCE_NOT_FOUND). */
    public void deleteEvent(Long memberId, String calendarId, String eventId) {
        URI uri = eventUri(calendarId, eventId);
        tokenProvider.executeWithRetry(memberId, accessToken -> {
            googleRestClient.delete()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    /** {@code /calendars/{calendarId}/events} 컬렉션 빌더(쓰기/목록 공용). calendarId는 자동 URL 인코딩된다. */
    private UriComponentsBuilder eventsBuilder(String calendarId) {
        return UriComponentsBuilder.fromUriString(oauthProperties.calendarApiBaseUrl())
                .pathSegment("calendar", "v3", "calendars", calendarId, "events");
    }

    private URI eventUri(String eventId) {
        return eventUri(PRIMARY_CALENDAR_ID, eventId);
    }

    private URI eventUri(String calendarId, String eventId) {
        return eventsBuilder(calendarId).pathSegment(eventId).build().toUri();
    }

    // ── 복습 미러: 보조 캘린더 + 종일 이벤트 ──

    /**
     * 보조 캘린더("orino 복습")를 생성하고 그 calendarId를 반환한다. 미러 최초 enable 시 1회 호출한다.
     */
    public String createSecondaryCalendar(Long memberId, String summary) {
        URI uri = UriComponentsBuilder.fromUriString(oauthProperties.calendarApiBaseUrl())
                .pathSegment("calendar", "v3", "calendars")
                .build()
                .toUri();
        GoogleCalendarWriteBody body = new GoogleCalendarWriteBody(summary);
        GoogleCalendarResource created = tokenProvider.executeWithRetry(memberId, accessToken ->
                googleRestClient.post()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(GoogleCalendarResource.class));
        return created == null ? null : created.id();
    }

    /**
     * 보조 캘린더에 하루짜리 종일 이벤트를 생성하고 생성된 eventId를 반환한다. 알림은 계정 기본(useDefault).
     * {@code description}은 자료별 묶음 설명(null 가능).
     */
    public String insertAllDayEvent(Long memberId, String calendarId, String summary,
                                    String description, LocalDate date) {
        URI uri = eventsBuilder(calendarId).build().toUri();
        GoogleEventWriteBody body = allDayBody(summary, description, date);
        GoogleEventItem item = tokenProvider.executeWithRetry(memberId, accessToken ->
                googleRestClient.post()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(GoogleEventItem.class));
        return item == null ? null : item.id();
    }

    /**
     * 보조 캘린더 종일 이벤트의 제목·설명을 patch한다(복습 개수/자료 변동 반영). 없는 id면 404(RESOURCE_NOT_FOUND).
     */
    public void patchAllDayEvent(Long memberId, String calendarId, String eventId,
                                 String summary, String description) {
        URI uri = eventUri(calendarId, eventId);
        GoogleEventWriteBody body = new GoogleEventWriteBody(summary, null, description, null, null);
        tokenProvider.executeWithRetry(memberId, accessToken ->
                googleRestClient.patch()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(GoogleEventItem.class));
    }

    /** 하루짜리 종일 이벤트 바디(useDefault 알림). Google 종일 종료는 배타적이라 end=date+1일. */
    private GoogleEventWriteBody allDayBody(String summary, String description, LocalDate date) {
        GoogleEventTime start = new GoogleEventTime(null, date.toString(), null);
        GoogleEventTime end = new GoogleEventTime(null, date.plusDays(1).toString(), null);
        return new GoogleEventWriteBody(
                summary, null, description, start, end, null, null, GoogleReminders.defaults());
    }

    private GoogleEventWriteBody toWriteBody(EventRequest request, ZoneId zone) {
        return new GoogleEventWriteBody(
                request.title(), request.location(), request.description(),
                startTime(request, zone), endTime(request, zone));
    }

    /** 루틴 쓰기 바디: 기본 필드 + recurrence(RRULE) + 루틴 태그. rule이 null이면 recurrence는 비운다. */
    private GoogleEventWriteBody toRoutineWriteBody(EventRequest request, RecurrenceRule rule,
                                                    Map<String, String> privateProps, ZoneId zone) {
        List<String> recurrence = rule == null
                ? null
                : List.of(RecurrenceRuleFactory.toRRule(rule, zone));
        GoogleExtendedProperties extended = GoogleExtendedProperties.ofPrivate(privateProps);
        return new GoogleEventWriteBody(
                request.title(), request.location(), request.description(),
                startTime(request, zone), endTime(request, zone), recurrence, extended);
    }

    private GoogleEventTime startTime(EventRequest request, ZoneId zone) {
        return request.allDay()
                ? new GoogleEventTime(null, request.start(), null)
                : new GoogleEventTime(request.start(), null, zone.getId());
    }

    private GoogleEventTime endTime(EventRequest request, ZoneId zone) {
        if (request.allDay()) {
            // Google 종일 종료는 배타적(다음 날) → 사용자 inclusive end + 1일
            return new GoogleEventTime(null, LocalDate.parse(request.end()).plusDays(1).toString(), null);
        }
        return new GoogleEventTime(request.end(), null, zone.getId());
    }

    private PlannerEvent normalize(GoogleEventItem item, ZoneId zone) {
        GoogleEventDateTime start = item.start();
        GoogleEventDateTime end = item.end();
        boolean allDay = start != null && start.date() != null;

        String startValue;
        String endValue;
        if (allDay) {
            startValue = start.date();
            // Google 종일 종료는 배타적(다음 날) → 포함 마지막 날짜로 보정
            endValue = (end != null && end.date() != null)
                    ? LocalDate.parse(end.date()).minusDays(1).toString()
                    : start.date();
        } else {
            startValue = toLocalDateTime(start, zone);
            endValue = toLocalDateTime(end, zone);
        }

        return new PlannerEvent(
                item.id(),
                item.summary(),
                allDay,
                startValue,
                endValue,
                item.location(),
                item.description(),
                item.recurringEventId() != null,
                "google",
                toRoutineMeta(item));
    }

    /** extendedProperties에 루틴 태그가 있으면 피드 주석(type/recurringEventId/done)을 만든다. done은 R3에서 조인. */
    private RoutineMeta toRoutineMeta(GoogleEventItem item) {
        if (item.extendedProperties() == null) {
            return null;
        }
        Map<String, String> priv = item.extendedProperties().privateProperties();
        if (!RoutineTag.isRoutine(priv)) {
            return null;
        }
        RoutineType type = RoutineTag.routineType(priv);
        return new RoutineMeta(
                type == null ? null : type.code(),
                item.recurringEventId(),
                false);
    }

    private String toLocalDateTime(GoogleEventDateTime dateTime, ZoneId zone) {
        if (dateTime == null || dateTime.dateTime() == null) {
            return null;
        }
        return OffsetDateTime.parse(dateTime.dateTime())
                .atZoneSameInstant(zone)
                .toLocalDateTime()
                .format(LOCAL_DATETIME);
    }
}
