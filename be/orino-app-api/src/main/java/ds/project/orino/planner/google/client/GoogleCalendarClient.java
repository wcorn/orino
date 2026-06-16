package ds.project.orino.planner.google.client;

import ds.project.orino.planner.google.calendar.dto.EventRequest;
import ds.project.orino.planner.google.calendar.dto.GoogleEventWriteBody;
import ds.project.orino.planner.google.calendar.dto.GoogleEventWriteBody.GoogleEventTime;
import ds.project.orino.planner.google.calendar.dto.GoogleEventsResponse;
import ds.project.orino.planner.google.calendar.dto.GoogleEventsResponse.GoogleEventDateTime;
import ds.project.orino.planner.google.calendar.dto.GoogleEventsResponse.GoogleEventItem;
import ds.project.orino.planner.google.calendar.dto.PlannerEvent;
import ds.project.orino.planner.google.config.GoogleOAuthProperties;
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
    private static final String PRIMARY_CALENDAR_PATH = "/calendar/v3/calendars/primary/events";

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
        URI uri = UriComponentsBuilder.fromUriString(oauthProperties.calendarApiBaseUrl())
                .path(PRIMARY_CALENDAR_PATH)
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

    /** 일정 생성. 생성된 이벤트를 정규화해 반환한다. */
    public PlannerEvent insertEvent(Long memberId, EventRequest request, ZoneId zone) {
        URI uri = UriComponentsBuilder.fromUriString(oauthProperties.calendarApiBaseUrl())
                .path(PRIMARY_CALENDAR_PATH)
                .build()
                .toUri();

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

    /** 일정 삭제. 없는 id면 404(RESOURCE_NOT_FOUND). */
    public void deleteEvent(Long memberId, String eventId) {
        URI uri = eventUri(eventId);
        tokenProvider.executeWithRetry(memberId, accessToken -> {
            googleRestClient.delete()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    private URI eventUri(String eventId) {
        return UriComponentsBuilder.fromUriString(oauthProperties.calendarApiBaseUrl())
                .path(PRIMARY_CALENDAR_PATH)
                .pathSegment(eventId)
                .build()
                .toUri();
    }

    private GoogleEventWriteBody toWriteBody(EventRequest request, ZoneId zone) {
        GoogleEventTime start;
        GoogleEventTime end;
        if (request.allDay()) {
            start = new GoogleEventTime(null, request.start(), null);
            // Google 종일 종료는 배타적(다음 날) → 사용자 inclusive end + 1일
            end = new GoogleEventTime(null, LocalDate.parse(request.end()).plusDays(1).toString(), null);
        } else {
            start = new GoogleEventTime(request.start(), null, zone.getId());
            end = new GoogleEventTime(request.end(), null, zone.getId());
        }
        return new GoogleEventWriteBody(
                request.title(), request.location(), request.description(), start, end);
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
                item.recurringEventId() != null,
                "google");
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
