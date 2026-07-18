package ds.project.orino.planner.dataset.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.dataset.entity.DatasetMerge;
import ds.project.orino.domain.planner.dataset.entity.DatasetRow;
import ds.project.orino.domain.planner.dataset.repository.DatasetMergeRepository;
import ds.project.orino.domain.planner.dataset.repository.DatasetRowRepository;
import ds.project.orino.planner.dataset.dto.DatasetColumn;
import ds.project.orino.planner.dataset.dto.MergeView;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 셀 병합 저장·조회. 값은 {@code dataset_row.cells}에 직사각형 그대로 두고 span만
 * {@code dataset_merge}에 담는다 — 수식·서식과 같은 sparse 오버레이 전략이라 읽기 경로가 안 바뀐다.
 *
 * <p>병합 영역은 정체성(앵커 {@code row_id}·{@code col_key})으로 저장하지만 본질적으로 위치
 * (인접 행·열의 직사각형)이므로, 검증은 앵커의 <b>행 번호·열 인덱스</b>로 기하를 따진다.
 */
@Service
public class DatasetMergeService {

    private final DatasetMergeRepository mergeRepository;
    private final DatasetRowRepository rowRepository;

    public DatasetMergeService(DatasetMergeRepository mergeRepository,
                               DatasetRowRepository rowRepository) {
        this.mergeRepository = mergeRepository;
        this.rowRepository = rowRepository;
    }

    /**
     * 앵커 기준으로 직사각형 영역을 병합한다(통째 교체). 이미 병합이 있으면 span을 갱신한다.
     *
     * <p>검증: 앵커 열 존재(404) · 실제 병합이 되도록 {@code (1,1)} 금지 · 오른쪽/아래 경계 초과 금지 ·
     * 다른 병합과 직사각형이 겹침 금지(모두 400). 가로·세로 병합을 함께 허용한다.
     */
    public void setMerge(Long datasetId, DatasetRow anchorRow, String anchorColKey,
                         int rowSpan, int colSpan, List<DatasetColumn> columns, int rowCount) {
        int atCol = indexOf(columns, anchorColKey);
        if (atCol < 0) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        // (1,1)은 병합이 아니다 — 해제는 별도 DELETE로 표현한다.
        if (rowSpan == 1 && colSpan == 1) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "병합할 범위가 없습니다.");
        }
        if (atCol + colSpan > columns.size()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "병합 범위가 열 수를 넘습니다.");
        }
        int atRow = anchorRow.getRowIndex();
        if (atRow + rowSpan > rowCount) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "병합 범위가 행 수를 넘습니다.");
        }
        // 다른 병합과 직사각형이 겹치면 거부한다(가로·세로 모두 2D 교차로 본다).
        Rect target = new Rect(atRow, atCol, rowSpan, colSpan);
        for (DatasetMerge other : mergeRepository.findByDatasetId(datasetId)) {
            if (other.getAnchorRowId().equals(anchorRow.getId())
                    && other.getAnchorColKey().equals(anchorColKey)) {
                continue; // 자기 자신(범위 갱신)은 겹침이 아니다.
            }
            Rect otherRect = rectOf(other, columns);
            if (otherRect != null && target.intersects(otherRect)) {
                throw new CustomException(ErrorCode.INVALID_REQUEST, "다른 병합과 겹칩니다.");
            }
        }

        DatasetMerge existing = mergeRepository
                .findByAnchorRowIdAndAnchorColKey(anchorRow.getId(), anchorColKey).orElse(null);
        if (existing == null) {
            mergeRepository.save(
                    new DatasetMerge(datasetId, anchorRow.getId(), anchorColKey, rowSpan, colSpan));
        } else {
            existing.update(rowSpan, colSpan);
        }
    }

    /** 병합 해제. 없으면 아무 일도 안 한다(멱등). */
    public void unmerge(Long anchorRowId, String anchorColKey) {
        mergeRepository.findByAnchorRowIdAndAnchorColKey(anchorRowId, anchorColKey)
                .ifPresent(mergeRepository::delete);
    }

    /**
     * 그 dataset의 병합 전체를 표시형으로. 앵커 {@code row_id}를 <b>행 번호</b>로 바꿔 준다 —
     * 세로 병합은 앵커 행이 화면 밖에 있어도 덮인 행을 그려야 해서 FE가 전체를 갖고 있어야 한다.
     * 병합은 sparse라 대개 적다.
     */
    public List<MergeView> allMerges(Long datasetId) {
        List<DatasetMerge> merges = mergeRepository.findByDatasetId(datasetId);
        if (merges.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> rowIndexById = rowIndexById(merges);
        return merges.stream()
                .map(m -> {
                    Integer rowIndex = rowIndexById.get(m.getAnchorRowId());
                    return rowIndex == null ? null
                            : new MergeView(rowIndex, m.getAnchorColKey(),
                                    m.getRowSpan(), m.getColSpan());
                })
                .filter(v -> v != null)
                .sorted((a, b) -> a.rowIndex() != b.rowIndex()
                        ? Integer.compare(a.rowIndex(), b.rowIndex())
                        : a.colKey().compareTo(b.colKey()))
                .toList();
    }

    /**
     * 열이 지워졌을 때 그 열에 걸친 병합을 해제한다 — 영역이 온전할 수 없어졌다(#829 O3 "깨지면 해제").
     * 앵커가 그 열이거나, 다른 열 앵커의 span이 그 열을 덮는 경우 모두 지운다.
     */
    public void invalidateColumn(Long datasetId, String colKey, List<DatasetColumn> columns) {
        int gone = indexOf(columns, colKey);
        for (DatasetMerge merge : mergeRepository.findByDatasetId(datasetId)) {
            if (merge.getAnchorColKey().equals(colKey)) {
                mergeRepository.delete(merge);
                continue;
            }
            if (gone < 0) {
                continue;
            }
            int at = indexOf(columns, merge.getAnchorColKey());
            if (at >= 0 && at < gone && gone < at + merge.getColSpan()) {
                mergeRepository.delete(merge); // 이 병합의 span이 지워질 열을 덮는다.
            }
        }
    }

    /**
     * 열 순서가 바뀌면 병합 영역이 불연속이 될 수 있어 그 dataset의 병합을 모두 해제한다
     * (#829 O3, v1 보수적 처리). 병합은 sparse라 대개 없거나 적다.
     */
    public void invalidateAllOnReorder(Long datasetId) {
        List<DatasetMerge> merges = mergeRepository.findByDatasetId(datasetId);
        if (!merges.isEmpty()) {
            mergeRepository.deleteAll(merges);
        }
    }

    /** 병합들의 앵커 행 번호를 한 번에 조회한다. */
    private Map<Long, Integer> rowIndexById(List<DatasetMerge> merges) {
        List<Long> ids = merges.stream().map(DatasetMerge::getAnchorRowId).distinct().toList();
        Map<Long, Integer> byId = new HashMap<>();
        for (DatasetRow row : rowRepository.findAllById(ids)) {
            byId.put(row.getId(), row.getRowIndex());
        }
        return byId;
    }

    /** 병합의 직사각형(행 번호·열 인덱스 기반). 앵커 행/열을 못 찾으면 null. */
    private Rect rectOf(DatasetMerge merge, List<DatasetColumn> columns) {
        int atCol = indexOf(columns, merge.getAnchorColKey());
        if (atCol < 0) {
            return null;
        }
        Integer atRow = rowRepository.findById(merge.getAnchorRowId())
                .map(DatasetRow::getRowIndex).orElse(null);
        if (atRow == null) {
            return null;
        }
        return new Rect(atRow, atCol, merge.getRowSpan(), merge.getColSpan());
    }

    private int indexOf(List<DatasetColumn> columns, String key) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /** 행·열 인덱스 직사각형. 겹침 판정용. */
    private record Rect(int row, int col, int rowSpan, int colSpan) {
        boolean intersects(Rect o) {
            return row < o.row + o.rowSpan && o.row < row + rowSpan
                    && col < o.col + o.colSpan && o.col < col + colSpan;
        }
    }
}
