package ds.project.orino.domain.planner.travel.repository;

import ds.project.orino.domain.planner.travel.entity.TripActivityPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 기록 사진. 모든 접근이 {@code idx_photo_log (log_id, sort_order)}를 탄다. */
public interface TripActivityPhotoRepository extends JpaRepository<TripActivityPhoto, Long> {

    List<TripActivityPhoto> findAllByLogIdOrderBySortOrderAscIdAsc(Long logId);

    /** 여러 기록의 사진을 한 번에 읽는다 - 보드가 기록 수만큼 쿼리를 날리지 않게. */
    List<TripActivityPhoto> findAllByLogIdInOrderBySortOrderAscIdAsc(List<Long> logIds);

    long countByLogId(Long logId);

    /** 새 사진을 맨 뒤에 붙일 때 쓸 다음 순서값. 사진이 없으면 0. */
    default int nextSortOrder(Long logId) {
        return findAllByLogIdOrderBySortOrderAscIdAsc(logId).stream()
                .mapToInt(TripActivityPhoto::getSortOrder)
                .max()
                .orElse(-1) + 1;
    }
}
