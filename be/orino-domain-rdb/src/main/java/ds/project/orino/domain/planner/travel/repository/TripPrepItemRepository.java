package ds.project.orino.domain.planner.travel.repository;

import ds.project.orino.domain.planner.travel.entity.PrepCategory;
import ds.project.orino.domain.planner.travel.entity.TripPrepItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 준비 항목 조회. 화면은 <b>여행 하나를 통째로</b> 읽는다 — 분류 4개를 항목이 없어도 전부
 * 내려야 하고 진행률·기한 지남 개수도 전체를 봐야 나오므로, 분류별로 나눠 읽을 이유가 없다.
 * 여행 하나에 항목은 수십 개다.
 */
public interface TripPrepItemRepository extends JpaRepository<TripPrepItem, Long> {

    /**
     * 여행 전체. 정렬은 인덱스 {@code idx_prep_trip_category_order}를 그대로 탄다.
     *
     * <p>다만 {@code category}는 VARCHAR라 <b>DB가 주는 분류 차례는 사전순</b>이다
     * (BAG → BOOKING → DOCUMENT → TODO). 화면이 보는 차례(서류 → 예약 → 짐 → 할 일)는
     * enum 선언 순서이고, 그건 응답을 만들면서 다시 묶어 정한다 — 여기서 보장하는 것은
     * <b>분류 안의 순서</b>다.
     */
    List<TripPrepItem> findAllByTripIdOrderByCategoryAscDisplayOrderAscIdAsc(Long tripId);

    /**
     * 여러 여행의 항목을 한 번에. 사이드바 요약이 여행마다 진행률을 세는데, 여행 수만큼
     * 질의를 날리면 여행이 늘 때마다 요약이 느려진다 — 화면을 옮길 때마다 부르는 자리다.
     *
     * <p>정렬이 없는 이유는 <b>집계만</b> 쓰기 때문이다. 화면에 그리는 목록은 위의 여행
     * 하나짜리 질의가 담당한다.
     */
    List<TripPrepItem> findAllByTripIdIn(Collection<Long> tripIds);

    /** 새 항목의 순서를 정할 때 쓴다 — 그 분류의 맨 뒤가 어디인지 알아야 한다. */
    List<TripPrepItem> findAllByTripIdAndCategoryOrderByDisplayOrderAscIdAsc(
            Long tripId, PrepCategory category);
}
