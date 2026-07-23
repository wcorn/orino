package ds.project.orino.domain.planner.lifelog.repository;

import ds.project.orino.domain.planner.lifelog.entity.MomentPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MomentPhotoRepository extends JpaRepository<MomentPhoto, Long> {

    List<MomentPhoto> findAllByMomentIdOrderBySortOrderAscIdAsc(Long momentId);

    /** 피드 배치 로딩: 여러 기록의 사진을 한 번에. */
    List<MomentPhoto> findAllByMomentIdInOrderBySortOrderAscIdAsc(Collection<Long> momentIds);

    void deleteByMomentId(Long momentId);
}
