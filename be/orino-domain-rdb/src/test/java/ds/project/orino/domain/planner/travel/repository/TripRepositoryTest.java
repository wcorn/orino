package ds.project.orino.domain.planner.travel.repository;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.support.RepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여행 매핑과 목록 조회 규칙을 고정한다. 상태(예정/진행 중/완료)로 거르는 쿼리는 없다 —
 * 상태는 컬럼이 아니라 여행 타임존의 오늘로 파생하는 값이라 SQL에서 판정할 수 없고,
 * 여기서는 그 대신 쓰는 기간 비교 조회만 검증한다(파생 판정은 {@code TripTest}).
 */
@RepositoryTest
@Transactional
class TripRepositoryTest {

    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.save(new Member("traveler", "pw")).getId();
    }

    @Test
    @DisplayName("여행 필드가 그대로 저장·조회되고 알림 기본값이 붙는다")
    void savesAndLoadsTrip() {
        Trip saved = tripRepository.save(new Trip(memberId, "도쿄 3박4일", "도쿄",
                LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 27), "Asia/Tokyo", "JPY"));

        Trip found = tripRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getMemberId()).isEqualTo(memberId);
        assertThat(found.getTitle()).isEqualTo("도쿄 3박4일");
        assertThat(found.getDestinationName()).isEqualTo("도쿄");
        assertThat(found.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(found.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(found.getTimezone()).isEqualTo("Asia/Tokyo");
        assertThat(found.getCurrency()).isEqualTo("JPY");
        assertThat(found.getDefaultNotifyMinutes()).isEqualTo(15);
        assertThat(found.isMorningSummaryEnabled()).isFalse();
        // 1단계는 목적지를 수동 입력하므로 장소 참조가 비어 있다.
        assertThat(found.getDestinationPlaceId()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("목적지 좌표(날씨 기준점)는 소수점 7자리까지 보존된다")
    void keepsCoordinatePrecision() {
        Trip trip = tripRepository.save(tokyoTrip());
        trip.updateDestinationPlace(null, new BigDecimal("35.6812362"), new BigDecimal("139.7671248"));
        tripRepository.flush();

        Trip found = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(found.getLat()).isEqualByComparingTo("35.6812362");
        assertThat(found.getLng()).isEqualByComparingTo("139.7671248");
    }

    @Test
    @DisplayName("findByIdAndMemberId는 다른 멤버의 여행을 넘겨주지 않는다")
    void scopedByMember() {
        Long other = memberRepository.save(new Member("other", "pw")).getId();
        Long id = tripRepository.save(tokyoTrip()).getId();

        assertThat(tripRepository.findByIdAndMemberId(id, memberId)).isPresent();
        assertThat(tripRepository.findByIdAndMemberId(id, other)).isEmpty();
    }

    @Test
    @DisplayName("예정·진행 중 목록은 아직 끝나지 않은 여행을 시작일 오름차순으로 준다")
    void listsUnfinishedByStartDateAsc() {
        LocalDate today = LocalDate.of(2026, 10, 25);
        Long ongoing = tripRepository.save(trip("도쿄", "2026-10-24", "2026-10-27")).getId();
        Long soon = tripRepository.save(trip("오사카", "2026-11-02", "2026-11-05")).getId();
        Long later = tripRepository.save(trip("삿포로", "2026-12-01", "2026-12-04")).getId();
        // 어제 끝난 여행은 빠져야 한다.
        tripRepository.save(trip("제주", "2026-10-20", "2026-10-24"));

        List<Long> ids = tripRepository
                .findAllByMemberIdAndEndDateGreaterThanEqualOrderByStartDateAscIdAsc(memberId, today)
                .stream().map(Trip::getId).toList();

        assertThat(ids).containsExactly(ongoing, soon, later);
    }

    @Test
    @DisplayName("오늘 끝나는 여행은 아직 진행 중이라 예정·진행 중 목록에 남는다")
    void endingTodayStaysInUnfinishedList() {
        LocalDate today = LocalDate.of(2026, 10, 27);
        Long endsToday = tripRepository.save(trip("도쿄", "2026-10-24", "2026-10-27")).getId();

        assertThat(tripRepository
                .findAllByMemberIdAndEndDateGreaterThanEqualOrderByStartDateAscIdAsc(memberId, today))
                .extracting(Trip::getId)
                .containsExactly(endsToday);
        assertThat(tripRepository
                .findAllByMemberIdAndEndDateLessThanOrderByEndDateDescIdDesc(memberId, today))
                .isEmpty();
    }

    @Test
    @DisplayName("완료 목록은 끝난 여행을 종료일 내림차순으로 준다")
    void listsCompletedByEndDateDesc() {
        LocalDate today = LocalDate.of(2026, 10, 28);
        Long older = tripRepository.save(trip("제주", "2026-05-01", "2026-05-03")).getId();
        Long recent = tripRepository.save(trip("도쿄", "2026-10-24", "2026-10-27")).getId();
        tripRepository.save(trip("오사카", "2026-11-02", "2026-11-05"));    // 아직 안 감

        List<Long> ids = tripRepository
                .findAllByMemberIdAndEndDateLessThanOrderByEndDateDescIdDesc(memberId, today)
                .stream().map(Trip::getId).toList();

        assertThat(ids).containsExactly(recent, older);
    }

    @Test
    @DisplayName("목록 조회는 다른 멤버의 여행을 섞지 않는다")
    void listsScopedByMember() {
        Long other = memberRepository.save(new Member("other", "pw")).getId();
        tripRepository.save(tokyoTrip());
        tripRepository.save(new Trip(other, "남의 여행", "파리",
                LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 27), "Europe/Paris", "EUR"));

        assertThat(tripRepository.findAllByMemberIdOrderByStartDateDescIdDesc(memberId))
                .extracting(Trip::getTitle)
                .containsExactly("도쿄");
    }

    @Test
    @DisplayName("전체 목록은 최근 여행이 위로 온다")
    void listsAllByStartDateDesc() {
        Long may = tripRepository.save(trip("제주", "2026-05-01", "2026-05-03")).getId();
        Long dec = tripRepository.save(trip("삿포로", "2026-12-01", "2026-12-04")).getId();
        Long oct = tripRepository.save(trip("도쿄", "2026-10-24", "2026-10-27")).getId();

        assertThat(tripRepository.findAllByMemberIdOrderByStartDateDescIdDesc(memberId))
                .extracting(Trip::getId)
                .containsExactly(dec, oct, may);
    }

    @Test
    @DisplayName("update는 기간·타임존·통화를 함께 바꾼다")
    void updatesBasics() {
        Trip trip = tripRepository.save(tokyoTrip());

        trip.update("오사카 2박3일", "오사카", LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 4), "Asia/Tokyo", "JPY");
        tripRepository.flush();

        Trip found = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("오사카 2박3일");
        assertThat(found.getDestinationName()).isEqualTo("오사카");
        assertThat(found.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(found.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(found.totalDays()).isEqualTo(3);
    }

    @Test
    @DisplayName("알림 설정(기본 시점·아침 요약)이 저장된다")
    void updatesNotificationSettings() {
        Trip trip = tripRepository.save(tokyoTrip());

        trip.updateNotificationSettings(30, true);
        tripRepository.flush();

        Trip found = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(found.getDefaultNotifyMinutes()).isEqualTo(30);
        assertThat(found.isMorningSummaryEnabled()).isTrue();
    }

    private Trip tokyoTrip() {
        return trip("도쿄", "2026-10-24", "2026-10-27");
    }

    private Trip trip(String title, String startDate, String endDate) {
        return new Trip(memberId, title, title, LocalDate.parse(startDate),
                LocalDate.parse(endDate), "Asia/Tokyo", "JPY");
    }
}
