package ds.project.orino.planner.travel.prep.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.PrepCategory;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripPrepItem;
import ds.project.orino.domain.planner.travel.repository.TripPrepItemRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.day.service.TripClock;
import ds.project.orino.planner.travel.day.service.TripDayService;
import ds.project.orino.planner.travel.prep.dto.PrepField;
import ds.project.orino.planner.travel.prep.dto.PrepGroup;
import ds.project.orino.planner.travel.prep.dto.PrepItemMutation;
import ds.project.orino.planner.travel.prep.dto.PrepItemView;
import ds.project.orino.planner.travel.prep.dto.PrepRequests;
import ds.project.orino.planner.travel.prep.dto.PrepResponse;
import ds.project.orino.planner.travel.prep.dto.PrepSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 준비 CRUD(명세 v2.2 §11~§13 · API §10).
 *
 * <p><b>기한은 D−N으로만 산다.</b> 날짜는 저장하지 않고 조회할 때마다 출발일에서 뺀다 —
 * 그래서 출발일을 하루 당기면 기한이 통째로 따라 움직이고, 고쳐 줄 행이 하나도 없다(D-29).
 *
 * <p>기준 "오늘"은 서버 로컬 날짜가 아니라 <b>첫날 기준 도시의 오늘</b>이다. 여행 상태·D-day와
 * 같은 시계를 쓴다 — 여기만 다른 시계를 보면 출발 전날 밤에 화면의 D-day와 기한 경고가 하루
 * 어긋나고, 둘 다 그럴듯해 보여서 어느 쪽이 틀렸는지 알 수 없다.
 *
 * <p>집계({@link PrepSummary})는 <b>항상 저장 후 전체를 다시 세어</b> 만든다. 체크 하나에
 * 진행률·사이드바 배지·상단 경고가 같이 움직이는데, 바뀐 항목만 보고 ±1 하면 기한 지남처럼
 * 오늘에 의존하는 값이 조용히 어긋난다.
 */
@Service
@Transactional(readOnly = true)
public class PrepService {

    private final TripRepository tripRepository;
    private final TripPrepItemRepository prepRepository;
    private final TripDayService tripDayService;
    private final Clock clock;

    public PrepService(TripRepository tripRepository,
                       TripPrepItemRepository prepRepository,
                       TripDayService tripDayService,
                       Clock clock) {
        this.tripRepository = tripRepository;
        this.prepRepository = prepRepository;
        this.tripDayService = tripDayService;
        this.clock = clock;
    }

    /**
     * 화면 한 벌. 분류 4개는 <b>항목이 없어도 전부</b> 내려간다 — FE가 분류 목록을 따로 들면
     * 서버와 두 벌이 되고, 그때부터 다섯 번째 분류가 조용히 생긴다.
     */
    public PrepResponse get(Long memberId, Long tripId) {
        Trip trip = getOwnedTrip(memberId, tripId);
        LocalDate today = todayOf(trip);
        List<TripPrepItem> items = itemsOf(tripId);

        Map<PrepCategory, List<TripPrepItem>> byCategory = new LinkedHashMap<>();
        for (PrepCategory category : PrepCategory.values()) {
            byCategory.put(category, new ArrayList<>());
        }
        items.forEach(item -> byCategory.get(item.getCategory()).add(item));

        List<PrepGroup> groups = byCategory.entrySet().stream()
                .map(entry -> toGroup(entry.getKey(), entry.getValue(),
                        trip.getStartDate(), today))
                .toList();

        PrepSummary summary = summaryOf(items, trip.getStartDate(), today);
        return new PrepResponse(tripId, trip.getStartDate(),
                ChronoUnit.DAYS.between(today, trip.getStartDate()),
                summary.total(), summary.done(), summary.overdueCount(), groups);
    }

    /**
     * 항목 추가. {@code title}만 있으면 된다.
     *
     * <p><b>순서는 서버가 정한다</b> — 그 분류의 맨 뒤다. 클라이언트가 계산해 보내면 두 탭에서
     * 동시에 추가할 때 같은 자리를 두 항목이 갖는다.
     */
    @Transactional
    public PrepItemMutation create(Long memberId, Long tripId, PrepRequests.Create request) {
        Trip trip = getOwnedTrip(memberId, tripId);
        requireValidDue(request.dueDaysBefore());

        // 애매하면 할 일(§11). FE도 같은 기본값을 쓰지만 규칙은 서버에도 있어야 한다 —
        // 분류를 안 실어 보내는 클라이언트가 하나만 생겨도 그때부터 규칙이 갈린다.
        PrepCategory category = request.category() == null
                ? PrepCategory.TODO : request.category();

        TripPrepItem item = new TripPrepItem(tripId, memberId, category,
                request.title().trim(), nextOrder(tripId, category));
        item.changeQuantity(request.quantity());
        item.changeDueDaysBefore(request.dueDaysBefore());
        item.changeUrl(request.url());
        item.changeMemo(request.memo());

        return mutationOf(trip, prepRepository.save(item));
    }

    /**
     * 부분 수정. 체크 토글도 이 경로다.
     *
     * <p>분류를 먼저 옮기고 수량을 나중에 손댄다. 순서가 뒤집히면 「짐으로 옮기면서 수량 4」가
     * 옛 분류로 판정돼 수량만 사라진다.
     */
    @Transactional
    public PrepItemMutation patch(Long memberId, Long itemId, PrepRequests.Patch request) {
        TripPrepItem item = getOwnedItem(memberId, itemId);
        Trip trip = getOwnedTrip(memberId, item.getTripId());
        requireValidDue(request.dueDaysBefore());

        if (request.category() != null && request.category() != item.getCategory()) {
            // 새 분류의 맨 뒤로 다시 매긴다. 옛 순서를 들고 가면 그 분류에 이미 있던 항목과
            // 자리가 겹쳐 화면 순서가 저장할 때마다 흔들린다.
            item.changeCategory(request.category(), nextOrder(item.getTripId(),
                    request.category()));
        }
        if (request.title() != null && !request.title().isBlank()) {
            item.rename(request.title().trim());
        }
        if (request.done() != null) {
            item.check(request.done());
        }

        Set<PrepField> clear = request.clear() == null ? Set.of() : request.clear();
        applyDetail(clear.contains(PrepField.QUANTITY), request.quantity(),
                item::changeQuantity);
        applyDetail(clear.contains(PrepField.DUE_DAYS_BEFORE), request.dueDaysBefore(),
                item::changeDueDaysBefore);
        applyDetail(clear.contains(PrepField.URL), request.url(), item::changeUrl);
        applyDetail(clear.contains(PrepField.MEMO), request.memo(), item::changeMemo);

        return mutationOf(trip, prepRepository.saveAndFlush(item));
    }

    /**
     * 하드 삭제. 되돌리기는 FE의 5초 대기라 여기까지 왔으면 이미 되돌릴 뜻이 없다 —
     * 소프트 삭제를 두면 아무도 안 읽는 행이 쌓이고, 진행률이 그걸 세는지 아닌지가 매번
     * 다시 질문이 된다.
     */
    @Transactional
    public void delete(Long memberId, Long itemId) {
        prepRepository.delete(getOwnedItem(memberId, itemId));
    }

    /**
     * 한 분류 안의 순서를 통째로 다시 매긴다.
     *
     * <p>보내지 않은 항목은 <b>지우지 않고 뒤에 붙인다</b>. 화면이 목록 일부만 들고 있을 때
     * 나머지가 사라지는 것보다, 순서가 뒤로 밀리는 편이 되돌리기 쉽다.
     */
    @Transactional
    public void reorder(Long memberId, Long tripId, PrepRequests.Order request) {
        getOwnedTrip(memberId, tripId);
        List<TripPrepItem> items = prepRepository
                .findAllByTripIdAndCategoryOrderByDisplayOrderAscIdAsc(tripId,
                        request.category());
        Map<Long, TripPrepItem> byId = new LinkedHashMap<>();
        items.forEach(item -> byId.put(item.getId(), item));

        int order = 0;
        Set<Long> placed = new HashSet<>();
        for (Long itemId : request.itemIds()) {
            TripPrepItem item = byId.get(itemId);
            // 남의 항목도, 다른 분류의 항목도 여기서는 똑같이 404다.
            if (item == null || !placed.add(itemId)) {
                throw new CustomException(ErrorCode.TRAVEL_PREP_ITEM_NOT_FOUND);
            }
            item.changeOrder(order++);
        }
        for (TripPrepItem item : items) {
            if (!placed.contains(item.getId())) {
                item.changeOrder(order++);
            }
        }
    }

    /**
     * 진행률과 기한 지남 개수만. 사이드바 배지·홈 카드가 목록 없이 이것만 읽는다.
     *
     * <p><b>화면이 세지 않고 여기서 센다.</b> 배지와 준비 화면 상단이 서로 다른 값을 말하면,
     * 사용자는 무엇을 눌러야 배지가 사라지는지 알 수 없다 — 같은 함수에서 나와야 한다.
     *
     * @param today 첫날 기준 도시의 오늘. 호출부가 이미 알고 있으면 그것을 넘긴다 —
     *              요약 한 번에 날짜 행을 두 번 읽지 않게
     */
    public PrepSummary summaryOf(Trip trip, LocalDate today) {
        return summaryOf(itemsOf(trip.getId()), trip.getStartDate(), today);
    }

    /**
     * 아직 체크하지 않은 항목 수. 준비 알림이 <b>보내기 직전에</b> 부른다.
     *
     * <p>기한과 무관하므로 「오늘」이 필요 없다 — 「6개 남았어요」의 6은 날짜를 안 본다.
     */
    public int remainingCount(Long tripId) {
        return (int) itemsOf(tripId).stream().filter(item -> !item.isDone()).count();
    }

    // ---------------- helpers ----------------

    /**
     * 값을 바꾼다 — 지우라고 했으면 null로, 보냈으면 그 값으로, 아무 말도 없으면 그대로.
     * 이 세 갈래가 PATCH의 전부다.
     */
    private static <T> void applyDetail(boolean clear, T value, Consumer<T> setter) {
        if (clear) {
            setter.accept(null);
        } else if (value != null) {
            setter.accept(value);
        }
    }

    private Trip getOwnedTrip(Long memberId, Long tripId) {
        return tripRepository.findByIdAndMemberId(tripId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_TRIP_NOT_FOUND));
    }

    /** 남의 항목도 404 — 403이면 "그 id의 항목은 존재한다"가 새어나간다. */
    private TripPrepItem getOwnedItem(Long memberId, Long itemId) {
        TripPrepItem item = prepRepository.findById(itemId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_PREP_ITEM_NOT_FOUND));
        if (!item.getMemberId().equals(memberId)) {
            throw new CustomException(ErrorCode.TRAVEL_PREP_ITEM_NOT_FOUND);
        }
        return item;
    }

    /**
     * 첫날 기준 도시의 오늘. 여행 상태·D-day와 같은 시계다({@link TripClock}).
     *
     * <p>날짜 행이 없는 여행은 v2.1에서 존재할 수 없지만, 그 한 건 때문에 준비 화면 전체가
     * 사라지지는 않게 한다 — {@code zoneOn}이 기기 시간대로 버틴다.
     */
    private LocalDate todayOf(Trip trip) {
        Map<LocalDate, TravelPlace> cities = tripDayService.baseCitiesOf(trip.getId());
        return LocalDate.now(clock.withZone(TripClock.zoneOn(trip.getStartDate(), cities)));
    }

    private List<TripPrepItem> itemsOf(Long tripId) {
        return prepRepository.findAllByTripIdOrderByCategoryAscDisplayOrderAscIdAsc(tripId);
    }

    /** 그 분류의 맨 뒤. 비어 있으면 0이다. */
    private int nextOrder(Long tripId, PrepCategory category) {
        return prepRepository
                .findAllByTripIdAndCategoryOrderByDisplayOrderAscIdAsc(tripId, category).stream()
                .mapToInt(TripPrepItem::getDisplayOrder)
                .max()
                .orElse(-1) + 1;
    }

    /** 「출발 3일 후」는 준비가 아니다(API §10). */
    private static void requireValidDue(Integer dueDaysBefore) {
        if (dueDaysBefore != null && dueDaysBefore < 0) {
            throw new CustomException(ErrorCode.TRAVEL_PREP_INVALID_DUE);
        }
    }

    private static PrepGroup toGroup(PrepCategory category, List<TripPrepItem> items,
                                     LocalDate startDate, LocalDate today) {
        return new PrepGroup(category, items.size(),
                (int) items.stream().filter(TripPrepItem::isDone).count(),
                items.stream().map(item -> PrepItemView.of(item, startDate, today)).toList());
    }

    private static PrepSummary summaryOf(List<TripPrepItem> items, LocalDate startDate,
                                         LocalDate today) {
        return new PrepSummary(items.size(),
                (int) items.stream().filter(TripPrepItem::isDone).count(),
                (int) items.stream().filter(item -> item.isOverdue(startDate, today)).count());
    }

    private PrepItemMutation mutationOf(Trip trip, TripPrepItem item) {
        LocalDate today = todayOf(trip);
        return new PrepItemMutation(item.getCategory(),
                PrepItemView.of(item, trip.getStartDate(), today),
                summaryOf(itemsOf(trip.getId()), trip.getStartDate(), today));
    }
}
