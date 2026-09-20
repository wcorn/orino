package ds.project.orino.domain.planner.travel.repository;

import ds.project.orino.domain.planner.travel.entity.TripExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 여행 지출 조회. 예전에는 가계부의 원장 리포지토리가 이 자리였다 — 경비가
 * 원장 위의 읽기 뷰였기 때문이다(D-27). 이제 여행이 자기 장부를 읽는다.
 *
 * <p>유형 필터가 사라진 것이 눈에 띄는 차이다. 원장에서는 <b>이체와 수입을 빼는 일</b>이
 * 질의마다 붙어 있었지만(카드 대금 납부가 여행 경비로 새는 구멍), 여기에는 지출만 있다.
 */
public interface TripExpenseRepository extends JpaRepository<TripExpense, Long> {

    /**
     * 그 여행의 지출 전부. 날짜순이라 화면이 「출발 전 / N일차 / 다녀온 뒤」로 그대로 나눈다.
     * 인덱스 {@code idx_trip_expense_trip_date}를 탄다.
     */
    List<TripExpense> findAllByTripIdAndDeletedAtIsNullOrderByOccurredOnAscIdAsc(Long tripId);

    /**
     * 내 지출 하나. <b>남의 것은 빈 값</b>이라 호출부가 404로 답한다 — 403이면
     * 「그 id의 지출은 있다」가 새어나간다.
     */
    Optional<TripExpense> findByIdAndMemberIdAndDeletedAtIsNull(Long id, Long memberId);

    /**
     * 여행별 <b>확정</b> 지출 합계. 사이드바 여행 트리와 폴백 화면이 「경비 41.2만」에 쓴다.
     *
     * <p>조건은 위 목록 질의와 같고 확정만 남기는 것 하나가 더 붙는다 — 경비 화면의 「썼다」가
     * 확정만 세기 때문이다. <b>두 질의는 같이 고친다</b>: 사이드바가 화면과 다른 값을 쓰면
     * 사용자에게는 어느 쪽이 맞는지 알 방법이 없다.
     *
     * <p>목록을 읽어 더하지 않는 이유는 여행 수에 비례하기 때문이다. 요약은 화면을 옮길 때마다
     * 부르는 자리라, 숫자 하나를 얻자고 그 여행 지출 전부를 끌어오지 않는다.
     */
    @Query("""
            SELECT e.tripId AS tripId, SUM(e.amount) AS total
            FROM TripExpense e
            WHERE e.tripId IN :tripIds
              AND e.deletedAt IS NULL
              AND e.status = ds.project.orino.domain.planner.travel.entity.TripExpenseStatus.CONFIRMED
            GROUP BY e.tripId
            """)
    List<TripTotal> sumConfirmedByTrip(@Param("tripIds") Collection<Long> tripIds);

    /** 여행 하나의 확정 지출 합계 한 줄. */
    interface TripTotal {
        Long getTripId();

        long getTotal();
    }
}
