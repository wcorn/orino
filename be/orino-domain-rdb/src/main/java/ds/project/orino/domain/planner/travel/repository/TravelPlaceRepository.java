package ds.project.orino.domain.planner.travel.repository;

import ds.project.orino.domain.planner.travel.entity.TravelPlace;
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

    /** 여러 일정의 장소를 한 번에 붙일 때 쓰는 배치 조회(N+1 회피). */
    List<TravelPlace> findAllByIdIn(List<Long> ids);

    /** 이름 부분일치 — 직접 입력한 장소를 다시 고를 때. */
    List<TravelPlace> findAllByMemberIdAndNameContainingOrderByNameAsc(Long memberId, String name);
}
