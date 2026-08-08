package ds.project.orino.domain.planner.travel.repository;

import ds.project.orino.domain.planner.travel.entity.TripActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 일정 사후 기록. 일정당 최대 1건이라 조회는 항상 단건이다. */
public interface TripActivityLogRepository extends JpaRepository<TripActivityLog, Long> {

    Optional<TripActivityLog> findByActivityId(Long activityId);

    /**
     * 여러 일정의 기록을 한 번에 읽는다 — 보드는 일정 수만큼 행을 그리는데 기록을 건건이
     * 조회하면 그대로 N+1이 된다.
     */
    List<TripActivityLog> findAllByActivityIdIn(List<Long> activityIds);
}
