package ds.project.orino.domain.planner.lifelog.repository;

import ds.project.orino.domain.planner.lifelog.entity.Moment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MomentRepository extends JpaRepository<Moment, Long> {

    Optional<Moment> findByIdAndMemberId(Long id, Long memberId);

    /** 피드 기본 정렬(역시간순). 커서 페이지네이션은 #952에서 얹는다. */
    List<Moment> findAllByMemberIdOrderByOccurredAtDescIdDesc(Long memberId);
}
