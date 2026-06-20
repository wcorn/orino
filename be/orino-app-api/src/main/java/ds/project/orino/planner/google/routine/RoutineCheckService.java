package ds.project.orino.planner.google.routine;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.routine.entity.RoutineCheck;
import ds.project.orino.domain.planner.routine.repository.RoutineCheckRepository;
import ds.project.orino.planner.google.calendar.dto.GoogleEventsResponse.GoogleEventItem;
import ds.project.orino.planner.google.client.GoogleCalendarClient;
import ds.project.orino.planner.google.routine.dto.RoutineCheckResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

/**
 * 습관 완료 체크 토글. {@code done=true}면 행 upsert(존재=완료), {@code done=false}면 행 삭제(부재=미완료).
 *
 * <p>대상이 habit 루틴인지 Google 마스터 이벤트의 {@code orinoRoutineType} 태그로 검증한다.
 * schedule(시간 고정)이거나 루틴이 아니면 400(체크 비대상). 없는 시리즈면 getEvent가 404를 던진다.
 * 미연동이면 토큰 공급 단계에서 409.
 */
@Service
public class RoutineCheckService {

    private final GoogleCalendarClient calendarClient;
    private final RoutineCheckRepository routineCheckRepository;

    public RoutineCheckService(GoogleCalendarClient calendarClient,
                               RoutineCheckRepository routineCheckRepository) {
        this.calendarClient = calendarClient;
        this.routineCheckRepository = routineCheckRepository;
    }

    @Transactional
    public RoutineCheckResponse toggle(Long memberId, String recurringEventId,
                                       LocalDate date, boolean done) {
        requireHabit(memberId, recurringEventId);

        if (done) {
            check(memberId, recurringEventId, date);
        } else {
            routineCheckRepository.deleteByMemberIdAndRecurringEventIdAndInstanceDate(
                    memberId, recurringEventId, date);
        }
        return new RoutineCheckResponse(recurringEventId, date, done);
    }

    /** habit이 아니면 400. 시리즈가 없으면 getEvent가 404(RESOURCE_NOT_FOUND). */
    private void requireHabit(Long memberId, String recurringEventId) {
        GoogleEventItem master = calendarClient.getEvent(memberId, recurringEventId);
        Map<String, String> priv = master.extendedProperties() == null
                ? null
                : master.extendedProperties().privateProperties();
        if (RoutineTag.routineType(priv) != RoutineType.HABIT) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }

    /** 멱등 upsert: 이미 있으면 그대로 둔다. */
    private void check(Long memberId, String recurringEventId, LocalDate date) {
        boolean exists = routineCheckRepository
                .existsByMemberIdAndRecurringEventIdAndInstanceDate(memberId, recurringEventId, date);
        if (!exists) {
            routineCheckRepository.save(new RoutineCheck(memberId, recurringEventId, date));
        }
    }
}
