package ds.project.orino.domain.planner.travel.repository;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.support.RepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 장소 캐시 매핑과 재사용 규칙을 고정한다. 1단계에서는 쓰지 않지만(2단계부터 채운다) 테이블을
 * 미리 만들었으므로 매핑이 맞는지는 지금 확인해 둔다 — 나중에 어긋나면 FK ALTER로 번진다.
 */
@RepositoryTest
@Transactional
class TravelPlaceRepositoryTest {

    @Autowired
    private TravelPlaceRepository placeRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.save(new Member("traveler", "pw")).getId();
    }

    @Test
    @DisplayName("구글 장소는 기본 정보·상세가 그대로 저장·조회된다")
    void savesGooglePlace() {
        TravelPlace place = placeRepository.save(
                TravelPlace.fromGoogle(memberId, "ChIJ8T1GpMGOGGARDYGSgpooDWw", "센소지"));
        place.updateBasics("도쿄도 다이토구 아사쿠사 2-3-1", new BigDecimal("35.7147651"),
                new BigDecimal("139.7966553"), "buddhist_temple", new BigDecimal("4.5"));
        place.updateDetails("+81 3-3842-0181", "{\"weekdayText\":[\"월: 06:00~17:00\"]}",
                Instant.parse("2026-08-07T00:00:00Z"));
        placeRepository.flush();

        TravelPlace found = placeRepository.findById(place.getId()).orElseThrow();
        assertThat(found.getGooglePlaceId()).isEqualTo("ChIJ8T1GpMGOGGARDYGSgpooDWw");
        assertThat(found.getName()).isEqualTo("센소지");
        assertThat(found.getAddress()).isEqualTo("도쿄도 다이토구 아사쿠사 2-3-1");
        assertThat(found.getLat()).isEqualByComparingTo("35.7147651");
        assertThat(found.getLng()).isEqualByComparingTo("139.7966553");
        assertThat(found.getCategory()).isEqualTo("buddhist_temple");
        assertThat(found.getRating()).isEqualByComparingTo("4.5");
        assertThat(found.getPhone()).isEqualTo("+81 3-3842-0181");
        assertThat(found.getOpeningHours()).contains("06:00~17:00");
        assertThat(found.isManualEntry()).isFalse();
    }

    @Test
    @DisplayName("직접 입력한 장소는 구글 식별자 없이 저장된다")
    void savesManualPlace() {
        TravelPlace saved = placeRepository.save(TravelPlace.manual(memberId, "숙소 근처 골목 카페"));

        TravelPlace found = placeRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getGooglePlaceId()).isNull();
        assertThat(found.isManualEntry()).isTrue();
        assertThat(found.getDetailsRefreshedAt()).isNull();
    }

    @Test
    @DisplayName("같은 구글 장소는 멤버 안에서 다시 찾아 재사용한다")
    void findsExistingGooglePlace() {
        placeRepository.save(TravelPlace.fromGoogle(memberId, "ChIJ_senso_ji", "센소지"));

        assertThat(placeRepository.findByMemberIdAndGooglePlaceId(memberId, "ChIJ_senso_ji"))
                .isPresent();
        assertThat(placeRepository.findByMemberIdAndGooglePlaceId(memberId, "ChIJ_unknown"))
                .isEmpty();
    }

    @Test
    @DisplayName("장소는 멤버로 스코프된다 — 남의 장소를 재사용하지 않는다")
    void scopedByMember() {
        Long other = memberRepository.save(new Member("other", "pw")).getId();
        Long id = placeRepository.save(
                TravelPlace.fromGoogle(memberId, "ChIJ_senso_ji", "센소지")).getId();

        assertThat(placeRepository.findByIdAndMemberId(id, memberId)).isPresent();
        assertThat(placeRepository.findByIdAndMemberId(id, other)).isEmpty();
        assertThat(placeRepository.findByMemberIdAndGooglePlaceId(other, "ChIJ_senso_ji")).isEmpty();
    }

    @Test
    @DisplayName("여러 일정의 장소를 id 배치로 한 번에 읽는다")
    void findsByIdBatch() {
        Long a = placeRepository.save(TravelPlace.fromGoogle(memberId, "ChIJ_a", "센소지")).getId();
        Long b = placeRepository.save(TravelPlace.fromGoogle(memberId, "ChIJ_b", "우에노공원")).getId();
        placeRepository.save(TravelPlace.fromGoogle(memberId, "ChIJ_c", "안 쓰는 곳"));

        assertThat(placeRepository.findAllByIdIn(List.of(a, b)))
                .extracting(TravelPlace::getName)
                .containsExactlyInAnyOrder("센소지", "우에노공원");
    }

    @Test
    @DisplayName("이름 부분일치로 직접 입력한 장소를 다시 고른다")
    void searchesByNameFragment() {
        placeRepository.save(TravelPlace.manual(memberId, "숙소 근처 카페"));
        placeRepository.save(TravelPlace.manual(memberId, "역 앞 카페"));
        placeRepository.save(TravelPlace.manual(memberId, "라멘집"));
        Long other = memberRepository.save(new Member("other", "pw")).getId();
        placeRepository.save(TravelPlace.manual(other, "남의 카페"));

        assertThat(placeRepository.findAllByMemberIdAndNameContainingOrderByNameAsc(memberId, "카페"))
                .extracting(TravelPlace::getName)
                .containsExactly("숙소 근처 카페", "역 앞 카페");
    }

    @Test
    @DisplayName("상세를 받은 적 없거나 30일이 지나면 재조회 대상이다")
    void detailsExpireAfterTtl() {
        TravelPlace place = placeRepository.save(
                TravelPlace.fromGoogle(memberId, "ChIJ_senso_ji", "센소지"));
        Instant now = Instant.parse("2026-09-06T00:00:00Z");

        // 아직 상세를 받은 적 없다.
        assertThat(place.needsDetailsRefresh(now)).isTrue();

        place.updateDetails(null, "{}", Instant.parse("2026-08-07T00:00:00Z"));
        // 30일 딱 지나기 전에는 캐시를 그대로 쓴다.
        assertThat(place.needsDetailsRefresh(now)).isFalse();
        assertThat(place.needsDetailsRefresh(Instant.parse("2026-09-06T00:00:01Z"))).isTrue();
    }
}
