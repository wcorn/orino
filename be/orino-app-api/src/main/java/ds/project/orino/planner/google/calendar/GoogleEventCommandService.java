package ds.project.orino.planner.google.calendar;

import ds.project.orino.planner.google.calendar.dto.EventRequest;
import ds.project.orino.planner.google.calendar.dto.PlannerEvent;
import ds.project.orino.planner.google.client.GoogleCalendarClient;
import ds.project.orino.redis.planner.google.GoogleCalendarCacheRepository;
import org.springframework.stereotype.Service;

import java.time.ZoneId;

/**
 * 일정 쓰기(생성/수정/삭제) 오케스트레이션. Google 프록시 후 해당 사용자 단기 캐시를 무효화한다.
 *
 * <p>미연동이면 {@link GoogleCalendarClient}의 토큰 공급 단계에서 GOOGLE_NOT_CONNECTED(409)가 발생한다.
 * 반복 일정 편집은 단일 인스턴스 patch 우선("이 일정 이후 전체"는 차기).
 */
@Service
public class GoogleEventCommandService {

    private final GoogleCalendarClient calendarClient;
    private final GoogleCalendarCacheRepository cacheRepository;

    public GoogleEventCommandService(GoogleCalendarClient calendarClient,
                                     GoogleCalendarCacheRepository cacheRepository) {
        this.calendarClient = calendarClient;
        this.cacheRepository = cacheRepository;
    }

    public PlannerEvent create(Long memberId, EventRequest request, ZoneId zone) {
        PlannerEvent created = calendarClient.insertEvent(memberId, request, zone);
        cacheRepository.evictAll(memberId);
        return created;
    }

    public PlannerEvent update(Long memberId, String eventId, EventRequest request, ZoneId zone) {
        PlannerEvent updated = calendarClient.patchEvent(memberId, eventId, request, zone);
        cacheRepository.evictAll(memberId);
        return updated;
    }

    public void delete(Long memberId, String eventId) {
        calendarClient.deleteEvent(memberId, eventId);
        cacheRepository.evictAll(memberId);
    }
}
