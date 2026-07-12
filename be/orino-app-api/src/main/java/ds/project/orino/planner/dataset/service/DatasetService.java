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
import ds.project.orino.planner.dataset.dto.RowView;
import ds.project.orino.planner.dataset.dto.RowsResponse;
import ds.project.orino.planner.dataset.dto.UpdateRowRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class DatasetService {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<DatasetColumn>> COLUMNS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> CELLS_TYPE = new TypeReference<>() {
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

    /** 행 벌크 추가(끝에 append). Import 청크에 사용. */
    @Transactional
    public DatasetResponse bulkAppend(Long memberId, Long datasetId, BulkRowsRequest request) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<List<String>> rows = request.rows();
        if (rows.size() > MAX_BULK_ROWS) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        int colCount = parseColumns(dataset.getColumns()).size();
        long totalCells = (long) (dataset.getRowCount() + rows.size()) * colCount;
        if (totalCells > MAX_CELLS) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        int start = dataset.getRowCount();
        List<DatasetRow> entities = new java.util.ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            entities.add(new DatasetRow(datasetId, start + i, serialize(rows.get(i))));
        }
        rowRepository.saveAll(entities);
        dataset.setRowCount(start + rows.size());
        return DatasetResponse.of(dataset, parseColumns(dataset.getColumns()));
    }

    public RowsResponse getRows(Long memberId, Long datasetId, Integer offset, Integer limit) {
        getOwned(memberId, datasetId);
        int off = offset == null ? 0 : Math.max(0, offset);
        int lim = clampLimit(limit);
        List<RowView> rows = rowRepository
                .findByDatasetIdAndRowIndexGreaterThanEqualAndRowIndexLessThanOrderByRowIndexAsc(
                        datasetId, off, off + lim)
                .stream()
                .map(r -> new RowView(r.getRowIndex(), parseCells(r.getCells())))
                .toList();
        return new RowsResponse(rows, off, lim);
    }

    @Transactional
    public RowView updateRow(Long memberId, Long datasetId, int rowIndex, UpdateRowRequest request) {
        getOwned(memberId, datasetId);
        DatasetRow row = rowRepository.findByDatasetIdAndRowIndex(datasetId, rowIndex)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        row.updateCells(serialize(request.cells()));
        return new RowView(rowIndex, request.cells());
    }

    @Transactional
    public InsertRowResponse insertRow(Long memberId, Long datasetId, InsertRowRequest request) {
        Dataset dataset = getOwned(memberId, datasetId);
        int rowCount = dataset.getRowCount();
        int at = request.atIndex() == null
                ? rowCount
                : Math.max(0, Math.min(request.atIndex(), rowCount));
        long totalCells = (long) (rowCount + 1) * parseColumns(dataset.getColumns()).size();
        if (totalCells > MAX_CELLS) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        if (at < rowCount) {
            rowRepository.shiftUp(datasetId, at); // 뒤 행을 밀어 자리 확보(flush)
        }
        rowRepository.save(new DatasetRow(datasetId, at, serialize(request.cells())));
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

    private List<String> parseCells(String json) {
        try {
            return MAPPER.readValue(json, CELLS_TYPE);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }
}
