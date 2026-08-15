package ds.project.orino.domain.planner.travel.repository;

import ds.project.orino.domain.planner.travel.entity.TravelMove;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 저장된 이동 조회(#1208).
 *
 * <p>보드는 <b>그날 등장하는 장소를 한 번에 넘겨</b> 필요한 이동을 통째로 받는다. 구간마다
 * 조회하면 일정 수만큼 쿼리가 나고, 그건 유료 호출을 없애면서 DB 왕복으로 갈아탄 것일 뿐이다.
 */
public interface TravelMoveRepository extends JpaRepository<TravelMove, Long> {

    /**
     * 그 장소들 사이의 이동을 전부 읽는다.
     *
     * <p>양쪽에 {@code IN}을 걸면 실제로 잇지 않는 조합까지 딸려 온다 — 그 필터는 호출부가
     * 장소 쌍으로 한다. 그날 장소는 많아야 열몇 개라 SQL로 쌍을 나열하는 것보다 이쪽이
     * 읽기 쉽고, 인덱스는 그대로 탄다.
     */
    List<TravelMove> findAllByMemberIdAndFromPlaceIdInAndToPlaceIdIn(
            Long memberId, Collection<Long> fromPlaceIds, Collection<Long> toPlaceIds);

    Optional<TravelMove> findByMemberIdAndFromPlaceIdAndToPlaceId(
            Long memberId, Long fromPlaceId, Long toPlaceId);

    void deleteByMemberIdAndFromPlaceIdAndToPlaceId(
            Long memberId, Long fromPlaceId, Long toPlaceId);
}
