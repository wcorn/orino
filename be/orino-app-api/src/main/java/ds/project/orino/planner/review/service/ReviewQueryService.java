package ds.project.orino.planner.review.service;

import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.repository.FlashcardRepository;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.Rating;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.planner.review.dto.PreviewView;
import ds.project.orino.planner.review.dto.TodayReviewFlashcard;
import ds.project.orino.planner.review.dto.TodayReviewItem;
import ds.project.orino.planner.review.dto.TodayReviewMaterial;
import ds.project.orino.planner.review.dto.TodayReviewsResponse;
import ds.project.orino.planner.review.sm2.Sm2Calculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
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
    private final Clock clock;

    public ReviewQueryService(ReviewScheduleRepository reviewScheduleRepository,
                              FlashcardRepository flashcardRepository,
                              StudyMaterialRepository studyMaterialRepository,
                              Clock clock) {
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.flashcardRepository = flashcardRepository;
        this.studyMaterialRepository = studyMaterialRepository;
        this.clock = clock;
    }

    public TodayReviewsResponse findToday(Long memberId) {
        LocalDate today = LocalDate.now(clock);

        List<ReviewSchedule> reviews = reviewScheduleRepository
                .findAllByMemberIdAndStatusAndScheduledDateLessThanEqualOrderByScheduledDateAscIdAsc(
                        memberId, ReviewStatus.PENDING, today);

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
                .map(r -> toItem(r, cardById, materialById, today))
                .toList();

        return new TodayReviewsResponse(today, items);
    }

    private TodayReviewItem toItem(ReviewSchedule r,
                                   Map<Long, Flashcard> cardById,
                                   Map<Long, StudyMaterial> materialById,
                                   LocalDate today) {
        Flashcard card = cardById.get(r.getFlashcardId());
        StudyMaterial material = materialById.get(card.getMaterialId());
        TodayReviewFlashcard flashcardDto = TodayReviewFlashcard.of(card, TodayReviewMaterial.of(material));
        PreviewView preview = preview(r);
        int delayDays = (int) ChronoUnit.DAYS.between(r.getScheduledDate(), today);
        return new TodayReviewItem(
                r.getId(), r.getScheduledDate(), delayDays,
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
