package ds.project.orino.planner.flashcard.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.entity.FlashcardType;
import ds.project.orino.domain.planner.flashcard.repository.FlashcardRepository;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.planner.flashcard.dto.FlashcardCreateRequest;
import ds.project.orino.planner.flashcard.dto.FlashcardCreateResponse;
import ds.project.orino.planner.flashcard.dto.FlashcardResponse;
import ds.project.orino.planner.flashcard.dto.FlashcardUpdateRequest;
import ds.project.orino.planner.flashcard.dto.OrderingItem;
import ds.project.orino.planner.review.dto.ReviewScheduleView;
import ds.project.orino.planner.review.service.ReviewMirrorService;
import ds.project.orino.core.time.UserTimeZone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ds.project.orino.domain.planner.flashcard.entity.FlashcardType.ORDERING;

@Service
@Transactional(readOnly = true)
public class FlashcardService {

    private final FlashcardRepository flashcardRepository;
    private final ReviewScheduleRepository reviewScheduleRepository;
    private final StudyMaterialRepository studyMaterialRepository;
    private final ReviewMirrorService reviewMirrorService;
    private final FlashcardItemsCodec itemsCodec;
    private final Clock clock;

    public FlashcardService(FlashcardRepository flashcardRepository,
                            ReviewScheduleRepository reviewScheduleRepository,
                            StudyMaterialRepository studyMaterialRepository,
                            ReviewMirrorService reviewMirrorService,
                            FlashcardItemsCodec itemsCodec,
                            Clock clock) {
        this.flashcardRepository = flashcardRepository;
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.studyMaterialRepository = studyMaterialRepository;
        this.reviewMirrorService = reviewMirrorService;
        this.itemsCodec = itemsCodec;
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
                    return FlashcardResponse.of(c, itemsCodec.parse(c.getItems()),
                            next == null ? null : ReviewScheduleView.nextReview(next));
                })
                .toList();
    }

    @Transactional
    public FlashcardCreateResponse create(Long memberId, Long materialId, FlashcardCreateRequest request) {
        requireOwnedMaterial(memberId, materialId);

        Flashcard saved = flashcardRepository.save(buildCard(memberId, materialId, request));
        ZoneId zone = UserTimeZone.get();
        LocalDate today = clock.instant().atZone(zone).toLocalDate();
        ReviewSchedule firstReview = reviewScheduleRepository.save(
                ReviewSchedule.firstReview(memberId, saved.getId(), today, zone));

        // 첫 복습 dueDate를 보조 캘린더에 미러(커밋 후, 미러 활성 시에만)
        reviewMirrorService.reconcileAfterCommit(memberId,
                List.of(firstReview.getScheduledAt().atZone(zone).toLocalDate()), zone);

        return new FlashcardCreateResponse(
                FlashcardResponse.withoutReview(saved, itemsCodec.parse(saved.getItems())),
                ReviewScheduleView.firstReview(firstReview));
    }

    @Transactional
    public FlashcardResponse update(Long memberId, Long flashcardId, FlashcardUpdateRequest request) {
        if (request.type() == null && request.front() == null
                && request.back() == null && request.items() == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
        Flashcard card = getOwnedFlashcard(memberId, flashcardId);

        // type 생략 시 기존 종류 유지. 전환 시 대상 종류 제약을 만족해야 한다.
        FlashcardType targetType = request.type() != null ? request.type() : card.getType();

        if (request.front() != null) {
            card.updateFront(request.front());
        }
        if (targetType == ORDERING) {
            // items 미지정이면 기존 항목 유지. (BASIC→ORDERING 전환이면 기존 items가 없어 반드시 지정해야 함)
            List<OrderingItem> items = request.items() != null
                    ? request.items()
                    : itemsCodec.parse(card.getItems());
            validateOrdering(items);
            card.changeToOrdering(itemsCodec.serialize(items));
        } else {
            // back 미지정이면 기존 back 유지. (ORDERING→BASIC 전환이면 기존 back이 없어 반드시 지정해야 함)
            String back = request.back() != null ? request.back() : card.getBack();
            validateBasicBack(back);
            card.changeToBasic(back);
        }
        return FlashcardResponse.withoutReview(card, itemsCodec.parse(card.getItems()));
    }

    /** 종류별 제약을 검증하고 해당 종류의 카드 엔티티를 만든다. */
    private Flashcard buildCard(Long memberId, Long materialId, FlashcardCreateRequest request) {
        if (request.typeOrDefault() == ORDERING) {
            validateOrdering(request.items());
            return Flashcard.ordering(memberId, materialId, request.front(),
                    itemsCodec.serialize(request.items()));
        }
        validateBasicBack(request.back());
        return new Flashcard(memberId, materialId, request.front(), request.back());
    }

    /** BASIC: back 필수(1~1000자). 위반 시 SP-ERR-002. */
    private static void validateBasicBack(String back) {
        if (back == null || back.isBlank() || back.length() > 1000) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }

    /** ORDERING: items 3~7개, 각 text 1~1000자, id 카드 내 유일. 위반 시 SP-ERR-002. */
    private static void validateOrdering(List<OrderingItem> items) {
        if (items == null || items.size() < 3 || items.size() > 7) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        Set<String> ids = new HashSet<>();
        for (OrderingItem item : items) {
            if (item == null
                    || item.id() == null || item.id().isBlank()
                    || item.text() == null || item.text().isBlank() || item.text().length() > 1000) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }
            if (!ids.add(item.id())) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }
        }
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
