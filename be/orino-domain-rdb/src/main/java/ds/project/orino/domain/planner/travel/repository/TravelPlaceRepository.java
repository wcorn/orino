package ds.project.orino.domain.planner.travel.repository;

import ds.project.orino.domain.planner.travel.entity.PlaceKind;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 장소 캐시 조회. 여행이 아니라 <b>멤버</b>로 스코프한다 — 같은 장소를 여행마다 새로 만들면
 * "이전 여행에서 좋았던 곳" 판정이 불가능하다.
 *
 * <p>1단계에서는 쓰지 않는다. 2단계(장소 검색)부터 채우기 시작한다.
 */
public interface TravelPlaceRepository extends JpaRepository<TravelPlace, Long> {

    Optional<TravelPlace> findByIdAndMemberId(Long id, Long memberId);

    /** 이미 담아둔 구글 장소인지 확인해 중복 저장을 막는다({@code uk_place_member_google}). */
    Optional<TravelPlace> findByMemberIdAndGooglePlaceId(Long memberId, String googlePlaceId);

    /** 검색 결과 중 이미 담아 둔 장소를 한 번에 찾는다(결과 20개마다 조회하지 않게). */
    List<TravelPlace> findAllByMemberIdAndGooglePlaceIdIn(Long memberId, List<String> googlePlaceIds);

    /**
     * 광역 행정구역을 아직 못 채운 구글 장소. 이 칼럼이 생기기 전에 담긴 것들이다(#1375).
     *
     * <p><b>비어 있는 것만</b> 고른다 — 채워진 장소를 다시 부르면 유료 호출이 그만큼 헛나간다.
     * 직접 입력한 장소는 구글 id가 없어 애초에 물어볼 곳이 없다.
     */
    List<TravelPlace> findAllByMemberIdAndAdminAreaIsNullAndGooglePlaceIdIsNotNull(
            Long memberId, Limit limit);

    /** 남은 개수. 화면이 「몇 번 더 눌러야 하나」를 말해 준다. */
    long countByMemberIdAndAdminAreaIsNullAndGooglePlaceIdIsNotNull(Long memberId);

    /** 여러 일정의 장소를 한 번에 붙일 때 쓰는 배치 조회(N+1 회피). */
    List<TravelPlace> findAllByIdIn(List<Long> ids);

    /** 이름 부분일치 — 직접 입력한 장소를 다시 고를 때. */
    List<TravelPlace> findAllByMemberIdAndNameContainingOrderByNameAsc(Long memberId, String name);

    /**
     * 검색을 거치지 않고 만든 도시. 목적지를 직접 입력한 여행이 같은 도시를 다시 쓸 때
     * 장소를 새로 만들지 않기 위해 찾는다(같은 도시가 이름만 같은 행으로 늘어나면
     * 도시 일치 판정이 여행마다 갈린다).
     */
    Optional<TravelPlace> findFirstByMemberIdAndNameAndPlaceKindAndGooglePlaceIdIsNull(
            Long memberId, String name, PlaceKind placeKind);
}
