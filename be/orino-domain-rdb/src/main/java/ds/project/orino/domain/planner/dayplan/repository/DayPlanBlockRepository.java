package ds.project.orino.domain.planner.dayplan.repository;

import ds.project.orino.domain.planner.dayplan.entity.DayPlanBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DayPlanBlockRepository extends JpaRepository<DayPlanBlock, Long> {

    /** 멤버 주간 블록 일괄 로드(정렬은 서비스에서 요일·시작시각 순으로 처리). */
    List<DayPlanBlock> findAllByMemberId(Long memberId);

    /** 전량 교체 저장 시 기존 블록 정리. */
    void deleteByMemberId(Long memberId);
}
