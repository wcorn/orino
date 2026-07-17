package ds.project.orino.planner.dataset.service;

import ds.project.orino.domain.planner.dataset.entity.DatasetCellStyle;
import ds.project.orino.domain.planner.dataset.repository.DatasetCellStyleRepository;
import ds.project.orino.planner.dataset.dto.CellStyle;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 셀 서식(배경색·정렬) 저장·조회. 값은 {@code dataset_row.cells}에 그대로 두고 서식만
 * {@code dataset_cell_style}에 담는다 — 수식({@link DatasetFormulaService})과 같은 sparse 전략이라
 * 읽기 경로가 바뀌지 않는다.
 */
@Service
public class DatasetCellStyleService {

    private final DatasetCellStyleRepository styleRepository;

    public DatasetCellStyleService(DatasetCellStyleRepository styleRepository) {
        this.styleRepository = styleRepository;
    }

    /**
     * 셀 서식을 통째로 교체한다(부분 갱신 아님). 둘 다 null이면 서식이 없어진 것이므로 행을 지운다 —
     * 빈 행을 남기면 sparse가 깨진다.
     */
    public void setStyle(Long datasetId, Long rowId, String colKey, String bg, String align) {
        DatasetCellStyle existing = styleRepository.findByRowIdAndColKey(rowId, colKey).orElse(null);
        if (bg == null && align == null) {
            if (existing != null) {
                styleRepository.delete(existing);
            }
            return;
        }
        if (existing == null) {
            styleRepository.save(new DatasetCellStyle(datasetId, rowId, colKey, bg, align));
        } else {
            existing.update(bg, align);
        }
    }

    /** 페이지의 서식을 행 id별로 모은다. 서식 있는 셀만 담기므로 대개 비어 있다. */
    public Map<Long, Map<String, CellStyle>> stylesByRow(List<Long> rowIds) {
        if (rowIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<String, CellStyle>> result = new HashMap<>();
        for (DatasetCellStyle style : styleRepository.findByRowIdIn(rowIds)) {
            result.computeIfAbsent(style.getRowId(), k -> new HashMap<>())
                    .put(style.getColKey(), new CellStyle(style.getBg(), style.getAlign()));
        }
        return result;
    }

    /** 열 삭제 시 그 열의 서식을 정리한다. 담길 셀이 없어졌으니 남길 이유가 없다. */
    public void invalidateColumn(Long datasetId, String colKey) {
        styleRepository.deleteByDatasetIdAndColKey(datasetId, colKey);
    }
}
