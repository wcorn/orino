package ds.project.orino.domain.planner.travel.repository;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.travel.entity.PrepCategory;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripPrepItem;
import ds.project.orino.domain.support.RepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 준비 항목의 매핑과 읽기 경로를 고정한다.
 *
 * <p>화면이 읽는 길은 <b>분류 → 순서</b> 하나뿐이라({@code idx_prep_trip_category_order})
 * 그 정렬이 여기서 깨지면 화면 순서가 저장할 때마다 흔들린다.
 */
@RepositoryTest
@Transactional
class TripPrepItemRepositoryTest {

    private static final LocalDate OCT24 = LocalDate.of(2026, 10, 24);
    private static final LocalDate OCT29 = LocalDate.of(2026, 10, 29);

    @Autowired
    private TripPrepItemRepository prepRepository;
    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;
    private Long tripId;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.save(new Member("traveler", "pw")).getId();
        tripId = tripRepository.save(new Trip(memberId, "간사이", OCT24, OCT29)).getId();
    }

    @Test
    @DisplayName("항목 필드가 그대로 저장·조회된다 — done은 BIT(1), 기한은 D−N")
    void savesAndLoads() {
        TripPrepItem saved = prepRepository.save(
                new TripPrepItem(tripId, memberId, PrepCategory.BOOKING, "숙소 잔금 결제", 0));
        saved.changeDueDaysBefore(14);
        saved.changeUrl("https://example.com/booking");
        saved.changeMemo("카드로 결제");
        saved.check(true);
        prepRepository.flush();

        TripPrepItem found = prepRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getCategory()).isEqualTo(PrepCategory.BOOKING);
        assertThat(found.getTitle()).isEqualTo("숙소 잔금 결제");
        assertThat(found.isDone()).isTrue();
        assertThat(found.getDueDaysBefore()).isEqualTo(14);
        assertThat(found.getUrl()).isEqualTo("https://example.com/booking");
        assertThat(found.getMemo()).isEqualTo("카드로 결제");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("분류 안에서는 순서대로 읽힌다 — 분류끼리의 차례는 DB가 정하지 않는다")
    void readsOrderedWithinCategory() {
        prepRepository.save(new TripPrepItem(tripId, memberId, PrepCategory.BAG, "양말", 1));
        prepRepository.save(
                new TripPrepItem(tripId, memberId, PrepCategory.BAG, "멀티어댑터", 0));
        prepRepository.save(new TripPrepItem(tripId, memberId, PrepCategory.DOCUMENT, "여권", 0));

        List<TripPrepItem> items =
                prepRepository.findAllByTripIdOrderByCategoryAscDisplayOrderAscIdAsc(tripId);

        // category는 VARCHAR라 DB는 사전순(BAG → BOOKING → DOCUMENT → TODO)으로 준다.
        // 화면이 보는 차례(서류 → 예약 → 짐 → 할 일)는 enum 선언 순서이고,
        // 그건 서비스가 다시 묶으면서 정한다 — 여기서 확인할 것은 분류 안의 순서다.
        assertThat(items).extracting(TripPrepItem::getTitle)
                .containsExactly("멀티어댑터", "양말", "여권");
    }

    @Test
    @DisplayName("분류 하나만 순서대로 읽는다 — 새 항목의 자리를 정할 때 쓴다")
    void readsSingleCategory() {
        prepRepository.save(new TripPrepItem(tripId, memberId, PrepCategory.BAG, "양말", 1));
        prepRepository.save(
                new TripPrepItem(tripId, memberId, PrepCategory.BAG, "멀티어댑터", 0));
        prepRepository.save(new TripPrepItem(tripId, memberId, PrepCategory.TODO, "환전", 0));

        List<TripPrepItem> bag = prepRepository
                .findAllByTripIdAndCategoryOrderByDisplayOrderAscIdAsc(tripId, PrepCategory.BAG);

        assertThat(bag).extracting(TripPrepItem::getTitle)
                .containsExactly("멀티어댑터", "양말");
    }

    @Test
    @DisplayName("체크해도 순서는 그대로다 — 정렬은 done을 보지 않는다")
    void checkingDoesNotReorder() {
        TripPrepItem first = prepRepository.save(
                new TripPrepItem(tripId, memberId, PrepCategory.BAG, "멀티어댑터", 0));
        prepRepository.save(new TripPrepItem(tripId, memberId, PrepCategory.BAG, "양말", 1));

        first.check(true);
        prepRepository.flush();

        assertThat(prepRepository
                .findAllByTripIdAndCategoryOrderByDisplayOrderAscIdAsc(tripId, PrepCategory.BAG))
                .extracting(TripPrepItem::getTitle)
                .containsExactly("멀티어댑터", "양말");
    }
}
