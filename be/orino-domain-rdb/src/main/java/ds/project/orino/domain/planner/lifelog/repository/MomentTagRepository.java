package ds.project.orino.domain.planner.lifelog.repository;

import ds.project.orino.domain.planner.lifelog.entity.MomentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MomentTagRepository extends JpaRepository<MomentTag, Long> {

    List<MomentTag> findAllByMomentIdOrderByIdAsc(Long momentId);

    /** 피드 배치 로딩: 여러 기록의 태그를 한 번에. */
    List<MomentTag> findAllByMomentIdIn(Collection<Long> momentIds);

    void deleteByMomentId(Long momentId);

    /**
     * 태그 자동완성 — 멤버가 이미 쓴 태그 중 접두어로 시작하는 것들(중복 제거·정렬).
     * 멤버 스코프는 태그가 붙은 기록의 소유자로 건다.
     */
    @Query("""
            SELECT DISTINCT t.name
            FROM MomentTag t
            WHERE t.momentId IN (SELECT m.id FROM Moment m WHERE m.memberId = :memberId)
              AND t.name LIKE :prefix
            ORDER BY t.name ASC
            """)
    List<String> findDistinctNamesByMemberIdAndPrefix(@Param("memberId") Long memberId,
                                                       @Param("prefix") String prefix);
}
