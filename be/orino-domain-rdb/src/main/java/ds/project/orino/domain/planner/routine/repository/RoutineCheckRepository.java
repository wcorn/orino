package ds.project.orino.domain.planner.routine.repository;

import ds.project.orino.domain.planner.routine.entity.RoutineCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoutineCheckRepository extends JpaRepository<RoutineCheck, Long> {

    Optional<RoutineCheck> findByMemberIdAndRecurringEventIdAndInstanceDate(
            Long memberId, String recurringEventId, LocalDate instanceDate);

    boolean existsByMemberIdAndRecurringEventIdAndInstanceDate(
            Long memberId, String recurringEventId, LocalDate instanceDate);

    void deleteByMemberIdAndRecurringEventIdAndInstanceDate(
            Long memberId, String recurringEventId, LocalDate instanceDate);

    /** 통합 피드 done 조인용 batch 로드: [from, to] 구간의 완료 행을 한 번에 가져온다(N+1 회피). */
    List<RoutineCheck> findByMemberIdAndInstanceDateBetween(
            Long memberId, LocalDate from, LocalDate to);

    /** 시리즈 전체 삭제(scope=all)용 체크 정리. */
    void deleteByMemberIdAndRecurringEventId(Long memberId, String recurringEventId);

    /** 이후 모두(scope=following) 삭제용: instanceDate 이상 체크 정리. */
    void deleteByMemberIdAndRecurringEventIdAndInstanceDateGreaterThanEqual(
            Long memberId, String recurringEventId, LocalDate instanceDate);

    /**
     * following 편집 분할 시 체크 재키잉: {@code instanceDate} 이상 행을 새 시리즈 id로 이관한다.
     * 이미 이관된 행(oldId에 없음)에 대해서는 no-op이라 재시도에 멱등하다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update RoutineCheck c set c.recurringEventId = :newId "
            + "where c.memberId = :memberId and c.recurringEventId = :oldId "
            + "and c.instanceDate >= :instanceDate")
    int migrateFollowing(@Param("memberId") Long memberId,
                         @Param("oldId") String oldId,
                         @Param("newId") String newId,
                         @Param("instanceDate") LocalDate instanceDate);
}
