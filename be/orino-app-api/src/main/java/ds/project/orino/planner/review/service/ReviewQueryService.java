package ds.project.orino.planner.review.service;

import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.entity.FlashcardType;
import ds.project.orino.domain.planner.flashcard.repository.FlashcardRepository;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.Rating;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.common.page.KeysetCursor;
import ds.project.orino.planner.review.dto.CalendarReviewFlashcard;
import ds.project.orino.planner.review.dto.CalendarReviewItem;
import ds.project.orino.planner.review.dto.CalendarReviewMaterial;
import ds.project.orino.planner.review.dto.CalendarReviewsResponse;
import ds.project.orino.planner.review.dto.CardType;
import ds.project.orino.planner.review.dto.CompletedReviewItem;
import ds.project.orino.planner.review.dto.CompletedReviewsResponse;
import ds.project.orino.planner.review.dto.PreviewView;
import ds.project.orino.planner.review.dto.ReviewCardMaterial;
import ds.project.orino.planner.review.dto.ReviewCardView;
import ds.project.orino.planner.review.dto.ReviewSummaryResponse;
import ds.project.orino.planner.review.dto.TodayReviewFlashcard;
import ds.project.orino.planner.review.dto.TodayReviewItem;
import ds.project.orino.planner.review.dto.TodayReviewMaterial;
import ds.project.orino.planner.review.dto.TodayReviewsResponse;
import ds.project.orino.planner.review.dto.UpcomingReviewItem;
import ds.project.orino.planner.review.dto.UpcomingReviewsResponse;
import ds.project.orino.planner.review.dto.WhenKind;
import ds.project.orino.planner.review.sm2.Sm2Calculator;
import ds.project.orino.planner.flashcard.service.FlashcardItemsCodec;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ds.project.orino.core.time.UserTimeZone;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReviewQueryService {

    private final ReviewScheduleRepository reviewScheduleRepository;
    private final FlashcardRepository flashcardRepository;
    private final StudyMaterialRepository studyMaterialRepository;
    private final FlashcardItemsCodec itemsCodec;
    private final Clock clock;

    public ReviewQueryService(ReviewScheduleRepository reviewScheduleRepository,
                              FlashcardRepository flashcardRepository,
                              StudyMaterialRepository studyMaterialRepository,
                              FlashcardItemsCodec itemsCodec,
                              Clock clock) {
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.flashcardRepository = flashcardRepository;
        this.studyMaterialRepository = studyMaterialRepository;
        this.itemsCodec = itemsCodec;
        this.clock = clock;
    }

    public TodayReviewsResponse findToday(Long memberId) {
        ZoneId zone = UserTimeZone.get();
        Instant now = clock.instant();
        LocalDate today = now.atZone(zone).toLocalDate();

        List<ReviewSchedule> reviews = reviewScheduleRepository
                .findAllByMemberIdAndStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
                        memberId, ReviewStatus.PENDING, now);

        if (reviews.isEmpty()) {
            return new TodayReviewsResponse(today, List.of());
        }

        List<Long> flashcardIds = reviews.stream().map(ReviewSchedule::getFlashcardId).distinct().toList();
        Map<Long, Flashcard> cardById = flashcardRepository.findAllByIdIn(flashcardIds).stream()
                .collect(Collectors.toMap(Flashcard::getId, Function.identity()));

        List<Long> materialIds = cardById.values().stream()
                .map(Flashcard::getMaterialId).distinct().toList();
        Map<Long, StudyMaterial> materialById = studyMaterialRepository.findAllByIdIn(materialIds).stream()
                .collect(Collectors.toMap(StudyMaterial::getId, Function.identity()));

        List<TodayReviewItem> items = reviews.stream()
                .map(r -> toItem(r, cardById, materialById, today, zone))
                .toList();

        return new TodayReviewsResponse(today, items);
    }

    static final int MAX_RANGE_DAYS = 100;

    public CalendarReviewsResponse findCalendar(Long memberId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)
                || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        ZoneId zone = UserTimeZone.get();
        Instant fromInstant = from.atStartOfDay(zone).toInstant();
        Instant toInstant = to.atTime(LocalTime.MAX).atZone(zone).toInstant();

        List<ReviewSchedule> reviews = reviewScheduleRepository
                .findAllByMemberIdAndScheduledAtBetweenOrderByScheduledAtAscIdAsc(
                        memberId, fromInstant, toInstant);

        if (reviews.isEmpty()) {
            return new CalendarReviewsResponse(from, to, List.of());
        }

        Map<Long, Flashcard> cardById = loadCards(reviews);
        Map<Long, StudyMaterial> materialById = loadMaterials(cardById);

        List<CalendarReviewItem> items = reviews.stream()
                .map(r -> {
                    Flashcard card = cardById.get(r.getFlashcardId());
                    StudyMaterial material = materialById.get(card.getMaterialId());
                    CalendarReviewFlashcard flashcardDto = CalendarReviewFlashcard.of(
                            card, CalendarReviewMaterial.of(material));
                    return new CalendarReviewItem(
                            r.getId(), r.getScheduledAt(), r.getStatus(),
                            r.getRating(), r.getSequence(), flashcardDto);
                })
                .toList();

        return new CalendarReviewsResponse(from, to, items);
    }

    private Map<Long, Flashcard> loadCards(List<ReviewSchedule> reviews) {
        List<Long> flashcardIds = reviews.stream().map(ReviewSchedule::getFlashcardId).distinct().toList();
        return flashcardRepository.findAllByIdIn(flashcardIds).stream()
                .collect(Collectors.toMap(Flashcard::getId, Function.identity()));
    }

    private Map<Long, StudyMaterial> loadMaterials(Map<Long, Flashcard> cardById) {
        List<Long> materialIds = cardById.values().stream()
                .map(Flashcard::getMaterialId).distinct().toList();
        return studyMaterialRepository.findAllByIdIn(materialIds).stream()
                .collect(Collectors.toMap(StudyMaterial::getId, Function.identity()));
    }

    private TodayReviewItem toItem(ReviewSchedule r,
                                   Map<Long, Flashcard> cardById,
                                   Map<Long, StudyMaterial> materialById,
                                   LocalDate today,
                                   ZoneId zone) {
        Flashcard card = cardById.get(r.getFlashcardId());
        StudyMaterial material = materialById.get(card.getMaterialId());
        TodayReviewFlashcard flashcardDto = TodayReviewFlashcard.of(
                card, itemsCodec.parse(card.getItems()), TodayReviewMaterial.of(material));
        int delayDays = (int) ChronoUnit.DAYS.between(r.getScheduledAt().atZone(zone).toLocalDate(), today);
        return new TodayReviewItem(
                r.getId(), r.getScheduledAt(), delayDays,
                r.getSequence(), r.getIntervalDays(), r.getEaseFactor(),
                flashcardDto, preview(r, delayDays));
    }

    /** 지금 각 버튼을 누르면 다음 복습이 며칠 뒤인지. 밀린 일수가 간격에 반영되므로 함께 넘긴다. */
    private PreviewView preview(ReviewSchedule r, int delayDays) {
        return new PreviewView(
                Sm2Calculator.next(r.getIntervalDays(), r.getEaseFactor(), delayDays, Rating.AGAIN).intervalDays(),
                Sm2Calculator.next(r.getIntervalDays(), r.getEaseFactor(), delayDays, Rating.HARD).intervalDays(),
                Sm2Calculator.next(r.getIntervalDays(), r.getEaseFactor(), delayDays, Rating.GOOD).intervalDays(),
                Sm2Calculator.next(r.getIntervalDays(), r.getEaseFactor(), delayDays, Rating.EASY).intervalDays());
    }

    // ===== v2.5 복습 허브 (조회 전용) =====

    /** 목록 페이지 기본/최대 크기. */
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;

    /** estimatedMinutes 대략치 계산용 카드당 상수(초). */
    static final int SECONDS_PER_CARD = 40;

    /** 복습 허브 현황 집계. counts는 목록 길이가 아니라 서버 총계다. */
    public ReviewSummaryResponse summary(Long memberId) {
        ZoneId zone = UserTimeZone.get();
        Instant now = clock.instant();
        LocalDate today = now.atZone(zone).toLocalDate();
        Instant todayStart = today.atStartOfDay(zone).toInstant();
        Instant tomorrowStart = today.plusDays(1).atStartOfDay(zone).toInstant();

        List<ReviewSchedule> pending = reviewScheduleRepository
                .findAllByMemberIdAndStatus(memberId, ReviewStatus.PENDING);

        long nowCount = pending.stream().filter(r -> !r.getScheduledAt().isAfter(now)).count();
        long overdueCount = pending.stream().filter(r -> r.getScheduledAt().isBefore(todayStart)).count();
        long upcomingCount = pending.size();
        long doneToday = reviewScheduleRepository
                .countByMemberIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                        memberId, ReviewStatus.COMPLETED, todayStart, tomorrowStart);

        int estimatedMinutes = (int) Math.round(nowCount * SECONDS_PER_CARD / 60.0);

        List<ReviewSummaryResponse.Material> materials =
                buildMaterialSummaries(pending, now, todayStart, tomorrowStart, today, zone);

        ReviewSummaryResponse.Counts counts =
                new ReviewSummaryResponse.Counts(nowCount, overdueCount, upcomingCount, doneToday);
        return new ReviewSummaryResponse(today, counts, estimatedMinutes, materials);
    }

    private List<ReviewSummaryResponse.Material> buildMaterialSummaries(
            List<ReviewSchedule> pending, Instant now, Instant todayStart, Instant tomorrowStart,
            LocalDate today, ZoneId zone) {
        if (pending.isEmpty()) {
            return List.of();
        }
        Map<Long, Flashcard> cardById = loadCards(pending);
        Map<Long, StudyMaterial> materialById = loadMaterials(cardById);

        Map<Long, List<ReviewSchedule>> byMaterial = pending.stream()
                .collect(Collectors.groupingBy(r -> cardById.get(r.getFlashcardId()).getMaterialId()));

        return byMaterial.entrySet().stream()
                .map(e -> {
                    StudyMaterial material = materialById.get(e.getKey());
                    List<ReviewSchedule> rows = e.getValue();
                    long due = rows.stream()
                            .filter(r -> !r.getScheduledAt().isBefore(todayStart)
                                    && r.getScheduledAt().isBefore(tomorrowStart))
                            .count();
                    long overdue = rows.stream()
                            .filter(r -> r.getScheduledAt().isBefore(todayStart))
                            .count();
                    Instant earliest = rows.stream()
                            .map(ReviewSchedule::getScheduledAt)
                            .min(Comparator.naturalOrder())
                            .orElseThrow();
                    return new ReviewSummaryResponse.Material(
                            material.getId(), material.getTitle(), due, overdue,
                            nextLabel(earliest, now, today, zone));
                })
                .sorted(Comparator
                        .comparingLong((ReviewSummaryResponse.Material m) -> m.due() + m.overdue()).reversed()
                        .thenComparing(ReviewSummaryResponse.Material::name))
                .toList();
    }

    /** 자료별 다음 due 대략 라벨. 목록의 세밀한 시각 라벨은 FE가 포맷하지만, 레일 자료 행은 서버가 요약한다. */
    private String nextLabel(Instant earliest, Instant now, LocalDate today, ZoneId zone) {
        if (!earliest.isAfter(now)) {
            return "지금";
        }
        LocalDate date = earliest.atZone(zone).toLocalDate();
        if (date.equals(today)) {
            return "오늘";
        }
        String md = String.format("%02d/%02d", date.getMonthValue(), date.getDayOfMonth());
        return date.equals(today.plusDays(1)) ? "내일 " + md : md;
    }

    /** 앞으로의 복습 목록 — 전 PENDING, scheduled_at ASC, 커서 keyset 페이징 + scope/자료/기간/종류 필터. */
    public UpcomingReviewsResponse findUpcoming(Long memberId, String scope, Long materialId,
                                                String when, String type, String cursor, Integer size) {
        ZoneId zone = UserTimeZone.get();
        Instant now = clock.instant();
        LocalDate today = now.atZone(zone).toLocalDate();
        Instant todayStart = today.atStartOfDay(zone).toInstant();
        int pageSize = clampSize(size);

        Instant upperBound = upperBound(scope, when, today, zone);
        TypeFilter typeFilter = typeFilter(type);
        KeysetCursor c = cursor == null ? null : KeysetCursor.decode(cursor);

        List<ReviewSchedule> rows = reviewScheduleRepository.findUpcoming(
                memberId, materialId, upperBound, typeFilter.cardType(), typeFilter.siblingRequired(),
                c == null ? null : c.at(), c == null ? null : c.id(),
                PageRequest.of(0, pageSize + 1));

        boolean hasNext = rows.size() > pageSize;
        List<ReviewSchedule> page = hasNext ? rows.subList(0, pageSize) : rows;

        Map<Long, Flashcard> cardById = loadCards(page);
        Map<Long, StudyMaterial> materialById = loadMaterials(cardById);

        List<UpcomingReviewItem> items = page.stream()
                .map(r -> {
                    Flashcard card = cardById.get(r.getFlashcardId());
                    StudyMaterial material = materialById.get(card.getMaterialId());
                    ReviewCardView flashcard = ReviewCardView.of(card, ReviewCardMaterial.of(material));
                    return new UpcomingReviewItem(
                            r.getId(), r.getScheduledAt(),
                            whenKind(r.getScheduledAt(), now, today, zone),
                            r.getScheduledAt().isBefore(todayStart),
                            CardType.from(card), flashcard);
                })
                .toList();

        String nextCursor = null;
        if (hasNext) {
            ReviewSchedule last = page.get(page.size() - 1);
            nextCursor = new KeysetCursor(last.getScheduledAt(), last.getId()).encode();
        }

        // 첫 페이지에서만 COUNT — 이후 페이지는 필터가 같으므로 클라이언트가 첫 값을 유지한다.
        Long totalCount = c == null
                ? reviewScheduleRepository.countUpcoming(
                        memberId, materialId, upperBound,
                        typeFilter.cardType(), typeFilter.siblingRequired())
                : null;

        return new UpcomingReviewsResponse(today, items, nextCursor, hasNext, totalCount);
    }

    /** 완료된 복습 목록 — COMPLETED, completed_at DESC, 커서 keyset 페이징 + 자료/평가 필터. */
    public CompletedReviewsResponse findCompleted(Long memberId, Long materialId, String grade,
                                                  String cursor, Integer size) {
        int pageSize = clampSize(size);
        Rating rating = grade(grade);
        KeysetCursor c = cursor == null ? null : KeysetCursor.decode(cursor);

        List<ReviewSchedule> rows = reviewScheduleRepository.findCompleted(
                memberId, materialId, rating,
                c == null ? null : c.at(), c == null ? null : c.id(),
                PageRequest.of(0, pageSize + 1));

        boolean hasNext = rows.size() > pageSize;
        List<ReviewSchedule> page = hasNext ? rows.subList(0, pageSize) : rows;

        Map<Long, Flashcard> cardById = loadCards(page);
        Map<Long, StudyMaterial> materialById = loadMaterials(cardById);

        List<CompletedReviewItem> items = page.stream()
                .map(r -> {
                    Flashcard card = cardById.get(r.getFlashcardId());
                    StudyMaterial material = materialById.get(card.getMaterialId());
                    ReviewCardView flashcard = ReviewCardView.of(card, ReviewCardMaterial.of(material));
                    return new CompletedReviewItem(
                            r.getId(), r.getCompletedAt(), r.getRating(), r.getSequence(),
                            CardType.from(card), flashcard);
                })
                .toList();

        String nextCursor = null;
        if (hasNext) {
            ReviewSchedule last = page.get(page.size() - 1);
            nextCursor = new KeysetCursor(last.getCompletedAt(), last.getId()).encode();
        }

        // 첫 페이지에서만 COUNT — 이후 페이지는 필터가 같으므로 클라이언트가 첫 값을 유지한다.
        Long totalCount = c == null
                ? reviewScheduleRepository.countCompleted(memberId, materialId, rating)
                : null;

        return new CompletedReviewsResponse(items, nextCursor, hasNext, totalCount);
    }

    private int clampSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        return Math.max(1, Math.min(size, MAX_SIZE));
    }

    private WhenKind whenKind(Instant at, Instant now, LocalDate today, ZoneId zone) {
        if (!at.isAfter(now)) {
            return WhenKind.NOW;
        }
        return at.atZone(zone).toLocalDate().equals(today) ? WhenKind.TODAY : WhenKind.FUTURE;
    }

    /** scope와 when 상한을 하나로 합친다(둘 다 scheduled_at 상한이므로 더 좁은 쪽). null이면 상한 없음. */
    private Instant upperBound(String scope, String when, LocalDate today, ZoneId zone) {
        Instant fromScope = scopeBound(scope, today, zone);
        Instant fromWhen = whenBound(when, today, zone);
        if (fromScope == null) {
            return fromWhen;
        }
        if (fromWhen == null) {
            return fromScope;
        }
        return fromScope.isBefore(fromWhen) ? fromScope : fromWhen;
    }

    private Instant scopeBound(String scope, LocalDate today, ZoneId zone) {
        if (scope == null || scope.equals("all")) {
            return null;
        }
        return switch (scope) {
            case "today" -> today.plusDays(1).atStartOfDay(zone).toInstant();
            case "overdue" -> today.atStartOfDay(zone).toInstant();
            default -> throw new CustomException(ErrorCode.INVALID_REQUEST);
        };
    }

    private Instant whenBound(String when, LocalDate today, ZoneId zone) {
        if (when == null || when.equals("all")) {
            return null;
        }
        return switch (when) {
            case "today" -> today.plusDays(1).atStartOfDay(zone).toInstant();
            case "3d" -> today.plusDays(3).atStartOfDay(zone).toInstant();
            case "7d" -> today.plusDays(7).atStartOfDay(zone).toInstant();
            default -> throw new CustomException(ErrorCode.INVALID_REQUEST);
        };
    }

    private TypeFilter typeFilter(String type) {
        if (type == null || type.equals("all")) {
            return new TypeFilter(null, null);
        }
        return switch (type) {
            case "basic" -> new TypeFilter(FlashcardType.BASIC, Boolean.FALSE);
            case "order" -> new TypeFilter(FlashcardType.ORDERING, null);
            case "pair" -> new TypeFilter(FlashcardType.BASIC, Boolean.TRUE);
            default -> throw new CustomException(ErrorCode.INVALID_REQUEST);
        };
    }

    private Rating grade(String grade) {
        if (grade == null || grade.equals("all")) {
            return null;
        }
        try {
            return Rating.valueOf(grade);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }

    /** 종류 필터를 쿼리 파라미터로 환산한 값. siblingRequired: TRUE=NOT NULL, FALSE=NULL, null=무관. */
    private record TypeFilter(FlashcardType cardType, Boolean siblingRequired) {
    }
}
