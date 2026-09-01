package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerStatement;
import ds.project.orino.domain.planner.ledger.entity.LedgerStatementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LedgerStatementRepository extends JpaRepository<LedgerStatement, Long> {

    Optional<LedgerStatement> findByIdAndMemberId(Long id, Long memberId);

    /** 자산 삭제 전 확인용 — 청구서가 한 장이라도 있으면 카드를 지울 수 없다. */
    boolean existsByMemberIdAndCardAssetId(Long memberId, Long cardAssetId);

    /** 그 카드의 청구서를 최근 사이클부터. */
    List<LedgerStatement> findAllByMemberIdAndCardAssetIdOrderByCycleStartDesc(
            Long memberId, Long cardAssetId);

    /** 사이클 시작일로 찾는다 — `UNIQUE(card_asset_id, cycle_start)`가 하나임을 보장한다. */
    Optional<LedgerStatement> findByCardAssetIdAndCycleStart(Long cardAssetId, LocalDate cycleStart);

    /** 그 날짜를 품는 사이클. 카드 사용 건이 편입될 곳이다. */
    @Query("""
            SELECT s FROM LedgerStatement s
            WHERE s.cardAssetId = :cardAssetId
              AND s.cycleStart <= :date
              AND s.cycleEnd >= :date
            """)
    Optional<LedgerStatement> findCovering(@Param("cardAssetId") Long cardAssetId,
                                           @Param("date") LocalDate date);

    /** 그 구간에 결제일이 오는 청구서. 청구 기준 통계가 이 목록으로 카드 사용을 옮겨 센다. */
    List<LedgerStatement> findAllByMemberIdAndPaymentDateBetween(
            Long memberId, LocalDate from, LocalDate to);

    /** 마감일이 지났는데 아직 집계 중인 것들. 스케줄러가 확정으로 넘긴다. */
    List<LedgerStatement> findAllByStatusAndCycleEndBefore(
            LedgerStatementStatus status, LocalDate date);

    /**
     * 미납 — 결제일이 지났는데 아직 다 내지 않은 청구서.
     *
     * <p><b>플래그를 저장하지 않고 물어볼 때마다 센다.</b> 저장하면 날짜가 바뀔 때마다 누군가
     * 갱신해야 하고, 갱신을 놓치는 순간 화면이 거짓말을 한다.
     */
    @Query("""
            SELECT s FROM LedgerStatement s
            WHERE s.memberId = :memberId
              AND s.status IN :unsettled
              AND s.paymentDate < :today
            ORDER BY s.paymentDate ASC
            """)
    List<LedgerStatement> findOverdue(@Param("memberId") Long memberId,
                                      @Param("unsettled") Collection<LedgerStatementStatus> unsettled,
                                      @Param("today") LocalDate today);

    /** 다가오는 결제. 「앞으로 나갈 돈」이 이 목록에서 나온다. */
    @Query("""
            SELECT s FROM LedgerStatement s
            WHERE s.memberId = :memberId
              AND s.status IN :unsettled
              AND s.paymentDate BETWEEN :from AND :to
            ORDER BY s.paymentDate ASC
            """)
    List<LedgerStatement> findUpcoming(@Param("memberId") Long memberId,
                                       @Param("unsettled") Collection<LedgerStatementStatus> unsettled,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);
}
