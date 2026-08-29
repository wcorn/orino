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

    /**
     * 그 회차로 적힌 행. <b>삭제된 것도 가져온다.</b>
     *
     * <p>되돌리거나 미납 처리해도 행은 남고, {@code UNIQUE(recurring_id, occurrence_date)}가
     * 그 자리를 계속 잡고 있다. 미납이 뒤늦게 빠졌을 때 새로 넣는 대신 <b>이 행을 되살리는</b>
     * 것이 정확하기도 하다 — 안 빠진 게 아니라 늦게 빠진 것이니까.
     */
    Optional<LedgerTransaction> findByRecurringIdAndOccurrenceDate(
            Long recurringId, LocalDate occurrenceDate);

    /** 그 정기 항목으로 적힌 것들. 소급 해지 일괄 되돌리기가 이 목록을 쓴다. */
    List<LedgerTransaction> findAllByRecurringIdAndDeletedAtIsNull(Long recurringId);

    /** 한 번이라도 적혔는가. 무료 체험 종료 임박 신호가 이 사실로 갈린다. */
    boolean existsByRecurringIdAndDeletedAtIsNull(Long recurringId);

    /**
     * 직접 예약 — 예정의 네 출처 중 <b>유일하게 실체화된</b> 것이다(확정 명세 §8.1).
     *
     * <p>재산세·보험 갱신·명절 경조사는 규칙으로 만들 수 없지만 잔액 계획에는 반드시 들어간다.
     */
    List<LedgerTransaction> findAllByMemberIdAndStatusAndDeletedAtIsNullAndOccurredOnBetweenOrderByOccurredOnAscIdAsc(
            Long memberId, LedgerTransactionStatus status, LocalDate from, LocalDate to);

    /** 예정일이 도래한 직접 예약. 배치가 확정으로 승격한다. */
    List<LedgerTransaction> findAllByStatusAndDeletedAtIsNullAndOccurredOnLessThanEqual(
            LedgerTransactionStatus status, LocalDate date);

    /**
     * 날짜별·유형별·상태별 합계. 캘린더가 <b>과거는 확정, 미래는 예정</b>으로 나눠 그린다.
     *
     * <p>한 질의로 둘을 함께 가져오는 이유는 경계 때문이다 — 오늘 날짜에는 이미 쓴 것과
     * 아직 안 나간 것이 함께 있을 수 있고, 그 둘을 다른 질의로 뽑으면 합이 안 맞는 날이 생긴다.
     */
    @Query("""
            SELECT t.occurredOn AS date, t.type AS type, t.status AS status,
                   SUM(t.amount) AS total
            FROM LedgerTransaction t
            WHERE t.memberId = :memberId
              AND t.deletedAt IS NULL
              AND t.occurredOn BETWEEN :from AND :to
            GROUP BY t.occurredOn, t.type, t.status
            """)
    List<DailyFlowTotal> sumDailyByTypeAndStatus(@Param("memberId") Long memberId,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);

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

    /** 자산별 지출(`LDG-082`). <b>카드는 사용 기준</b>이다 — 대금 납부는 이체라 여기 없다. */
    @Query("""
            SELECT t.assetId AS assetId, SUM(t.amount) AS total
            FROM LedgerTransaction t
            WHERE t.memberId = :memberId
              AND t.status = :status
              AND t.deletedAt IS NULL
              AND t.type = ds.project.orino.domain.planner.ledger.entity.LedgerFlow.EXPENSE
              AND t.occurredOn BETWEEN :from AND :to
            GROUP BY t.assetId
            """)
    List<AssetTotal> sumExpenseByAsset(@Param("memberId") Long memberId,
                                       @Param("status") LedgerTransactionStatus status,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    /** 그 구간의 수입 합계. 저축률이 이 값을 분모로 쓴다 — 환불은 수입이 아니라 지출 감소다. */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM LedgerTransaction t
            WHERE t.memberId = :memberId
              AND t.status = :status
              AND t.deletedAt IS NULL
              AND t.type = ds.project.orino.domain.planner.ledger.entity.LedgerFlow.INCOME
              AND t.source <> ds.project.orino.domain.planner.ledger.entity.LedgerTransactionSource.REFUND
              AND t.occurredOn BETWEEN :from AND :to
            """)
    long sumIncome(@Param("memberId") Long memberId,
                   @Param("status") LedgerTransactionStatus status,
                   @Param("from") LocalDate from,
                   @Param("to") LocalDate to);

    /** 그 구간에 할부가 있었나. 두 관점이 벌어지는 <b>이유</b>를 가르는 값이다. */
    @Query("""
            SELECT COUNT(t.id) > 0 FROM LedgerTransaction t
            WHERE t.memberId = :memberId
              AND t.deletedAt IS NULL
              AND t.installmentId IS NOT NULL
              AND t.occurredOn BETWEEN :from AND :to
            """)
    boolean existsInstallmentBetween(@Param("memberId") Long memberId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /**
     * 그 구간에 <b>할부가 아닌</b> 카드 사용이 있었나.
     *
     * <p>두 관점이 벌어지는 이유는 할부만이 아니다 — 이번 달에 긁은 카드값은 다음 달에
     * 청구되므로 <b>사이클 경계</b>만으로도 벌어진다. 원인이 둘일 때 하나만 말하면 나머지
     * 금액이 설명되지 않은 채 남는다.
     */
    @Query("""
            SELECT COUNT(t.id) > 0 FROM LedgerTransaction t
            WHERE t.memberId = :memberId
              AND t.deletedAt IS NULL
              AND t.statementId IS NOT NULL
              AND t.installmentId IS NULL
              AND t.occurredOn BETWEEN :from AND :to
            """)
    boolean existsCardUsageBetween(@Param("memberId") Long memberId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

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
     * 어느 날까지의 자산별·유형별 확정 합계. <b>그 날의 순자산</b>이 여기서 나온다.
     *
     * <p>잔액을 저장하지 않으므로(D-8) 「그때 얼마였나」도 원장을 그 시점까지 다시 더해서
     * 얻는다 — 월말 스냅샷 테이블을 두면 원장과 갈라지는 순간이 생기고, 그때 어느 쪽이
     * 맞는지 알 방법이 없다.
     */
    @Query("""
            SELECT t.assetId AS assetId, t.type AS type, SUM(t.amount) AS total
            FROM LedgerTransaction t
            WHERE t.memberId = :memberId
              AND t.status = :status
              AND t.deletedAt IS NULL
              AND t.occurredOn <= :until
            GROUP BY t.assetId, t.type
            """)
    List<AssetFlowTotal> sumConfirmedByAssetAndTypeUpTo(@Param("memberId") Long memberId,
                                                        @Param("status") LedgerTransactionStatus status,
                                                        @Param("until") LocalDate until);

    /** 그 날까지 이체로 <b>들어온</b> 돈. 위 질의는 나간 쪽만 본다. */
    @Query("""
            SELECT t.counterAssetId AS assetId, SUM(t.amount) AS total
            FROM LedgerTransaction t
            WHERE t.memberId = :memberId
              AND t.status = :status
              AND t.deletedAt IS NULL
              AND t.counterAssetId IS NOT NULL
              AND t.occurredOn <= :until
            GROUP BY t.counterAssetId
            """)
    List<AssetTotal> sumConfirmedByCounterAssetUpTo(@Param("memberId") Long memberId,
                                                    @Param("status") LedgerTransactionStatus status,
                                                    @Param("until") LocalDate until);

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
     * 카테고리별 합계 — <b>유형과 출처까지 묶어서</b> 준다.
     *
     * <p>출처가 필요한 이유는 환불이다. 상쇄 거래는 반대 방향으로 적히지만 원 거래의 카테고리를
     * 물려받으므로, 「그 카테고리의 지출이 줄었다」로 읽으려면 두 줄을 함께 봐야 한다.
     * 환불을 통째로 빼 버리면 환불한 돈이 영원히 지출로 남는다.
     *
     * <p>미분류(NULL)도 한 칸을 차지한다 — 안 보이면 정리하지 않는다.
     */
    @Query("""
            SELECT t.categoryId AS categoryId, t.type AS type, t.source AS source,
                   SUM(t.amount) AS total, COUNT(t.id) AS count
            FROM LedgerTransaction t
            WHERE t.memberId = :memberId
              AND t.status = :status
              AND t.deletedAt IS NULL
              AND t.occurredOn BETWEEN :from AND :to
            GROUP BY t.categoryId, t.type, t.source
            """)
    List<CategoryFlowTotal> sumByCategoryAndFlow(@Param("memberId") Long memberId,
                                                 @Param("status") LedgerTransactionStatus status,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);

    /**
     * 위와 같지만 자산으로 좁힌다.
     *
     * <p>{@code assetIds}가 복수인 이유는 체크카드다. 카드로 쓴 돈은 연결 계좌에서 빠지므로
     * 그 계좌의 분포에도 함께 잡혀야 한다 — 빠지면 잔액과 분포가 서로 다른 이야기를 한다.
     */
    @Query("""
            SELECT t.categoryId AS categoryId, t.type AS type, t.source AS source,
                   SUM(t.amount) AS total, COUNT(t.id) AS count
            FROM LedgerTransaction t
            WHERE t.memberId = :memberId
              AND t.assetId IN :assetIds
              AND t.status = :status
              AND t.deletedAt IS NULL
              AND t.occurredOn BETWEEN :from AND :to
            GROUP BY t.categoryId, t.type, t.source
            """)
    List<CategoryFlowTotal> sumByCategoryAndFlowForAssets(@Param("memberId") Long memberId,
                                                          @Param("assetIds") Collection<Long> assetIds,
                                                          @Param("status") LedgerTransactionStatus status,
                                                          @Param("from") LocalDate from,
                                                          @Param("to") LocalDate to);

    /**
     * 청구서에 편입된 사용 건의 합계 — <b>유형·출처별</b>로 준다.
     *
     * <p>산식이 사용 합계와 환불을 따로 보여줘야 하므로 여기서 합쳐 주지 않는다.
     * 「왜 이 금액이지」에 답하려면 항목이 남아 있어야 한다(확정 명세 §7.4).
     *
     * <p><b>할부 원 거래는 뺀다.</b> 그 돈은 회차로 각 청구월에 나뉘어 들어가므로, 산 달의
     * 사용 합계에도 넣으면 <b>같은 금액이 두 번 청구된다</b>. 원 거래의 전액은 소비 관점(통계)과
     * 부채가 보고, 청구서는 회차만 본다 — 두 관점이 갈라지는 지점이 정확히 여기다(§10.1).
     */
    @Query("""
            SELECT t.type AS type, t.source AS source, SUM(t.amount) AS total
            FROM LedgerTransaction t
            WHERE t.statementId = :statementId
              AND t.deletedAt IS NULL
              AND t.installmentId IS NULL
            GROUP BY t.type, t.source
            """)
    List<StatementFlowTotal> sumByStatement(@Param("statementId") Long statementId);

    /** 그 청구서에 편입된 사용 건들. 화면이 「무엇을 썼나」를 펼쳐 보여준다. */
    List<LedgerTransaction> findAllByStatementIdAndDeletedAtIsNullOrderByOccurredOnAscIdAsc(
            Long statementId);

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

    /** 청구서 안의 유형·출처별 합계 한 줄. */
    interface StatementFlowTotal {
        LedgerFlow getType();

        LedgerTransactionSource getSource();

        long getTotal();
    }

    /** 날짜·유형·상태별 합계 한 줄. 캘린더가 읽는다. */
    interface DailyFlowTotal {
        LocalDate getDate();

        LedgerFlow getType();

        LedgerTransactionStatus getStatus();

        long getTotal();
    }

    /** 카테고리·유형·출처별 합계 한 줄. {@code categoryId}가 null이면 미분류다. */
    interface CategoryFlowTotal {
        Long getCategoryId();

        LedgerFlow getType();

        LedgerTransactionSource getSource();

        long getTotal();

        long getCount();
    }
}
