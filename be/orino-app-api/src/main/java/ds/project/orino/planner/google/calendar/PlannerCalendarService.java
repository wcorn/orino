package ds.project.orino.planner.google.calendar;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.planner.google.calendar.dto.FeedError;
import ds.project.orino.planner.google.calendar.dto.GoogleEventsView;
import ds.project.orino.planner.google.calendar.dto.PlannerCalendarFeed;
import ds.project.orino.planner.google.calendar.dto.PlannerEvent;
import ds.project.orino.planner.google.calendar.dto.PlannerReview;
import ds.project.orino.planner.google.calendar.dto.PlannerTask;
import ds.project.orino.planner.google.task.GoogleTasksQueryService;
import ds.project.orino.planner.review.dto.CalendarReviewItem;
import ds.project.orino.planner.review.service.ReviewQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 통합 캘린더 피드 조립. 일정(Google)·할 일(M3)·복습(orino 오버레이)을 한 응답으로 모은다.
 *
 * <p>부분 실패 허용: 소스 하나가 실패해도 나머지를 정상 반환하고 {@code partial=true} + {@code errors[]}.
 * 잘못된 기간 요청은 부분 실패가 아니라 400(INVALID_REQUEST)으로 처리한다.
 *
 * <p>지연 최적화(#544): 외부 Google API 두 건(일정·할 일)을 직렬로 쌓던 것을 가상 스레드로 병렬 실행하고,
 * 서브밀리초 DB 조회인 복습은 요청 스레드에서 그대로 수행한다(시간대 ThreadLocal·OSIV 유지). 총 지연이
 * 직렬 합산에서 max로 줄어든다.
 */
@Service
public class PlannerCalendarService {

    private static final int MAX_RANGE_DAYS = 100;

    private final GoogleEventQueryService googleEventQueryService;
    private final GoogleTasksQueryService googleTasksQueryService;
    private final ReviewQueryService reviewQueryService;
    private final ExecutorService plannerFeedExecutor;

    public PlannerCalendarService(GoogleEventQueryService googleEventQueryService,
                                  GoogleTasksQueryService googleTasksQueryService,
                                  ReviewQueryService reviewQueryService,
                                  ExecutorService plannerFeedExecutor) {
        this.googleEventQueryService = googleEventQueryService;
        this.googleTasksQueryService = googleTasksQueryService;
        this.reviewQueryService = reviewQueryService;
        this.plannerFeedExecutor = plannerFeedExecutor;
    }

    public PlannerCalendarFeed getFeed(Long memberId, LocalDate from, LocalDate to, ZoneId zone) {
        validateRange(from, to);

        // 외부 Google API 두 건은 가상 스레드로 동시에 호출한다(zone은 인자로 전달, ThreadLocal 비의존).
        CompletableFuture<EventsResult> eventsFuture =
                CompletableFuture.supplyAsync(() -> fetchEvents(memberId, from, to, zone), plannerFeedExecutor);
        CompletableFuture<TasksResult> tasksFuture =
                CompletableFuture.supplyAsync(() -> fetchTasks(memberId, from, to), plannerFeedExecutor);

        // 복습은 DB(서브밀리초)라 요청 스레드에서 실행해 UserTimeZone ThreadLocal과 OSIV를 유지한다.
        ReviewsResult reviewsResult = fetchReviews(memberId, from, to);

        EventsResult eventsResult = eventsFuture.join();
        TasksResult tasksResult = tasksFuture.join();

        List<FeedError> errors = new ArrayList<>();
        eventsResult.error().ifPresent(errors::add);
        reviewsResult.error().ifPresent(errors::add);
        tasksResult.error().ifPresent(errors::add);

        return new PlannerCalendarFeed(
                from, to,
                eventsResult.connected(),
                !errors.isEmpty(),
                errors,
                eventsResult.events(),
                tasksResult.tasks(),
                reviewsResult.reviews());
    }

    private EventsResult fetchEvents(Long memberId, LocalDate from, LocalDate to, ZoneId zone) {
        try {
            GoogleEventsView view = googleEventQueryService.getEvents(memberId, from, to, zone);
            return new EventsResult(view.connected(), view.events(), Optional.empty());
        } catch (CustomException e) {
            // invalid_grant면 연동이 만료된 것이라 미연동으로 본다(FE 재연동 CTA).
            boolean connected = e.getErrorCode() != ErrorCode.GOOGLE_INVALID_GRANT;
            return new EventsResult(connected, List.of(),
                    Optional.of(new FeedError("google-events", e.getErrorCode().getMessage())));
        } catch (RuntimeException e) {
            return new EventsResult(true, List.of(),
                    Optional.of(new FeedError("google-events", "일정을 불러오지 못했습니다.")));
        }
    }

    private ReviewsResult fetchReviews(Long memberId, LocalDate from, LocalDate to) {
        try {
            List<PlannerReview> reviews = reviewQueryService.findCalendar(memberId, from, to).reviews().stream()
                    .map(PlannerCalendarService::toFeedReview)
                    .toList();
            return new ReviewsResult(reviews, Optional.empty());
        } catch (RuntimeException e) {
            return new ReviewsResult(List.of(), Optional.of(new FeedError("reviews", "복습을 불러오지 못했습니다.")));
        }
    }

    private TasksResult fetchTasks(Long memberId, LocalDate from, LocalDate to) {
        try {
            List<PlannerTask> tasks = googleTasksQueryService.listTasks(memberId, false).stream()
                    .filter(task -> withinRange(task.due(), from, to))
                    .toList();
            return new TasksResult(tasks, Optional.empty());
        } catch (CustomException e) {
            // 미연동/연동만료는 오류가 아니라 '할 일 없음'으로 본다(일정 소스와 동일 처리, partial 미표기).
            if (e.getErrorCode() == ErrorCode.GOOGLE_NOT_CONNECTED
                    || e.getErrorCode() == ErrorCode.GOOGLE_INVALID_GRANT) {
                return new TasksResult(List.of(), Optional.empty());
            }
            return new TasksResult(List.of(), Optional.of(new FeedError("google-tasks", "할 일을 불러오지 못했습니다.")));
        } catch (RuntimeException e) {
            return new TasksResult(List.of(), Optional.of(new FeedError("google-tasks", "할 일을 불러오지 못했습니다.")));
        }
    }

    /** due(날짜) 마감이 [from, to] 구간 안인 할 일만 캘린더에 배치한다(마감 없는 할 일은 제외). */
    private static boolean withinRange(String due, LocalDate from, LocalDate to) {
        if (due == null) {
            return false;
        }
        LocalDate dueDate = LocalDate.parse(due);
        return !dueDate.isBefore(from) && !dueDate.isAfter(to);
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

    private record EventsResult(boolean connected, List<PlannerEvent> events, Optional<FeedError> error) {
    }

    private record ReviewsResult(List<PlannerReview> reviews, Optional<FeedError> error) {
    }

    private record TasksResult(List<PlannerTask> tasks, Optional<FeedError> error) {
    }
}
