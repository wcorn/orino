package ds.project.orino.domain.review.repository;

import ds.project.orino.domain.review.entity.ReviewSchedule;
import ds.project.orino.domain.review.entity.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReviewScheduleRepository
        extends JpaRepository<ReviewSchedule, Long> {

    @Query("select r from ReviewSchedule r " +
            "join r.studyUnit u " +
            "join u.material m " +
            "where m.member.id = :memberId " +
            "and r.status in :statuses " +
            "and r.scheduledDate <= :dueDate " +
            "order by r.scheduledDate asc, r.sequence asc")
    List<ReviewSchedule> findDueByMember(@Param("memberId") Long memberId,
                                         @Param("dueDate") LocalDate dueDate,
                                         @Param("statuses") List<ReviewStatus> statuses);

    @Query("select r from ReviewSchedule r " +
            "join r.studyUnit u " +
            "join u.material m " +
            "where m.member.id = :memberId " +
            "and r.scheduledDate = :date " +
            "and r.status = :status")
    List<ReviewSchedule> findByMemberAndDateAndStatus(@Param("memberId") Long memberId,
                                                      @Param("date") LocalDate date,
                                                      @Param("status") ReviewStatus status);

    @Query("select r from ReviewSchedule r " +
            "where r.studyUnit.id = :studyUnitId " +
            "and r.status in :statuses " +
            "order by r.sequence asc")
    List<ReviewSchedule> findByStudyUnitIdAndStatusIn(
            @Param("studyUnitId") Long studyUnitId,
            @Param("statuses") List<ReviewStatus> statuses);

    @Query("select r from ReviewSchedule r " +
            "where r.studyUnit.id = :studyUnitId " +
            "and r.sequence > :sequence " +
            "and r.status = :status " +
            "order by r.sequence asc")
    List<ReviewSchedule> findUpcomingByStudyUnit(
            @Param("studyUnitId") Long studyUnitId,
            @Param("sequence") int sequence,
            @Param("status") ReviewStatus status);

    @Query("select coalesce(max(r.sequence), 0) from ReviewSchedule r " +
            "where r.studyUnit.id = :studyUnitId")
    int findMaxSequenceByStudyUnit(
            @Param("studyUnitId") Long studyUnitId);
}
