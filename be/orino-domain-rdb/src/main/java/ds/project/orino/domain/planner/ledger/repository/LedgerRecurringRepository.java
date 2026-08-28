package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerRecurring;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurringStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LedgerRecurringRepository extends JpaRepository<LedgerRecurring, Long> {

    Optional<LedgerRecurring> findByIdAndMemberId(Long id, Long memberId);

    /** 해지한 항목도 함께 온다 — 목록에서 사라지지 않고 「종료됨」으로 남는다(§6.6). */
    List<LedgerRecurring> findAllByMemberIdOrderByIdAsc(Long memberId);

    /**
     * 스케줄러가 훑는 대상.
     *
     * <p>{@code PAUSED}도 가져온다 — 정지는 <b>구간</b>이라 정지 시작 전의 밀린 회차가
     * 남아 있을 수 있고, 그건 여전히 적혀야 한다. 그날 살아 있었는지는
     * {@code isActiveOn}이 날짜별로 판정한다.
     */
    List<LedgerRecurring> findAllByStatusIn(Collection<LedgerRecurringStatus> statuses);
}
