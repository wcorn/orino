package ds.project.orino.domain.planner.shortlink.repository;

import ds.project.orino.domain.planner.shortlink.entity.ShortlinkVisitDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ShortlinkVisitDailyRepository extends JpaRepository<ShortlinkVisitDaily, Long> {

    /**
     * 방문 한 건을 그날 칸에 더한다. <b>읽어서 +1 하고 저장하지 않는다</b> —
     * 동시에 두 명이 열면 둘 중 하나가 사라진다. UPSERT 한 문장으로 끝낸다(D-12).
     */
    @Modifying
    @Query(value = """
            INSERT INTO shortlink_visit_daily (shortlink_id, visit_date, visit_count, bot_count)
            VALUES (:shortlinkId, :visitDate, :visitCount, :botCount)
            ON DUPLICATE KEY UPDATE
                visit_count = visit_count + VALUES(visit_count),
                bot_count = bot_count + VALUES(bot_count)
            """, nativeQuery = true)
    void accumulate(@Param("shortlinkId") Long shortlinkId,
                    @Param("visitDate") LocalDate visitDate,
                    @Param("visitCount") int visitCount,
                    @Param("botCount") int botCount);

    List<ShortlinkVisitDaily> findAllByShortlinkIdAndVisitDateBetweenOrderByVisitDateAsc(
            Long shortlinkId, LocalDate from, LocalDate to);

    /** 총 방문(사람) · 총 봇. 캐시 컬럼을 두지 않고 매번 센다(데이터 모델 §3). */
    @Query("""
            SELECT COALESCE(SUM(d.visitCount), 0) AS visits,
                   COALESCE(SUM(d.botCount), 0) AS bots
            FROM ShortlinkVisitDaily d
            WHERE d.shortlinkId = :shortlinkId
            """)
    VisitSumProjection sumByShortlinkId(@Param("shortlinkId") Long shortlinkId);

    @Query("""
            SELECT COALESCE(SUM(d.visitCount), 0)
            FROM ShortlinkVisitDaily d
            WHERE d.shortlinkId = :shortlinkId AND d.visitDate >= :from
            """)
    long sumVisitsSince(@Param("shortlinkId") Long shortlinkId, @Param("from") LocalDate from);

    /** 목록 배치 로딩: 링크별 사람 방문 합계를 한 번에. */
    @Query("""
            SELECT d.shortlinkId AS shortlinkId, COALESCE(SUM(d.visitCount), 0) AS total
            FROM ShortlinkVisitDaily d
            WHERE d.shortlinkId IN :shortlinkIds
            GROUP BY d.shortlinkId
            """)
    List<VisitTotalProjection> sumByShortlinkIdIn(
            @Param("shortlinkIds") Collection<Long> shortlinkIds);

    /** {@code /select} 카드의 이번 주 방문 — 멤버의 살아 있는 링크 전체 합계. */
    @Query("""
            SELECT COALESCE(SUM(d.visitCount), 0)
            FROM ShortlinkVisitDaily d
            WHERE d.visitDate >= :from
              AND d.shortlinkId IN (
                    SELECT s.id FROM Shortlink s
                    WHERE s.memberId = :memberId AND s.deletedAt IS NULL)
            """)
    long sumVisitsByMemberSince(@Param("memberId") Long memberId, @Param("from") LocalDate from);

    interface VisitTotalProjection {
        Long getShortlinkId();

        long getTotal();
    }

    interface VisitSumProjection {
        long getVisits();

        long getBots();
    }
}
