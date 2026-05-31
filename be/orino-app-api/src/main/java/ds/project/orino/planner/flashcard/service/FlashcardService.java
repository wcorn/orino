package ds.project.orino.planner.flashcard.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.repository.FlashcardRepository;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.planner.flashcard.dto.FlashcardCreateRequest;
import ds.project.orino.planner.flashcard.dto.FlashcardCreateResponse;
import ds.project.orino.planner.flashcard.dto.FlashcardResponse;
import ds.project.orino.planner.flashcard.dto.FlashcardUpdateRequest;
import ds.project.orino.planner.review.dto.ReviewScheduleView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class FlashcardService {

    private final FlashcardRepository flashcardRepository;
    private final ReviewScheduleRepository reviewScheduleRepository;
    private final StudyMaterialRepository studyMaterialRepository;
    private final Clock clock;

    public FlashcardService(FlashcardRepository flashcardRepository,
                            ReviewScheduleRepository reviewScheduleRepository,
                            StudyMaterialRepository studyMaterialRepository,
                            Clock clock) {
        this.flashcardRepository = flashcardRepository;
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.studyMaterialRepository = studyMaterialRepository;
        this.clock = clock;
    }

    public List<FlashcardResponse> findAllByMaterialId(Long memberId, Long materialId) {
        requireOwnedMaterial(memberId, materialId);

        List<Flashcard> cards = flashcardRepository.findAllByMaterialIdOrderByCreatedAtAscIdAsc(materialId);
        if (cards.isEmpty()) {
            return List.of();
        }

        Map<Long, ReviewSchedule> nextReviewByCard = loadNextReviewByCard(cards);
        return cards.stream()
                .map(c -> {
                    ReviewSchedule next = nextReviewByCard.get(c.getId());
                    return FlashcardResponse.of(c, next == null ? null : ReviewScheduleView.nextReview(next));
                })
                .toList();
    }

    @Transactional
    public FlashcardCreateResponse create(Long memberId, Long materialId, FlashcardCreateRequest request) {
        requireOwnedMaterial(memberId, materialId);

        Flashcard saved = flashcardRepository.save(
                new Flashcard(memberId, materialId, request.front(), request.back()));
        LocalDate today = LocalDate.now(clock);
        ReviewSchedule firstReview = reviewScheduleRepository.save(
                ReviewSchedule.firstReview(memberId, saved.getId(), today));

        return new FlashcardCreateResponse(
                FlashcardResponse.withoutReview(saved),
                ReviewScheduleView.firstReview(firstReview));
    }

    @Transactional
    public FlashcardResponse update(Long memberId, Long flashcardId, FlashcardUpdateRequest request) {
        if (request.front() == null && request.back() == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
        Flashcard card = getOwnedFlashcard(memberId, flashcardId);
        if (request.front() != null) {
            card.updateFront(request.front());
        }
        if (request.back() != null) {
            card.updateBack(request.back());
        }
        return FlashcardResponse.withoutReview(card);
    }

    @Transactional
    public void delete(Long memberId, Long flashcardId) {
        Flashcard card = getOwnedFlashcard(memberId, flashcardId);
        flashcardRepository.delete(card);
    }

    private void requireOwnedMaterial(Long memberId, Long materialId) {
        studyMaterialRepository.findByIdAndMemberId(materialId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Flashcard getOwnedFlashcard(Long memberId, Long flashcardId) {
        return flashcardRepository.findByIdAndMemberId(flashcardId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Map<Long, ReviewSchedule> loadNextReviewByCard(List<Flashcard> cards) {
        List<Long> ids = cards.stream().map(Flashcard::getId).toList();
        List<ReviewSchedule> reviews = reviewScheduleRepository
                .findAllByFlashcardIdInAndStatusOrderByScheduledAtAscIdAsc(ids, ReviewStatus.PENDING);
        Map<Long, ReviewSchedule> firstByCard = new HashMap<>();
        for (ReviewSchedule r : reviews) {
            firstByCard.putIfAbsent(r.getFlashcardId(), r);
        }
        return firstByCard;
    }
}
