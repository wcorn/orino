package ds.project.orino.domain.planner.dataset.repository;

import ds.project.orino.domain.planner.dataset.entity.DatasetRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DatasetRowRepository extends JpaRepository<DatasetRow, Long> {

    /** 페이지 조회 — row_index ∈ [from, to). */
    List<DatasetRow>
    findByDatasetIdAndRowIndexGreaterThanEqualAndRowIndexLessThanOrderByRowIndexAsc(
            Long datasetId, int fromInclusive, int toExclusive);

    Optional<DatasetRow> findByDatasetIdAndRowIndex(Long datasetId, int rowIndex);

    long countByDatasetId(Long datasetId);

    /**
     * 삽입 자리 확보 — row_index ≥ from 을 +1. UNIQUE(dataset_id,row_index) 충돌을 피하려
     * 높은 인덱스부터(DESC) 옮긴다. (MySQL은 UPDATE ... ORDER BY 지원)
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE dataset_row SET row_index = row_index + 1 "
            + "WHERE dataset_id = :datasetId AND row_index >= :from ORDER BY row_index DESC",
            nativeQuery = true)
    void shiftUp(@Param("datasetId") Long datasetId, @Param("from") int from);

    /** 삭제 후 메우기 — row_index &gt; from 을 -1. 낮은 인덱스부터(ASC) 옮긴다. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE dataset_row SET row_index = row_index - 1 "
            + "WHERE dataset_id = :datasetId AND row_index > :from ORDER BY row_index ASC",
            nativeQuery = true)
    void shiftDown(@Param("datasetId") Long datasetId, @Param("from") int from);
}
