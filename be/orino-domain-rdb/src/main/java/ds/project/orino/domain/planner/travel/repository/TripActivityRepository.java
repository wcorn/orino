package ds.project.orino.domain.planner.travel.repository;

import ds.project.orino.domain.planner.travel.entity.TripActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 일정 조회. 모든 접근이 {@code idx_activity_trip_date_order (trip_id, activity_date, sort_order)}를
 * 탄다.
 *
 * <p>정렬은 {@code sortOrder}만 본다 — 시각으로 정렬하지 않는다(시각 없는 일정이 허용되고
 * 드래그 순서가 시각보다 우선한다). 동점은 {@code id}로 갈라 순서를 결정적으로 만든다.
 */
public interface TripActivityRepository extends JpaRepository<TripActivity, Long> {

    Optional<TripActivity> findByIdAndTripId(Long id, Long tripId);

    /**
     * 보드 조회 — 한 여행의 전 일정을 한 번에 읽는다. 날짜별 그룹핑은 애플리케이션에서 한다.
     * 보관함({@code activity_date IS NULL})이 MySQL NULLS-FIRST 규칙에 따라 앞에 온다.
     */
    List<TripActivity> findAllByTripIdOrderByActivityDateAscSortOrderAscIdAsc(Long tripId);

    /** 특정 날짜의 일정. 재인덱싱·삽입 위치 계산에 쓴다. */
    List<TripActivity> findAllByTripIdAndActivityDateOrderBySortOrderAscIdAsc(
            Long tripId, LocalDate activityDate);

    /** 미배정 보관함. {@code activityDate IS NULL}은 파생 쿼리로 표현할 수 없어 직접 쓴다. */
    @Query("""
            SELECT a FROM TripActivity a
            WHERE a.tripId = :tripId AND a.activityDate IS NULL
            ORDER BY a.sortOrder ASC, a.id ASC
            """)
    List<TripActivity> findUnscheduled(@Param("tripId") Long tripId);

    /** 새 일정을 맨 뒤에 붙일 때 쓸 다음 순서값. 빈 날짜면 0. */
    @Query("""
            SELECT COALESCE(MAX(a.sortOrder) + 1, 0) FROM TripActivity a
            WHERE a.tripId = :tripId
              AND (:activityDate IS NULL AND a.activityDate IS NULL
                   OR a.activityDate = :activityDate)
            """)
    int nextSortOrder(@Param("tripId") Long tripId, @Param("activityDate") LocalDate activityDate);

    /**
     * 여행 기간이 줄어 기간 밖으로 밀려난 일정. 보관함으로 내릴 대상을 고른다.
     * 보관함 일정({@code activityDate IS NULL})은 애초에 대상이 아니다.
     */
    @Query("""
            SELECT a FROM TripActivity a
            WHERE a.tripId = :tripId
              AND a.activityDate IS NOT NULL
              AND (a.activityDate < :startDate OR a.activityDate > :endDate)
            ORDER BY a.activityDate ASC, a.sortOrder ASC, a.id ASC
            """)
    List<TripActivity> findOutsidePeriod(@Param("tripId") Long tripId,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    long countByTripId(Long tripId);

    /**
     * 기간 밖으로 밀려난 일정 수. 확인 모달·409 응답이 개수만 필요로 해서 목록 없이 센다.
     */
    @Query("""
            SELECT COUNT(a) FROM TripActivity a
            WHERE a.tripId = :tripId
              AND a.activityDate IS NOT NULL
              AND (a.activityDate < :startDate OR a.activityDate > :endDate)
            """)
    long countOutsidePeriod(@Param("tripId") Long tripId,
                            @Param("startDate") LocalDate startDate,
                            @Param("endDate") LocalDate endDate);

    /** 여행별 일정 수를 한 번에 센다 — 목록 화면의 N+1을 막는다. */
    @Query("""
            SELECT new ds.project.orino.domain.planner.travel.repository.TripActivityCount(
                       a.tripId, COUNT(a))
            FROM TripActivity a
            WHERE a.tripId IN :tripIds
            GROUP BY a.tripId
            """)
    List<TripActivityCount> countByTripIds(@Param("tripIds") List<Long> tripIds);
}
