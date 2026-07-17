package ds.project.orino.planner.dataset.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.dataset.entity.Dataset;
import ds.project.orino.domain.planner.dataset.entity.DatasetRow;
import ds.project.orino.domain.planner.dataset.repository.DatasetRepository;
import ds.project.orino.domain.planner.dataset.repository.DatasetRowRepository;
import ds.project.orino.planner.dataset.dto.AddColumnRequest;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    /** 열 개수 상한. 행이 0이면 MAX_CELLS가 열 증가를 못 막으므로 별도로 둔다. */
    static final int MAX_COLUMNS = 100;

    /** 열 key 규칙: {@code c<발급번호>}. 번호는 dataset의 next_column_seq에서 받는다. */
    private static final Pattern COLUMN_KEY = Pattern.compile("c(\\d+)");

    private final DatasetRepository datasetRepository;
    private final DatasetRowRepository rowRepository;

    public DatasetService(DatasetRepository datasetRepository, DatasetRowRepository rowRepository) {
        this.datasetRepository = datasetRepository;
        this.rowRepository = rowRepository;
    }

    @Transactional
    public DatasetResponse create(Long memberId, CreateDatasetRequest request) {
        if (request.columns().size() > MAX_COLUMNS) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        Dataset dataset = datasetRepository.save(new Dataset(
                memberId, serialize(request.columns()), nextSeqFor(request.columns())));
        return DatasetResponse.of(dataset, request.columns());
    }

    /**
     * 생성 시 카운터 시작값. 클라이언트가 보낸 key 중 {@code c<N>} 규칙에 맞는 최대 N보다 1 크게 잡아,
     * 이후 발급될 key가 기존 열과 겹치지 않게 한다.
     */
    private int nextSeqFor(List<DatasetColumn> columns) {
        int max = -1;
        for (DatasetColumn column : columns) {
            Matcher matcher = COLUMN_KEY.matcher(column.key());
            if (matcher.matches()) {
                max = Math.max(max, Integer.parseInt(matcher.group(1)));
            }
        }
        return max + 1;
    }

    /**
     * 열 추가. columns_json에 append만 하고 행은 건드리지 않는다 — 기존 행엔 새 key가 없고,
     * 읽을 때 투영이 빈 값으로 채운다. 그래서 행 수와 무관하게 O(1)이다.
     *
     * <p>key는 dataset의 카운터에서 발급받는다. 지워진 열의 key를 다시 쓰면 행에 남은 옛 값이
     * 새 열에 되살아나므로, 현재 열의 최대 번호가 아니라 발급 이력을 기준으로 삼는다.
     */
    @Transactional
    public DatasetResponse addColumn(Long memberId, Long datasetId, AddColumnRequest request) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        if (columns.size() + 1 > MAX_COLUMNS) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        if ((long) dataset.getRowCount() * (columns.size() + 1) > MAX_CELLS) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        List<DatasetColumn> updated = new java.util.ArrayList<>(columns);
        updated.add(new DatasetColumn("c" + dataset.issueColumnSeq(), request.label()));
        dataset.updateColumns(serialize(updated));
        return DatasetResponse.of(dataset, updated);
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
