package ds.project.orino.domain.planner.shortlink.repository;

import ds.project.orino.domain.planner.shortlink.entity.ShortlinkVisit;
import ds.project.orino.domain.planner.shortlink.entity.VisitDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 방문 원시 기록. <b>유입 경로 · 기기 · 국가는 여기서만 나온다</b> — 90일 창을 넘어가면
 * 비어 간다(명세 §8.3). 총 방문과 일별 추이는 집계 테이블이 답하므로 그쪽은 영구히 남는다.
 */
public interface ShortlinkVisitRepository extends JpaRepository<ShortlinkVisit, Long> {

    /** 마지막 사람 방문. 90일이 지나면 원시가 없어 null이 된다. */
    @Query("""
            SELECT MAX(v.visitedAt) FROM ShortlinkVisit v
            WHERE v.shortlinkId = :shortlinkId AND v.bot = false
            """)
    Instant findLastHumanVisitAt(@Param("shortlinkId") Long shortlinkId);

    /** 목록 배치 로딩: 링크별 마지막 사람 방문을 한 번에. */
    @Query("""
            SELECT v.shortlinkId AS shortlinkId, MAX(v.visitedAt) AS lastVisitedAt
            FROM ShortlinkVisit v
            WHERE v.shortlinkId IN :shortlinkIds AND v.bot = false
            GROUP BY v.shortlinkId
            """)
    List<LastVisitProjection> findLastHumanVisitByShortlinkIdIn(
            @Param("shortlinkIds") Collection<Long> shortlinkIds);

    /** 유입 경로 — 도메인까지만. 리퍼러 없는 방문(직접 입력·앱 내 이동)은 세지 않는다. */
    @Query("""
            SELECT v.referrerDomain AS name, COUNT(v.id) AS count
            FROM ShortlinkVisit v
            WHERE v.shortlinkId = :shortlinkId AND v.bot = false
              AND v.visitedAt >= :from AND v.referrerDomain IS NOT NULL
            GROUP BY v.referrerDomain
            ORDER BY COUNT(v.id) DESC, v.referrerDomain ASC
            """)
    List<NameCountProjection> countReferrers(@Param("shortlinkId") Long shortlinkId,
                                             @Param("from") Instant from);

    @Query("""
            SELECT v.device AS device, COUNT(v.id) AS count
            FROM ShortlinkVisit v
            WHERE v.shortlinkId = :shortlinkId AND v.bot = false AND v.visitedAt >= :from
            GROUP BY v.device
            ORDER BY COUNT(v.id) DESC
            """)
    List<DeviceCountProjection> countDevices(@Param("shortlinkId") Long shortlinkId,
                                             @Param("from") Instant from);

    /** 국가 — 판정에 실패한 방문(NULL)은 빼고 센다. 수단은 #1241에서 붙는다. */
    @Query("""
            SELECT v.country AS name, COUNT(v.id) AS count
            FROM ShortlinkVisit v
            WHERE v.shortlinkId = :shortlinkId AND v.bot = false
              AND v.visitedAt >= :from AND v.country IS NOT NULL
            GROUP BY v.country
            ORDER BY COUNT(v.id) DESC, v.country ASC
            """)
    List<NameCountProjection> countCountries(@Param("shortlinkId") Long shortlinkId,
                                             @Param("from") Instant from);

    /**
     * 90일 초과 원시 삭제. <b>집계 테이블은 건드리지 않는다</b> — 원시가 사라져도 일별
     * 막대 그래프가 남는 것이 이 설계의 핵심이다.
     */
    @Modifying
    @Query("DELETE FROM ShortlinkVisit v WHERE v.visitedAt < :threshold")
    int deleteOlderThan(@Param("threshold") Instant threshold);

    interface LastVisitProjection {
        Long getShortlinkId();

        Instant getLastVisitedAt();
    }

    interface NameCountProjection {
        String getName();

        long getCount();
    }

    interface DeviceCountProjection {
        VisitDevice getDevice();

        long getCount();
    }
}
