package ds.project.orino.domain.planner.travel.repository;

import ds.project.orino.domain.planner.travel.entity.TripStay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 숙소 조회. 겹침 검증도 목록 조회도 <b>여행 전체</b>를 읽어 애플리케이션에서 판정한다 —
 * 여행 하나에 숙소는 많아야 예닐곱 건이고, 겹침 규칙({@code [in, out)} 반열린 구간)을
 * SQL로 표현하면 체크아웃일 경계에서 틀리기 쉽다.
 */
public interface TripStayRepository extends JpaRepository<TripStay, Long> {

    List<TripStay> findAllByTripIdOrderByCheckInDateAscIdAsc(Long tripId);

    Optional<TripStay> findByIdAndTripId(Long id, Long tripId);
}
