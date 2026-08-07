package ds.project.orino.domain.planner.travel.repository;

import ds.project.orino.domain.planner.travel.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 여행 조회. <b>상태로 필터하는 쿼리 메서드를 두지 않는다</b> — 상태는 컬럼이 아니라
 * 여행 타임존의 오늘로 파생하는 값이라 SQL에서 판정할 수 없다. 대신 기간 비교로 뽑고
 * 표시 순서만 인덱스에 맞춘다.
 */
public interface TripRepository extends JpaRepository<Trip, Long> {

    Optional<Trip> findByIdAndMemberId(Long id, Long memberId);

    /** 예정·진행 중 목록: 아직 끝나지 않은 여행을 시작일 오름차순으로. {@code idx_trip_member_start}. */
    List<Trip> findAllByMemberIdAndEndDateGreaterThanEqualOrderByStartDateAscIdAsc(
            Long memberId, LocalDate today);

    /** 완료 목록: 이미 끝난 여행을 종료일 내림차순으로. {@code idx_trip_member_end}. */
    List<Trip> findAllByMemberIdAndEndDateLessThanOrderByEndDateDescIdDesc(
            Long memberId, LocalDate today);

    /** 여행 목록 화면 전체. 최근 여행이 위로. */
    List<Trip> findAllByMemberIdOrderByStartDateDescIdDesc(Long memberId);
}
