package ds.project.orino.planner.dataset.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.dataset.entity.Dataset;
import ds.project.orino.domain.planner.dataset.entity.DatasetRow;
import ds.project.orino.domain.planner.dataset.repository.DatasetRepository;
import ds.project.orino.domain.planner.dataset.repository.DatasetRowRepository;
import ds.project.orino.planner.dataset.dto.BulkRowsRequest;
import ds.project.orino.planner.dataset.dto.CreateDatasetRequest;
import ds.project.orino.planner.dataset.dto.DatasetColumn;
import ds.project.orino.planner.dataset.dto.DatasetResponse;
import ds.project.orino.planner.dataset.dto.InsertRowRequest;
import ds.project.orino.planner.dataset.dto.InsertRowResponse;
import ds.project.orino.planner.dataset.dto.RenameColumnRequest;
import ds.project.orino.planner.dataset.dto.RowView;
import ds.project.orino.planner.dataset.dto.RowsResponse;
import ds.project.orino.planner.dataset.dto.UpdateRowRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 데이터셋 CRUD.
 *
 * <p>저장소의 {@code dataset_row.cells}는 열 key 기반 맵({@code {"c0":"a","c1":"b"}})이지만,
 * API 계약({@link RowView#cells})은 위치 배열이다. 경계에서 {@code columns_json} 순서로
 * 투영·역투영한다({@link #toCellList}/{@link #toCellMap}). 덕분에 열 추가·삭제·순서변경이
 * columns_json 갱신만으로 끝나고(행 무손상), 클라이언트는 여전히 직사각형 표만 다루면 된다.
 */
@Service
@Transactional(readOnly = true)
public class DatasetService {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<DatasetColumn>> COLUMNS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> CELLS_TYPE = new TypeReference<>() {
    };

    static final int DEFAULT_PAGE_SIZE = 100;
    static final int MAX_PAGE_SIZE = 500;
    static final int MAX_BULK_ROWS = 2000;
    /** 데이터셋 전체 셀 상한(폭주 방지). */
    static final long MAX_CELLS = 1_000_000L;

    private final DatasetRepository datasetRepository;
    private final DatasetRowRepository rowRepository;

    public DatasetService(DatasetRepository datasetRepository, DatasetRowRepository rowRepository) {
        this.datasetRepository = datasetRepository;
        this.rowRepository = rowRepository;
    }

    @Transactional
    public DatasetResponse create(Long memberId, CreateDatasetRequest request) {
        Dataset dataset = datasetRepository.save(
                new Dataset(memberId, serialize(request.columns())));
        return DatasetResponse.of(dataset, request.columns());
    }

    public DatasetResponse getMeta(Long memberId, Long datasetId) {
        Dataset dataset = getOwned(memberId, datasetId);
        return DatasetResponse.of(dataset, parseColumns(dataset.getColumns()));
    }

    /**
     * 열 이름(label) 변경. key는 cells 맵의 주소라 바꾸지 않으며(바꾸면 기존 값과 연결이 끊긴다),
     * columns_json만 갱신하므로 행 데이터는 건드리지 않는다.
     */
    @Transactional
    public DatasetResponse renameColumn(Long memberId, Long datasetId, String key,
                                        RenameColumnRequest request) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        int at = -1;
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).key().equals(key)) {
                at = i;
                break;
            }
        }
        if (at < 0) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        List<DatasetColumn> updated = new java.util.ArrayList<>(columns);
        updated.set(at, new DatasetColumn(key, request.label()));
        dataset.updateColumns(serialize(updated));
        return DatasetResponse.of(dataset, updated);
    }

    /** 행 벌크 추가(끝에 append). Import 청크에 사용. */
    @Transactional
    public DatasetResponse bulkAppend(Long memberId, Long datasetId, BulkRowsRequest request) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<List<String>> rows = request.rows();
        if (rows.size() > MAX_BULK_ROWS) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        long totalCells = (long) (dataset.getRowCount() + rows.size()) * columns.size();
        if (totalCells > MAX_CELLS) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        int start = dataset.getRowCount();
        List<DatasetRow> entities = new java.util.ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            entities.add(new DatasetRow(datasetId, start + i, toCellMap(rows.get(i), columns)));
        }
        rowRepository.saveAll(entities);
        dataset.setRowCount(start + rows.size());
        return DatasetResponse.of(dataset, columns);
    }

    public RowsResponse getRows(Long memberId, Long datasetId, Integer offset, Integer limit) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        int off = offset == null ? 0 : Math.max(0, offset);
        int lim = clampLimit(limit);
        List<RowView> rows = rowRepository
                .findByDatasetIdAndRowIndexGreaterThanEqualAndRowIndexLessThanOrderByRowIndexAsc(
                        datasetId, off, off + lim)
                .stream()
                .map(r -> new RowView(r.getRowIndex(), toCellList(r.getCells(), columns)))
                .toList();
        return new RowsResponse(rows, off, lim);
    }

    @Transactional
    public RowView updateRow(Long memberId, Long datasetId, int rowIndex, UpdateRowRequest request) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        DatasetRow row = rowRepository.findByDatasetIdAndRowIndex(datasetId, rowIndex)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        row.updateCells(toCellMap(request.cells(), columns));
        // 저장된 값을 그대로 되돌려준다(열 수에 맞춰 잘리거나 채워진 결과).
        return new RowView(rowIndex, toCellList(row.getCells(), columns));
    }

    @Transactional
    public InsertRowResponse insertRow(Long memberId, Long datasetId, InsertRowRequest request) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        int rowCount = dataset.getRowCount();
        int at = request.atIndex() == null
                ? rowCount
                : Math.max(0, Math.min(request.atIndex(), rowCount));
        long totalCells = (long) (rowCount + 1) * columns.size();
        if (totalCells > MAX_CELLS) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        if (at < rowCount) {
            rowRepository.shiftUp(datasetId, at); // 뒤 행을 밀어 자리 확보(flush)
        }
        rowRepository.save(new DatasetRow(datasetId, at, toCellMap(request.cells(), columns)));
        dataset.setRowCount(rowCount + 1);
        return new InsertRowResponse(at);
    }

    @Transactional
    public void deleteRow(Long memberId, Long datasetId, int rowIndex) {
        Dataset dataset = getOwned(memberId, datasetId);
        DatasetRow row = rowRepository.findByDatasetIdAndRowIndex(datasetId, rowIndex)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        rowRepository.delete(row);
        rowRepository.shiftDown(datasetId, rowIndex); // 삭제 flush 후 뒤 행 당김
        dataset.setRowCount(dataset.getRowCount() - 1);
    }

    /** 데이터셋 삭제. dataset_row는 FK ON DELETE CASCADE로 함께 지워진다. */
    @Transactional
    public void delete(Long memberId, Long datasetId) {
        Dataset dataset = getOwned(memberId, datasetId);
        datasetRepository.delete(dataset);
    }

    private Dataset getOwned(Long memberId, Long datasetId) {
        return datasetRepository.findByIdAndMemberId(datasetId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
    }

    private String serialize(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, e);
        }
    }

    private List<DatasetColumn> parseColumns(String json) {
        try {
            return MAPPER.readValue(json, COLUMNS_TYPE);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    private Map<String, String> parseCells(String json) {
        try {
            return MAPPER.readValue(json, CELLS_TYPE);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    /** 저장소 맵 → API 위치 배열. 열 순서대로 뽑고, 값이 없는 열은 빈 문자열로 채운다. */
    private List<String> toCellList(String cellsJson, List<DatasetColumn> columns) {
        Map<String, String> cells = parseCells(cellsJson);
        List<String> list = new java.util.ArrayList<>(columns.size());
        for (DatasetColumn column : columns) {
            list.add(cells.getOrDefault(column.key(), ""));
        }
        return list;
    }

    /**
     * API 위치 배열 → 저장소 맵. 열 순서로 짝지으며, 열 수를 넘는 값은 담을 key가 없어 버린다.
     * 열 수보다 짧으면 나머지 열은 빈 문자열로 채워 직사각형을 유지한다.
     */
    private String toCellMap(List<String> cells, List<DatasetColumn> columns) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            String value = i < cells.size() ? cells.get(i) : null;
            map.put(columns.get(i).key(), value == null ? "" : value);
        }
        return serialize(map);
    }
}
