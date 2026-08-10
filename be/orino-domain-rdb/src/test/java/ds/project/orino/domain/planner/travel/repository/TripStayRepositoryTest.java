package ds.project.orino.domain.planner.travel.repository;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripStay;
import ds.project.orino.domain.support.RepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 숙소 매핑과 날짜 판정을 고정한다. 판정은 DB가 아니라 애플리케이션에서 하므로
 * ({@code [in, out)} 반열린 구간) 경계 규칙을 여기서 못 박는다 —
 * <b>체크아웃일 밤은 이미 다른 곳에서 잔다.</b>
 */
@RepositoryTest
@Transactional
class TripStayRepositoryTest {

    private static final LocalDate OCT24 = LocalDate.of(2026, 10, 24);
    private static final LocalDate OCT27 = LocalDate.of(2026, 10, 27);
    private static final LocalDate OCT29 = LocalDate.of(2026, 10, 29);

    @Autowired
    private TripStayRepository stayRepository;
    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private TravelPlaceRepository placeRepository;
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
    @DisplayName("숙소 필드가 그대로 저장·조회된다")
    void savesAndLoads() {
        Long placeId = placeRepository.save(
                TravelPlace.manual(memberId, "호텔 한큐")).getId();
        TripStay stay = stayRepository.save(new TripStay(tripId, "호텔 한큐", OCT24, OCT27));
        stay.updateBasics("호텔 한큐 레스파이어", placeId, OCT24, OCT27);
        stay.updateDetails(LocalTime.of(15, 0), LocalTime.of(11, 0),
                "https://example.com/booking", "조식 포함");
        stayRepository.flush();

        TripStay found = stayRepository.findById(stay.getId()).orElseThrow();
        assertThat(found.getTripId()).isEqualTo(tripId);
        assertThat(found.getName()).isEqualTo("호텔 한큐 레스파이어");
        assertThat(found.getPlaceId()).isEqualTo(placeId);
        assertThat(found.getCheckInDate()).isEqualTo(OCT24);
        assertThat(found.getCheckOutDate()).isEqualTo(OCT27);
        // 벽시계 시각이다 — UTC로 환산하지 않는다.
        assertThat(found.getCheckInTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(found.getCheckOutTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(found.getBookingUrl()).isEqualTo("https://example.com/booking");
        assertThat(found.getMemo()).isEqualTo("조식 포함");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("체크인 날짜 오름차순으로 나온다")
    void listsByCheckInDate() {
        stayRepository.save(new TripStay(tripId, "교토 료칸", OCT27, OCT29));
        stayRepository.save(new TripStay(tripId, "오사카 호텔", OCT24, OCT27));

        assertThat(stayRepository.findAllByTripIdOrderByCheckInDateAscIdAsc(tripId))
                .extracting(TripStay::getName)
                .containsExactly("오사카 호텔", "교토 료칸");
    }

    @Test
    @DisplayName("다른 여행의 숙소를 넘겨주지 않는다")
    void scopedByTrip() {
        Long other = tripRepository.save(new Trip(memberId, "제주", OCT24, OCT27)).getId();
        Long id = stayRepository.save(new TripStay(tripId, "오사카 호텔", OCT24, OCT27)).getId();

        assertThat(stayRepository.findByIdAndTripId(id, tripId)).isPresent();
        assertThat(stayRepository.findByIdAndTripId(id, other)).isEmpty();
    }

    @Test
    @DisplayName("오늘 밤 자는 곳은 체크인 이상 체크아웃 미만인 날짜다")
    void coversNightIsHalfOpen() {
        TripStay stay = new TripStay(tripId, "오사카 호텔", OCT24, OCT27);

        assertThat(stay.coversNight(LocalDate.of(2026, 10, 23))).isFalse();
        assertThat(stay.coversNight(OCT24)).isTrue();
        assertThat(stay.coversNight(LocalDate.of(2026, 10, 26))).isTrue();
        // 체크아웃일 밤은 다른 곳에서 잔다.
        assertThat(stay.coversNight(OCT27)).isFalse();
        assertThat(stay.isCheckOutOn(OCT27)).isTrue();
    }

    @Test
    @DisplayName("이동일(앞 숙소 체크아웃 = 뒤 숙소 체크인)은 겹침이 아니다")
    void checkoutDayIsNotOverlap() {
        TripStay osaka = new TripStay(tripId, "오사카 호텔", OCT24, OCT27);

        assertThat(osaka.overlaps(OCT27, OCT29)).isFalse();
        // 하루라도 밤이 겹치면 겹침이다.
        assertThat(osaka.overlaps(LocalDate.of(2026, 10, 26), OCT29)).isTrue();
        assertThat(osaka.overlaps(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 25))).isTrue();
    }

    @Test
    @DisplayName("여행 기간이 줄면 체크아웃을 당기고, 묵는 밤이 없어지면 지울 대상이 된다")
    void shrinkCheckOut() {
        TripStay stay = stayRepository.save(new TripStay(tripId, "오사카 호텔", OCT24, OCT29));

        stay.shrinkCheckOutTo(OCT27);
        assertThat(stay.isEmptyPeriod()).isFalse();

        stay.shrinkCheckOutTo(OCT24);
        assertThat(stay.isEmptyPeriod()).isTrue();
    }
}
