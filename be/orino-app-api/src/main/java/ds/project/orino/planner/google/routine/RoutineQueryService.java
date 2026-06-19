package ds.project.orino.planner.google.routine;

import ds.project.orino.planner.google.calendar.dto.GoogleEventsResponse.GoogleEventDateTime;
import ds.project.orino.planner.google.calendar.dto.GoogleEventsResponse.GoogleEventItem;
import ds.project.orino.planner.google.client.GoogleCalendarClient;
import ds.project.orino.planner.google.recurrence.RecurrenceRule;
import ds.project.orino.planner.google.recurrence.RecurrenceRuleFactory;
import ds.project.orino.planner.google.routine.dto.RoutineSeriesSummary;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 루틴 시리즈 목록 조회. {@code singleEvents=false}로 마스터만 받아 RRULE을 역파싱하고
 * 종류·한글 요약과 함께 관리 화면용 시리즈 요약으로 매핑한다.
 *
 * <p>미연동이면 {@link GoogleCalendarClient} 토큰 공급 단계에서 GOOGLE_NOT_CONNECTED(409)가 발생한다.
 */
@Service
public class RoutineQueryService {

    private static final DateTimeFormatter LOCAL_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final GoogleCalendarClient calendarClient;

    public RoutineQueryService(GoogleCalendarClient calendarClient) {
        this.calendarClient = calendarClient;
    }

    /** 루틴 마스터 시리즈 목록. 반복 규칙이 없는 항목(비정상 마스터)은 제외한다. */
    public List<RoutineSeriesSummary> list(Long memberId, ZoneId zone) {
        return calendarClient.listRoutineMasters(memberId).stream()
                .map(item -> toSummary(item, zone))
                .filter(Objects::nonNull)
                .toList();
    }

    private RoutineSeriesSummary toSummary(GoogleEventItem item, ZoneId zone) {
        if (item.recurrence() == null || item.recurrence().isEmpty()) {
            return null;
        }
        RecurrenceRule rule = RecurrenceRuleFactory.parse(item.recurrence().get(0), zone);

        boolean allDay = item.start() != null && item.start().date() != null;
        String start = allDay ? item.start().date() : toLocalDateTime(item.start(), zone);
        String end = allDay ? null : toLocalDateTime(item.end(), zone);

        return new RoutineSeriesSummary(
                item.id(),
                routineTypeCode(item),
                item.summary(),
                allDay,
                start,
                end,
                RoutineRecurrenceMapper.toDto(rule),
                RecurrenceTextFormatter.toKorean(rule),
                null);
    }

    private String routineTypeCode(GoogleEventItem item) {
        Map<String, String> priv = item.extendedProperties() == null
                ? null
                : item.extendedProperties().privateProperties();
        RoutineType type = RoutineTag.routineType(priv);
        return type == null ? null : type.code();
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
