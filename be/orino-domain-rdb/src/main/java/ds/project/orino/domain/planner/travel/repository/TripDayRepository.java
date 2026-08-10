package ds.project.orino.domain.planner.travel.repository;

import ds.project.orino.domain.planner.travel.entity.TripDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 여행 날짜 조회. 보드는 <b>기간 전체를 한 번에</b> 읽는다 — 길어야 한 달이라 날짜별로 끊어
 * 읽을 이유가 없고, 구간(Leg) 파생은 어차피 연속된 날짜를 전부 봐야 한다.
 */
public interface TripDayRepository extends JpaRepository<TripDay, Long> {

    /** 날짜 오름차순 — 구간 파생이 연속성을 보려면 순서가 보장돼야 한다. */
    List<TripDay> findAllByTripIdOrderByDayDateAsc(Long tripId);

    /** 목록 화면용 배치 조회 — 여행 수만큼 날짜를 따로 읽지 않는다(N+1 회피). */
    List<TripDay> findAllByTripIdInOrderByTripIdAscDayDateAsc(Collection<Long> tripIds);

    Optional<TripDay> findByTripIdAndDayDate(Long tripId, LocalDate dayDate);

    /** 기간이 줄었을 때 잘린 날짜만 지운다. */
    void deleteAllByTripIdAndDayDateBefore(Long tripId, LocalDate dayDate);

    void deleteAllByTripIdAndDayDateAfter(Long tripId, LocalDate dayDate);

    long countByTripId(Long tripId);
}
