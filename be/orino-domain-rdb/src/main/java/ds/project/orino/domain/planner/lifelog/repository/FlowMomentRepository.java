package ds.project.orino.domain.planner.lifelog.repository;

import ds.project.orino.domain.planner.lifelog.entity.FlowMoment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FlowMomentRepository extends JpaRepository<FlowMoment, Long> {

    /** 흐름 내 순서. 기본은 sort_order, 동률이면 id(담은 순). occurred_at 정렬은 서비스에서 조합. */
    List<FlowMoment> findAllByFlowIdOrderBySortOrderAscIdAsc(Long flowId);

    /** "이 기록이 담긴 흐름" 역조회. */
    List<FlowMoment> findAllByMomentId(Long momentId);

    /** 피드 배치 로딩: 여러 기록의 소속 흐름을 한 번에. */
    List<FlowMoment> findAllByMomentIdIn(Collection<Long> momentIds);

    /** 흐름 목록 배치 로딩: 여러 흐름의 소속 기록을 한 번에(카운트·커버·기간 계산용). */
    List<FlowMoment> findAllByFlowIdIn(Collection<Long> flowIds);

    boolean existsByFlowIdAndMomentId(Long flowId, Long momentId);

    Optional<FlowMoment> findByFlowIdAndMomentId(Long flowId, Long momentId);

    void deleteByFlowIdAndMomentId(Long flowId, Long momentId);

    /** 흐름 끝에 담을 때 쓸 다음 sort_order. 빈 흐름이면 -1을 반환해 첫 항목이 0이 되게 한다. */
    @Query("SELECT COALESCE(MAX(fm.sortOrder), -1) FROM FlowMoment fm WHERE fm.flowId = :flowId")
    int findMaxSortOrder(@Param("flowId") Long flowId);
}
