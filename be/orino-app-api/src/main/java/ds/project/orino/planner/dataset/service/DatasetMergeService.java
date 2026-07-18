package ds.project.orino.planner.dataset.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.dataset.entity.DatasetMerge;
import ds.project.orino.domain.planner.dataset.repository.DatasetMergeRepository;
import ds.project.orino.planner.dataset.dto.DatasetColumn;
import ds.project.orino.planner.dataset.dto.MergeSpec;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 셀 병합 저장·조회. 값은 {@code dataset_row.cells}에 직사각형 그대로 두고 span만
 * {@code dataset_merge}에 담는다 — 수식·서식과 같은 sparse 오버레이 전략이라 읽기 경로가 안 바뀐다.
 *
 * <p>슬라이스 1은 <b>가로 병합만</b>(rowSpan=1). 세로 병합은 슬라이스 2에서 이 서비스의 검증만
 * 완화하면 된다(스키마는 이미 rowSpan을 담는다).
 */
@Service
public class DatasetMergeService {

    private final DatasetMergeRepository mergeRepository;

    public DatasetMergeService(DatasetMergeRepository mergeRepository) {
        this.mergeRepository = mergeRepository;
    }

    /**
     * 앵커 기준으로 영역을 병합한다(통째 교체). 이미 병합이 있으면 span을 갱신한다.
     *
     * <p>검증: 앵커 열 존재(404) · 슬라이스 1은 rowSpan=1 · 실제 병합이 되도록 colSpan&ge;2 ·
     * 오른쪽 경계 초과 금지 · 같은 행의 다른 병합과 겹침 금지(모두 400).
     */
    public void setMerge(Long datasetId, Long anchorRowId, String anchorColKey,
                         int rowSpan, int colSpan, List<DatasetColumn> columns) {
        int at = indexOf(columns, anchorColKey);
        if (at < 0) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        // 슬라이스 1 — 가로 병합만. 스키마는 세로 span을 담지만 아직 렌더가 없다.
        if (rowSpan != 1) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "세로 병합은 아직 지원하지 않습니다.");
        }
        // (1,1)은 병합이 아니다 — 해제는 별도 DELETE로 표현한다.
        if (colSpan < 2) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "병합할 범위가 없습니다.");
        }
        // 오른쪽 끝을 넘으면 담을 열이 없다.
        if (at + colSpan > columns.size()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "병합 범위가 열 수를 넘습니다.");
        }
        // 같은 행의 다른 병합과 겹치면 거부한다(가로 병합이라 겹침=열 구간 교차).
        int newEnd = at + colSpan;
        for (DatasetMerge other : mergeRepository.findByAnchorRowIdIn(List.of(anchorRowId))) {
            if (other.getAnchorColKey().equals(anchorColKey)) {
                continue; // 자기 자신(범위 갱신)은 겹침이 아니다.
            }
            int otherAt = indexOf(columns, other.getAnchorColKey());
            if (otherAt < 0) {
                continue;
            }
            int otherEnd = otherAt + other.getColSpan();
            if (at < otherEnd && otherAt < newEnd) {
                throw new CustomException(ErrorCode.INVALID_REQUEST, "다른 병합과 겹칩니다.");
            }
        }

        DatasetMerge existing = mergeRepository
                .findByAnchorRowIdAndAnchorColKey(anchorRowId, anchorColKey).orElse(null);
        if (existing == null) {
            mergeRepository.save(new DatasetMerge(datasetId, anchorRowId, anchorColKey, rowSpan, colSpan));
        } else {
            existing.update(rowSpan, colSpan);
        }
    }

    /** 병합 해제. 없으면 아무 일도 안 한다(멱등). */
    public void unmerge(Long anchorRowId, String anchorColKey) {
        mergeRepository.findByAnchorRowIdAndAnchorColKey(anchorRowId, anchorColKey)
                .ifPresent(mergeRepository::delete);
    }

    /** 페이지의 병합을 앵커 행 id별로 모은다. 병합 있는 앵커만 담기므로 대개 비어 있다. */
    public Map<Long, Map<String, MergeSpec>> mergesByRow(List<Long> rowIds) {
        if (rowIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<String, MergeSpec>> result = new HashMap<>();
        for (DatasetMerge merge : mergeRepository.findByAnchorRowIdIn(rowIds)) {
            result.computeIfAbsent(merge.getAnchorRowId(), k -> new HashMap<>())
                    .put(merge.getAnchorColKey(), new MergeSpec(merge.getRowSpan(), merge.getColSpan()));
        }
        return result;
    }

    /**
     * 열이 지워졌을 때 그 열에 걸친 병합을 해제한다 — 영역이 온전할 수 없어졌다(#829 O3 "깨지면 해제").
     * 앵커가 그 열이거나, 다른 열 앵커의 span이 그 열을 덮는 경우 모두 지운다.
     * 서식·수식의 {@code invalidateColumn}과 같은 자리에서 부른다.
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

    private int indexOf(List<DatasetColumn> columns, String key) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }
}
