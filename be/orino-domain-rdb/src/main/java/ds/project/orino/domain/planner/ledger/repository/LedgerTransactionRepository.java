package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransaction;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionSource;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 원장 조회. <b>삭제된 행은 어느 집계에도 들어가지 않는다</b> — 모든 질의가
 * {@code deletedAt IS NULL}을 건다.
 *
 * <p>잔액을 컬럼으로 두지 않기로 했으므로(D-8) 여기 있는 합계 질의가 곧 잔액의 정의다.
 * 두 벌로 나눠 두었다 — 자산 쪽({@link #sumConfirmedByAssetAndType})과 이체받는
 * 쪽({@link #sumConfirmedByCounterAsset}). 한 질의로 합치면 같은 행을 두 번 세게 된다.
 */
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

    Optional<LedgerTransaction> findByIdAndMemberIdAndDeletedAtIsNull(Long id, Long memberId);

    List<LedgerTransaction> findAllByMemberIdAndIdInAndDeletedAtIsNull(
            Long memberId, Collection<Long> ids);

    /** 상쇄 거래들. 원 거래를 지우지 않으므로 「이 거래가 얼마나 환불됐나」는 여기서 나온다. */
    List<LedgerTransaction> findAllByMemberIdAndRefundOfIdAndDeletedAtIsNull(
            Long memberId, Long refundOfId);

    /** 내역 타임라인. 확정과 예정을 함께 담아 같은 스크롤 위에 놓는다. */
    List<LedgerTransaction> findAllByMemberIdAndDeletedAtIsNullAndOccurredOnBetweenOrderByOccurredOnDescIdDesc(
            Long memberId, LocalDate from, LocalDate to);

    /**
     * 그 자산의 내역. <b>이체받는 쪽도 포함한다</b> — 「이 통장에 무슨 일이 있었나」에는
     * 나간 돈과 들어온 돈이 함께 답해야 한다.
     */
    @Query("""
            SELECT t FROM LedgerTransaction t
            WHERE t.memberId = :memberId
              AND t.deletedAt IS NULL
              AND (t.assetId IN :assetIds OR t.counterAssetId IN :assetIds)
            ORDER BY t.occurredOn ASC, t.id ASC
            """)
    List<LedgerTransaction> findAllForAssetsOldestFirst(@Param("memberId") Long memberId,
                                                        @Param("assetIds") Collection<Long> assetIds);

    /**
     * 자산별·유형별 확정 합계. 잔액(입출금·저축·현금·간편결제)과 부채(신용카드 미결제)가
     * 모두 이 한 벌에서 파생된다.
     */
    @Query("""
            SELECT t.assetId AS assetId, t.type AS type, SUM(t.amount) AS total
            FROM LedgerTransaction t
            WHERE t.memberId = :memberId
              AND t.status = :status
              AND t.deletedAt IS NULL
            GROUP BY t.assetId, t.type
            """)
    List<AssetFlowTotal> sumConfirmedByAssetAndType(@Param("memberId") Long memberId,
                                                    @Param("status") LedgerTransactionStatus status);

    /** 이체로 <b>들어온</b> 돈. 위 질의는 나간 쪽만 본다. */
    @Query("""
            SELECT t.counterAssetId AS assetId, SUM(t.amount) AS total
            FROM LedgerTransaction t
            WHERE t.memberId = :memberId
              AND t.status = :status
              AND t.deletedAt IS NULL
              AND t.counterAssetId IS NOT NULL
            GROUP BY t.counterAssetId
            """)
    List<AssetTotal> sumConfirmedByCounterAsset(@Param("memberId") Long memberId,
                                                @Param("status") LedgerTransactionStatus status);

    /**
     * 기간 합계 — 유형과 출처를 함께 묶는다.
     *
     * <p>출처가 필요한 이유는 <b>환불</b> 때문이다. 상쇄 거래는 반대 방향으로 적히지만
     * 「수입이 늘었다」가 아니라 「지출이 줄었다」로 읽혀야 한다(확정 명세 §4.3).
     * 그 판정을 서비스가 하려면 출처가 합계에 남아 있어야 한다.
     */
    @Query("""
            SELECT t.type AS type, t.source AS source, t.status AS status, SUM(t.amount) AS total
            FROM LedgerTransaction t
            WHERE t.memberId = :memberId
              AND t.deletedAt IS NULL
              AND t.occurredOn BETWEEN :from AND :to
            GROUP BY t.type, t.source, t.status
            """)
    List<FlowSourceTotal> sumByTypeAndSource(@Param("memberId") Long memberId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /**
     * 자산 상세의 카테고리 분포. 미분류(NULL)도 한 칸을 차지한다 — 안 보이면 정리하지 않는다.
     *
     * <p>{@code assetIds}가 복수인 이유는 체크카드다. 카드로 쓴 돈은 연결 계좌에서 빠지므로
     * 그 계좌의 분포에도 함께 잡혀야 한다 — 빠지면 잔액과 분포가 서로 다른 이야기를 한다.
     */
    @Query("""
            SELECT t.categoryId AS categoryId, SUM(t.amount) AS total, COUNT(t.id) AS count
            FROM LedgerTransaction t
            WHERE t.memberId = :memberId
              AND t.assetId IN :assetIds
              AND t.type = :type
              AND t.source <> :excludedSource
              AND t.status = :status
              AND t.deletedAt IS NULL
              AND t.occurredOn BETWEEN :from AND :to
            GROUP BY t.categoryId
            ORDER BY SUM(t.amount) DESC
            """)
    List<CategoryTotal> sumByCategoryForAsset(@Param("memberId") Long memberId,
                                              @Param("assetIds") Collection<Long> assetIds,
                                              @Param("type") LedgerFlow type,
                                              @Param("excludedSource") LedgerTransactionSource excludedSource,
                                              @Param("status") LedgerTransactionStatus status,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    /** 대시보드의 「정리할 내역」. 이체는 애초에 분류 대상이 아니라 세지 않는다. */
    @Query("""
            SELECT COUNT(t.id) FROM LedgerTransaction t
            WHERE t.memberId = :memberId
              AND t.deletedAt IS NULL
              AND t.categoryId IS NULL
              AND t.type <> ds.project.orino.domain.planner.ledger.entity.LedgerFlow.TRANSFER
            """)
    long countUncategorized(@Param("memberId") Long memberId);

    /** 내용 자동완성. 최근 것부터 훑어 서비스가 제목 단위로 추린다. */
    @Query("""
            SELECT t FROM LedgerTransaction t
            WHERE t.memberId = :memberId
              AND t.deletedAt IS NULL
              AND t.title IS NOT NULL
              AND LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY t.occurredOn DESC, t.id DESC
            """)
    List<LedgerTransaction> searchByTitle(@Param("keyword") String keyword,
                                          @Param("memberId") Long memberId,
                                          Limit limit);

    /**
     * 카테고리 통합 — <b>내역이 따라간다</b>. 거래를 지우지 않고 소속만 옮긴다.
     * 삭제된 행도 함께 옮긴다: 되살렸을 때 사라진 카테고리를 가리키고 있으면 안 된다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE LedgerTransaction t SET t.categoryId = :targetCategoryId
            WHERE t.memberId = :memberId AND t.categoryId = :sourceCategoryId
            """)
    int moveCategory(@Param("memberId") Long memberId,
                     @Param("sourceCategoryId") Long sourceCategoryId,
                     @Param("targetCategoryId") Long targetCategoryId);

    /** 자산별·유형별 합계 한 줄. */
    interface AssetFlowTotal {
        Long getAssetId();

        LedgerFlow getType();

        long getTotal();
    }

    /** 자산별 합계 한 줄. */
    interface AssetTotal {
        Long getAssetId();

        long getTotal();
    }

    /** 유형·출처·상태별 합계 한 줄. */
    interface FlowSourceTotal {
        LedgerFlow getType();

        LedgerTransactionSource getSource();

        LedgerTransactionStatus getStatus();

        long getTotal();
    }

    /** 카테고리별 합계 한 줄. {@code categoryId}가 null이면 미분류다. */
    interface CategoryTotal {
        Long getCategoryId();

        long getTotal();

        long getCount();
    }
}
