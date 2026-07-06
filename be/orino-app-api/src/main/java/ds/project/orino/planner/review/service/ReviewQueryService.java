package ds.project.orino.planner.review.service;

import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.repository.FlashcardRepository;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.Rating;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.planner.review.dto.CalendarReviewFlashcard;
import ds.project.orino.planner.review.dto.CalendarReviewItem;
import ds.project.orino.planner.review.dto.CalendarReviewMaterial;
import ds.project.orino.planner.review.dto.CalendarReviewsResponse;
import ds.project.orino.planner.review.dto.PreviewView;
import ds.project.orino.planner.review.dto.TodayReviewFlashcard;
import ds.project.orino.planner.review.dto.TodayReviewItem;
import ds.project.orino.planner.review.dto.TodayReviewMaterial;
import ds.project.orino.planner.review.dto.TodayReviewsResponse;
import ds.project.orino.planner.review.sm2.Sm2Calculator;
import ds.project.orino.planner.flashcard.service.FlashcardItemsCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ds.project.orino.core.time.UserTimeZone;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
        PreviewView preview = preview(r);
        int delayDays = (int) ChronoUnit.DAYS.between(r.getScheduledAt().atZone(zone).toLocalDate(), today);
        return new TodayReviewItem(
                r.getId(), r.getScheduledAt(), delayDays,
                r.getSequence(), r.getIntervalDays(), r.getEaseFactor(),
                flashcardDto, preview);
    }

    private PreviewView preview(ReviewSchedule r) {
        int newSeq = r.getSequence() + 1;
        return new PreviewView(
                Sm2Calculator.next(newSeq, r.getIntervalDays(), r.getEaseFactor(), Rating.AGAIN).intervalDays(),
                Sm2Calculator.next(newSeq, r.getIntervalDays(), r.getEaseFactor(), Rating.HARD).intervalDays(),
                Sm2Calculator.next(newSeq, r.getIntervalDays(), r.getEaseFactor(), Rating.GOOD).intervalDays(),
                Sm2Calculator.next(newSeq, r.getIntervalDays(), r.getEaseFactor(), Rating.EASY).intervalDays());
    }
}
