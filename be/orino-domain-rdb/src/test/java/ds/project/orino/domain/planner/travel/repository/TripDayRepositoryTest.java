package ds.project.orino.domain.planner.travel.repository;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.travel.entity.PlaceKind;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripDay;
import ds.project.orino.domain.support.RepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 날짜 ↔ 기준 도시 매핑을 고정한다. v2.1에서 타임존·통화·날씨 좌표가 전부 여기서 파생되므로,
 * <b>이 매핑이 틀리면 화면은 멀쩡한데 시각만 조용히 어긋난다.</b>
 */
@RepositoryTest
@Transactional
class TripDayRepositoryTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 10, 24);
    private static final LocalDate DAY2 = LocalDate.of(2026, 10, 25);

    @Autowired
    private TripDayRepository dayRepository;
    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private TravelPlaceRepository placeRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;
    private Long tripId;
    private Long osaka;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.save(new Member("traveler", "pw")).getId();
        tripId = tripRepository.save(new Trip(memberId, "간사이", DAY1, DAY2)).getId();
        osaka = city("오사카", "Asia/Tokyo", "JPY").getId();
    }

    @Test
    @DisplayName("날짜와 기준 도시가 그대로 저장·조회된다")
    void savesAndLoads() {
        TripDay saved = dayRepository.save(new TripDay(tripId, DAY1, osaka));
        saved.updateCityMemo("체크아웃 후 교토역 코인로커에 짐 보관");
        dayRepository.flush();

        TripDay found = dayRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getTripId()).isEqualTo(tripId);
        assertThat(found.getDayDate()).isEqualTo(DAY1);
        assertThat(found.getBasePlaceId()).isEqualTo(osaka);
        assertThat(found.getCityMemo()).isEqualTo("체크아웃 후 교토역 코인로커에 짐 보관");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 여행·같은 날짜로 두 번 저장하면 UNIQUE 위반이다")
    void rejectsDuplicateDate() {
        dayRepository.save(new TripDay(tripId, DAY1, osaka));
        dayRepository.flush();

        // 하루에 기준 도시는 하나다 — 둘이면 그날의 타임존에 답이 두 개가 된다.
        // id가 IDENTITY라 save 시점에 INSERT가 나가고 거기서 막힌다.
        assertThatThrownBy(() -> dayRepository.save(new TripDay(tripId, DAY1, osaka)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("다른 여행이면 같은 날짜를 각자 가질 수 있다")
    void allowsSameDateAcrossTrips() {
        Long other = tripRepository.save(new Trip(memberId, "제주", DAY1, DAY2)).getId();
        dayRepository.save(new TripDay(tripId, DAY1, osaka));
        dayRepository.save(new TripDay(other, DAY1, osaka));

        assertThat(dayRepository.countByTripId(tripId)).isEqualTo(1);
        assertThat(dayRepository.countByTripId(other)).isEqualTo(1);
    }

    @Test
    @DisplayName("여행 날짜는 날짜 오름차순으로 나온다(구간 파생이 연속성을 본다)")
    void listsInDateOrder() {
        dayRepository.save(new TripDay(tripId, DAY2, osaka));
        dayRepository.save(new TripDay(tripId, DAY1, osaka));

        assertThat(dayRepository.findAllByTripIdOrderByDayDateAsc(tripId))
                .extracting(TripDay::getDayDate)
                .containsExactly(DAY1, DAY2);
    }

    @Test
    @DisplayName("여러 여행의 날짜를 한 번에 읽는다(목록 화면 N+1 회피)")
    void listsAcrossTrips() {
        Long other = tripRepository.save(new Trip(memberId, "제주", DAY1, DAY2)).getId();
        dayRepository.save(new TripDay(tripId, DAY1, osaka));
        dayRepository.save(new TripDay(other, DAY2, osaka));

        assertThat(dayRepository
                .findAllByTripIdInOrderByTripIdAscDayDateAsc(List.of(tripId, other)))
                .extracting(TripDay::getTripId)
                .containsExactly(tripId, other);
    }

    @Test
    @DisplayName("기준 도시를 바꿔도 그 날짜의 도시 메모는 살아남는다")
    void keepsCityMemoWhenBaseCityChanges() {
        Long kyoto = city("교토", "Asia/Tokyo", "JPY").getId();
        TripDay day = dayRepository.save(new TripDay(tripId, DAY1, osaka));
        day.updateCityMemo("코인로커");
        dayRepository.flush();

        day.changeBaseCity(kyoto);
        dayRepository.flush();

        TripDay found = dayRepository.findByTripIdAndDayDate(tripId, DAY1).orElseThrow();
        assertThat(found.getBasePlaceId()).isEqualTo(kyoto);
        assertThat(found.getCityMemo()).isEqualTo("코인로커");
    }

    @Test
    @DisplayName("공백만 남은 도시 메모는 비운 것으로 저장한다")
    void blankMemoBecomesNull() {
        TripDay day = dayRepository.save(new TripDay(tripId, DAY1, osaka));
        day.updateCityMemo("   ");
        dayRepository.flush();

        assertThat(dayRepository.findById(day.getId()).orElseThrow().getCityMemo()).isNull();
    }

    @Test
    @DisplayName("기준 도시에서 타임존·통화·좌표를 그대로 읽는다")
    void baseCityCarriesTimezoneAndCurrency() {
        dayRepository.save(new TripDay(tripId, DAY1, osaka));

        TravelPlace city = placeRepository.findById(osaka).orElseThrow();
        assertThat(city.getPlaceKind()).isEqualTo(PlaceKind.CITY);
        assertThat(city.isCity()).isTrue();
        assertThat(city.getTimezone()).isEqualTo("Asia/Tokyo");
        assertThat(city.getCurrency()).isEqualTo("JPY");
        assertThat(city.getLat()).isEqualByComparingTo("34.6937249");
        assertThat(city.getLng()).isEqualByComparingTo("135.5022535");
    }

    private TravelPlace city(String name, String timezone, String currency) {
        TravelPlace place = TravelPlace.manualCity(memberId, name, timezone, currency);
        place.updateCoordinates(new BigDecimal("34.6937249"), new BigDecimal("135.5022535"));
        return placeRepository.save(place);
    }
}
