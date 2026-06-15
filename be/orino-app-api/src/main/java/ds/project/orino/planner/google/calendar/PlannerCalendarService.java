package ds.project.orino.planner.google.calendar;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.planner.google.calendar.dto.FeedError;
import ds.project.orino.planner.google.calendar.dto.GoogleEventsView;
import ds.project.orino.planner.google.calendar.dto.PlannerCalendarFeed;
import ds.project.orino.planner.google.calendar.dto.PlannerEvent;
import ds.project.orino.planner.google.calendar.dto.PlannerReview;
import ds.project.orino.planner.google.calendar.dto.PlannerTask;
import ds.project.orino.planner.review.dto.CalendarReviewItem;
import ds.project.orino.planner.review.service.ReviewQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 통합 캘린더 피드 조립. 일정(Google)·할 일(M3)·복습(orino 오버레이)을 한 응답으로 모은다.
 *
 * <p>부분 실패 허용: 소스 하나가 실패해도 나머지를 정상 반환하고 {@code partial=true} + {@code errors[]}.
 * 잘못된 기간 요청은 부분 실패가 아니라 400(INVALID_REQUEST)으로 처리한다.
 */
@Service
public class PlannerCalendarService {

    private static final int MAX_RANGE_DAYS = 100;

    private final GoogleEventQueryService googleEventQueryService;
    private final ReviewQueryService reviewQueryService;

    public PlannerCalendarService(GoogleEventQueryService googleEventQueryService,
                                  ReviewQueryService reviewQueryService) {
        this.googleEventQueryService = googleEventQueryService;
        this.reviewQueryService = reviewQueryService;
    }

    public PlannerCalendarFeed getFeed(Long memberId, LocalDate from, LocalDate to, ZoneId zone) {
        validateRange(from, to);

        List<FeedError> errors = new ArrayList<>();

        boolean googleConnected = false;
        List<PlannerEvent> events = List.of();
        try {
            GoogleEventsView view = googleEventQueryService.getEvents(memberId, from, to, zone);
            googleConnected = view.connected();
            events = view.events();
        } catch (CustomException e) {
            // invalid_grant면 연동이 만료된 것이라 미연동으로 본다(FE 재연동 CTA).
            googleConnected = e.getErrorCode() != ErrorCode.GOOGLE_INVALID_GRANT;
            errors.add(new FeedError("google-events", e.getErrorCode().getMessage()));
        } catch (RuntimeException e) {
            googleConnected = true;
            errors.add(new FeedError("google-events", "일정을 불러오지 못했습니다."));
        }

        List<PlannerReview> reviews = List.of();
        try {
            reviews = reviewQueryService.findCalendar(memberId, from, to).reviews().stream()
                    .map(PlannerCalendarService::toFeedReview)
                    .toList();
        } catch (RuntimeException e) {
            errors.add(new FeedError("reviews", "복습을 불러오지 못했습니다."));
        }

        List<PlannerTask> tasks = List.of(); // M3(#484)에서 합류

        return new PlannerCalendarFeed(
                from, to, googleConnected, !errors.isEmpty(), errors, events, tasks, reviews);
    }

    private static PlannerReview toFeedReview(CalendarReviewItem item) {
        return new PlannerReview(
                item.id(),
                item.scheduledAt(),
                item.status().name(),
                item.flashcard().material().title(),
                item.flashcard().front(),
                true,
                "review");
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)
                || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }
}
