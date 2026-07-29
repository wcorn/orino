package ds.project.orino.planner.flashcard.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.common.page.KeysetCursor;
import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.entity.FlashcardType;
import ds.project.orino.domain.planner.flashcard.repository.FlashcardRepository;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.planner.flashcard.dto.FlashcardCreateRequest;
import ds.project.orino.planner.flashcard.dto.FlashcardCreateResponse;
import ds.project.orino.planner.flashcard.dto.FlashcardListResponse;
import ds.project.orino.planner.flashcard.dto.FlashcardResponse;
import ds.project.orino.planner.flashcard.dto.FlashcardUpdateRequest;
import ds.project.orino.planner.flashcard.dto.OrderingItem;
import ds.project.orino.planner.review.dto.ReviewScheduleView;
import ds.project.orino.planner.review.service.ReviewMirrorService;
import ds.project.orino.core.time.UserTimeZone;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

    /** 목록 페이지 기본/최대 크기. */
    static final int DEFAULT_SIZE = 30;
    static final int MAX_SIZE = 100;

    /**
     * 자료의 카드 목록 — {@code (created_at, id)} 커서 keyset 페이징 + 검색/종류/복습 상태/정렬.
     * 필터·정렬·페이징의 SSOT는 서버다(FE에서 재필터링하면 로드된 페이지에만 걸려 결과가 어긋난다).
     */
    public FlashcardListResponse findByMaterialId(Long memberId, Long materialId, String q,
                                                  String type, String review, String sort,
                                                  String cursor, Integer size) {
        requireOwnedMaterial(memberId, materialId);

        int pageSize = clampSize(size);
        String pattern = likePattern(q);
        TypeFilter typeFilter = typeFilter(type);
        ReviewWindow window = reviewWindow(review);
        boolean asc = ascending(sort);
        KeysetCursor c = cursor == null ? null : KeysetCursor.decode(cursor);

        Sort order = Sort.by(asc ? Sort.Direction.ASC : Sort.Direction.DESC, "createdAt", "id");
        List<Flashcard> rows = flashcardRepository.findPage(
                materialId, pattern, typeFilter.cardType(), typeFilter.siblingRequired(),
                window.from(), window.before(), asc,
                c == null ? null : c.at(), c == null ? null : c.id(),
                PageRequest.of(0, pageSize + 1, order));

        boolean hasNext = rows.size() > pageSize;
        List<Flashcard> page = hasNext ? rows.subList(0, pageSize) : rows;

        long totalCount = flashcardRepository.countPage(
                materialId, pattern, typeFilter.cardType(), typeFilter.siblingRequired(),
                window.from(), window.before());

        Map<Long, ReviewSchedule> nextReviewByCard = page.isEmpty()
                ? Map.of()
                : loadNextReviewByCard(page);
        List<FlashcardResponse> flashcards = page.stream()
                .map(c2 -> {
                    ReviewSchedule next = nextReviewByCard.get(c2.getId());
                    return FlashcardResponse.of(c2, itemsCodec.parse(c2.getItems()),
                            next == null ? null : ReviewScheduleView.nextReview(next));
                })
                .toList();

        String nextCursor = null;
        if (hasNext) {
            Flashcard last = page.get(page.size() - 1);
            nextCursor = new KeysetCursor(last.getCreatedAt(), last.getId()).encode();
        }
        return new FlashcardListResponse(flashcards, totalCount, nextCursor, hasNext);
    }

    private int clampSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        return Math.max(1, Math.min(size, MAX_SIZE));
    }

    /** 검색어를 소문자 LIKE 패턴으로. 공백뿐이면 필터 없음(null). */
    private String likePattern(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        return "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
    }

    /** 정렬 축은 생성순만. 복습 임박순은 복습 허브(/reviews/upcoming)가 담당한다. */
    private boolean ascending(String sort) {
        if (sort == null || sort.equals("created_asc")) {
            return true;
        }
        if (sort.equals("created_desc")) {
            return false;
        }
        throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    /** 종류 필터 — 복습 허브의 종류 축과 동일(pair = BASIC + siblingGroupId NOT NULL 파생). */
    private TypeFilter typeFilter(String type) {
        if (type == null || type.equals("all")) {
            return new TypeFilter(null, null);
        }
        return switch (type) {
            case "basic" -> new TypeFilter(FlashcardType.BASIC, Boolean.FALSE);
            case "order" -> new TypeFilter(ORDERING, null);
            case "pair" -> new TypeFilter(FlashcardType.BASIC, Boolean.TRUE);
            default -> throw new CustomException(ErrorCode.INVALID_REQUEST);
        };
    }

    /**
     * 복습 상태 필터를 PENDING 시각 구간 {@code [from, before)}로 환산한다.
     * 카드당 PENDING은 보통 1개이므로 "구간에 걸친 PENDING이 존재"는 사실상 nextReview 기준과 같다.
     */
    private ReviewWindow reviewWindow(String review) {
        if (review == null || review.equals("all")) {
            return new ReviewWindow(null, null);
        }
        ZoneId zone = UserTimeZone.get();
        LocalDate today = clock.instant().atZone(zone).toLocalDate();
        Instant todayStart = today.atStartOfDay(zone).toInstant();
        Instant tomorrowStart = today.plusDays(1).atStartOfDay(zone).toInstant();
        return switch (review) {
            case "overdue" -> new ReviewWindow(null, todayStart);
            case "today" -> new ReviewWindow(todayStart, tomorrowStart);
            case "upcoming" -> new ReviewWindow(tomorrowStart, null);
            default -> throw new CustomException(ErrorCode.INVALID_REQUEST);
        };
    }

    /** 종류 필터를 쿼리 파라미터로 환산한 값. siblingRequired: TRUE=NOT NULL, FALSE=NULL, null=무관. */
    private record TypeFilter(FlashcardType cardType, Boolean siblingRequired) {
    }

    /** 복습 상태 필터의 PENDING 시각 구간. 경계는 각각 null이면 무제한. */
    private record ReviewWindow(Instant from, Instant before) {
    }

    @Transactional
    public FlashcardCreateResponse create(Long memberId, Long materialId, FlashcardCreateRequest request) {
        requireOwnedMaterial(memberId, materialId);
        ZoneId zone = UserTimeZone.get();
        LocalDate today = clock.instant().atZone(zone).toLocalDate();

        if (request.isBidirectional()) {
            return createBidirectional(memberId, materialId, request, today, zone);
        }

        Flashcard saved = flashcardRepository.save(buildCard(memberId, materialId, request));
        ReviewSchedule firstReview = reviewScheduleRepository.save(
                ReviewSchedule.firstReview(memberId, saved.getId(), today, zone));

        // 첫 복습 dueDate를 보조 캘린더에 미러(커밋 후, 미러 활성 시에만)
        reviewMirrorService.reconcileAfterCommit(memberId,
                List.of(firstReview.getScheduledAt().atZone(zone).toLocalDate()), zone);

        return FlashcardCreateResponse.of(
                FlashcardResponse.withoutReview(saved, itemsCodec.parse(saved.getItems())),
                ReviewScheduleView.firstReview(firstReview));
    }

    /**
     * 양방향 짝 카드 생성: A(front→back)·B(back→front)를 같은 siblingGroupId로 만들고
     * 첫 복습을 엇갈리게(A today+1, B today+2) 예약한다. 응답에 짝(B)을 sibling으로 함께 담는다.
     */
    private FlashcardCreateResponse createBidirectional(Long memberId, Long materialId,
                                                        FlashcardCreateRequest request,
                                                        LocalDate today, ZoneId zone) {
        validateBidirectional(request);

        // A: 그룹 키 = 자기 id (짝이 공유)
        Flashcard a = flashcardRepository.save(
                new Flashcard(memberId, materialId, request.front(), request.back()));
        a.assignSiblingGroup(a.getId());
        // B: front/back 뒤집고 같은 그룹
        Flashcard b = new Flashcard(memberId, materialId, request.back(), request.front());
        b.assignSiblingGroup(a.getId());
        b = flashcardRepository.save(b);

        ReviewSchedule reviewA = reviewScheduleRepository.save(
                ReviewSchedule.firstReview(memberId, a.getId(), today, zone));
        ReviewSchedule reviewB = reviewScheduleRepository.save(
                ReviewSchedule.firstReview(memberId, b.getId(), today, zone, 2));

        reviewMirrorService.reconcileAfterCommit(memberId, List.of(
                reviewA.getScheduledAt().atZone(zone).toLocalDate(),
                reviewB.getScheduledAt().atZone(zone).toLocalDate()), zone);

        FlashcardCreateResponse sibling = FlashcardCreateResponse.of(
                FlashcardResponse.withoutReview(b, null), ReviewScheduleView.firstReview(reviewB));
        return new FlashcardCreateResponse(
                FlashcardResponse.withoutReview(a, null),
                ReviewScheduleView.firstReview(reviewA),
                sibling);
    }

    /** 양방향은 BASIC + back 존재(1~1000자)일 때만. ORDERING/back 없음이면 SP-ERR-002. */
    private static void validateBidirectional(FlashcardCreateRequest request) {
        if (request.typeOrDefault() != FlashcardType.BASIC) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        validateBasicBack(request.back());
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
