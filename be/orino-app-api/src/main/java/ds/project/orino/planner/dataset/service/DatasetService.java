package ds.project.orino.planner.dataset.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.dataset.entity.Dataset;
import ds.project.orino.domain.planner.dataset.entity.DatasetRow;
import ds.project.orino.domain.planner.dataset.repository.DatasetRepository;
import ds.project.orino.domain.planner.dataset.repository.DatasetRowRepository;
import ds.project.orino.planner.dataset.dto.AddColumnRequest;
import ds.project.orino.planner.dataset.dto.BulkRowsRequest;
import ds.project.orino.planner.dataset.dto.CellStyle;
import ds.project.orino.planner.dataset.dto.CreateDatasetRequest;
import ds.project.orino.planner.dataset.dto.DatasetColumn;
import ds.project.orino.planner.dataset.dto.DatasetResponse;
import ds.project.orino.planner.dataset.dto.FillCellsRequest;
import ds.project.orino.planner.dataset.dto.InsertRowRequest;
import ds.project.orino.planner.dataset.dto.InsertRowResponse;
import ds.project.orino.planner.dataset.dto.MergesResponse;
import ds.project.orino.planner.dataset.dto.RenameColumnRequest;
import ds.project.orino.planner.dataset.dto.ReorderColumnsRequest;
import ds.project.orino.planner.dataset.dto.ResizeColumnRequest;
import ds.project.orino.planner.dataset.dto.BulkCellStyleRequest;
import ds.project.orino.planner.dataset.dto.RowView;
import ds.project.orino.planner.dataset.dto.RowsResponse;
import ds.project.orino.planner.dataset.dto.SetCellMergeRequest;
import ds.project.orino.planner.dataset.dto.SetCellStyleRequest;
import ds.project.orino.planner.dataset.dto.SetColumnAlignRequest;
import ds.project.orino.planner.dataset.dto.UpdateRowRequest;
import ds.project.orino.planner.dataset.dto.UpdateRowResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    /** 기본 열 이름 접두사. 뒤에 붙는 번호는 비어 있는 것을 찾아 쓴다. */
    private static final String DEFAULT_LABEL_PREFIX = "열 ";

    private final DatasetRepository datasetRepository;
    private final DatasetRowRepository rowRepository;
    private final DatasetFormulaService formulaService;
    private final DatasetCellStyleService styleService;
    private final DatasetMergeService mergeService;

    public DatasetService(DatasetRepository datasetRepository, DatasetRowRepository rowRepository,
                          DatasetFormulaService formulaService,
                          DatasetCellStyleService styleService,
                          DatasetMergeService mergeService) {
        this.datasetRepository = datasetRepository;
        this.rowRepository = rowRepository;
        this.formulaService = formulaService;
        this.styleService = styleService;
        this.mergeService = mergeService;
    }

    /**
     * 데이터셋 생성. 열 label은 수식이 참조를 유일하게 지목할 수 있어야 하므로 중복을 허용하지 않는다.
     *
     * <p>다만 여기서 오는 label은 대개 <b>기계가 만든 것</b>(Import한 스프레드시트 헤더, 기본 이름)이라
     * 거부하지 않고 {@link #deduplicateLabels 자동 구분}한다. 중복 헤더를 가진 엑셀 파일은 흔한데,
     * 400으로 막으면 Import 자체가 불가능해진다. 엑셀·구글시트도 같은 방식으로 붙여 구분한다.
     */
    @Transactional
    public DatasetResponse create(Long memberId, CreateDatasetRequest request) {
        if (request.columns().size() > MAX_COLUMNS) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        List<DatasetColumn> columns = deduplicateLabels(request.columns());
        Dataset dataset = datasetRepository.save(new Dataset(
                memberId, serialize(columns), nextSeqFor(columns)));
        return metaResponse(dataset, columns);
    }

    /**
     * 중복 label에 {@code (2)}, {@code (3)}… 을 붙여 유일하게 만든다. 순서는 보존한다.
     * 붙인 이름이 또 다른 label과 겹치면 번호를 계속 올린다.
     */
    private List<DatasetColumn> deduplicateLabels(List<DatasetColumn> columns) {
        java.util.Set<String> taken = new java.util.HashSet<>();
        List<DatasetColumn> result = new java.util.ArrayList<>(columns.size());
        for (DatasetColumn column : columns) {
            String label = column.label();
            for (int n = 2; !taken.add(label); n++) {
                label = column.label() + " (" + n + ")";
            }
            result.add(column.withLabel(label));
        }
        return result;
    }

    /** 이미 쓰이고 있는 label이면 거부한다. {@code selfKey}는 자기 자신(rename 시 무변경 허용). */
    private void requireLabelFree(List<DatasetColumn> columns, String label, String selfKey) {
        boolean taken = columns.stream()
                .anyMatch(c -> !c.key().equals(selfKey) && c.label().equals(label));
        if (taken) {
            throw new CustomException(ErrorCode.DUPLICATE_COLUMN_LABEL);
        }
    }

    /** 아직 안 쓰인 {@code 열 N}을 찾아 준다. */
    private String generateLabel(List<DatasetColumn> columns) {
        java.util.Set<String> taken = columns.stream()
                .map(DatasetColumn::label)
                .collect(java.util.stream.Collectors.toSet());
        for (int n = columns.size() + 1; ; n++) {
            String candidate = DEFAULT_LABEL_PREFIX + n;
            if (taken.add(candidate)) {
                return candidate;
            }
        }
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

        // label을 안 주면 서버가 유일한 기본 이름을 발급한다(key 발급과 같은 방식).
        // 클라이언트가 이름을 지으면 열 개수 기반 규칙이 삭제 후 중복을 만든다.
        String label = request.label() == null || request.label().isBlank()
                ? generateLabel(columns)
                : request.label();
        requireLabelFree(columns, label, null);

        List<DatasetColumn> updated = new java.util.ArrayList<>(columns);
        DatasetColumn added = new DatasetColumn("c" + dataset.issueColumnSeq(), label);
        // atIndex를 주면 그 위치에 끼우고(범위 밖은 클램프), 없으면 끝에 붙인다.
        int at = request.atIndex() == null
                ? updated.size()
                : Math.max(0, Math.min(request.atIndex(), updated.size()));
        // 삽입 위치를 가로 span이 가로지르는 병합은 해제한다(삽입 전 순서로 판정). 끝에 추가면 no-op.
        mergeService.invalidateOnColumnInsert(datasetId, at, columns);
        updated.add(at, added);
        dataset.updateColumns(serialize(updated));
        return metaResponse(dataset, updated);
    }

    public DatasetResponse getMeta(Long memberId, Long datasetId) {
        Dataset dataset = getOwned(memberId, datasetId);
        return metaResponse(dataset, parseColumns(dataset.getColumns()));
    }

    /**
     * 열 삭제. columns_json에서만 빼고 행은 건드리지 않아 O(1)이다.
     *
     * <p>행에 남은 값은 투영이 columns 기준으로만 읽으므로 API로 드러나지 않고,
     * 그 행을 다음에 수정할 때 {@link #toCellMap}이 맵을 새로 만들며 함께 사라진다.
     * 즉 즉시 물리 삭제는 아니고 지연 정리다. 재발급되지 않는 key({@link Dataset#issueColumnSeq})
     * 덕분에 남은 값이 새 열에 되살아날 일은 없다.
     *
     * <p>마지막 열은 지울 수 없다. 열이 0개면 행이 값을 담을 자리가 없어진다.
     */
    @Transactional
    public DatasetResponse deleteColumn(Long memberId, Long datasetId, String key) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        if (columns.stream().noneMatch(c -> c.key().equals(key))) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (columns.size() <= 1) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        List<DatasetColumn> updated = columns.stream()
                .filter(c -> !c.key().equals(key))
                .toList();
        dataset.updateColumns(serialize(updated));
        // 그 열의 수식은 담길 셀이 없어졌으니 지우고, 그 열을 참조하던 수식은 #REF!가 된다.
        formulaService.invalidateColumn(datasetId, key);
        // 그 열의 서식도 담길 셀이 없어졌으니 지운다(col_key는 FK가 아니라 cascade가 없다).
        styleService.invalidateColumn(datasetId, key);
        // 그 열에 걸친 병합은 영역이 온전할 수 없어 해제한다(삭제 전 열 순서로 덮는 열을 계산해야 한다).
        mergeService.invalidateColumn(datasetId, key, columns);
        return metaResponse(dataset, updated);
    }

    /**
     * 열 순서 변경. columns_json 배열 순서만 바꾸고 행은 건드리지 않아 O(1)이다 —
     * cells가 key 맵이라 값이 위치에 묶여 있지 않고, 읽을 때 투영이 새 순서를 따라간다.
     *
     * <p>요청은 전체 순서이며, 현재 열 집합과 정확히 같아야 한다. 열 추가·삭제는 이 API의
     * 일이 아니므로 집합이 다르면 거부한다.
     */
    @Transactional
    public DatasetResponse reorderColumns(Long memberId, Long datasetId,
                                          ReorderColumnsRequest request) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());

        Map<String, DatasetColumn> byKey = new LinkedHashMap<>();
        for (DatasetColumn column : columns) {
            byKey.put(column.key(), column);
        }
        // 중복 key가 오면 size 비교만으론 못 걸러내므로 집합으로 견준다.
        if (request.keys().size() != columns.size()
                || !new java.util.HashSet<>(request.keys()).equals(byKey.keySet())) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        List<DatasetColumn> updated = request.keys().stream().map(byKey::get).toList();
        dataset.updateColumns(serialize(updated));
        // 순서가 바뀌면 병합 영역이 불연속이 될 수 있어 그 dataset의 병합을 모두 해제한다(#829 O3, v1 보수적).
        mergeService.invalidateAllOnReorder(datasetId);
        return metaResponse(dataset, updated);
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
        int at = indexOfColumn(columns, key);
        if (at < 0) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        // 사람이 직접 지정한 이름이므로 자동 구분하지 않고 충돌을 알린다.
        requireLabelFree(columns, request.label(), key);

        List<DatasetColumn> updated = new java.util.ArrayList<>(columns);
        // withLabel — 이름만 바꾸고 width는 보존한다. 새로 만들면 설정한 너비가 날아간다.
        updated.set(at, columns.get(at).withLabel(request.label()));
        dataset.updateColumns(serialize(updated));
        return metaResponse(dataset, updated);
    }

    /**
     * 열 너비 변경. columns_json의 해당 열만 고치고 행은 건드리지 않아 O(1)이다.
     * 너비는 열 단위 표시 속성이라 셀 값·수식과 무관하다.
     */
    @Transactional
    public DatasetResponse resizeColumn(Long memberId, Long datasetId, String key,
                                        ResizeColumnRequest request) {
        return updateWidth(memberId, datasetId, key, request.width());
    }

    /**
     * 열 너비를 지워 기본 폭으로 되돌린다. 너비 미설정(null)이 곧 기본 폭이므로
     * "되돌리기"는 별도 상태가 아니라 값의 부재로 표현된다.
     */
    @Transactional
    public DatasetResponse resetColumnWidth(Long memberId, Long datasetId, String key) {
        return updateWidth(memberId, datasetId, key, null);
    }

    private DatasetResponse updateWidth(Long memberId, Long datasetId, String key, Integer width) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        int at = indexOfColumn(columns, key);
        if (at < 0) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        List<DatasetColumn> updated = new java.util.ArrayList<>(columns);
        updated.set(at, columns.get(at).withWidth(width));
        dataset.updateColumns(serialize(updated));
        return metaResponse(dataset, updated);
    }

    /**
     * 열 기본 정렬 변경. columns_json의 해당 열만 고치고 행은 건드리지 않아 O(1)이다.
     * 정렬은 열 단위 표시 속성이라 셀 값·수식과 무관하다(셀 단위 정렬이 있으면 그쪽이 덮는다, #828 D2).
     */
    @Transactional
    public DatasetResponse setColumnAlign(Long memberId, Long datasetId, String key,
                                          SetColumnAlignRequest request) {
        return updateAlign(memberId, datasetId, key, request.align());
    }

    /**
     * 열 기본 정렬을 지워 기본 정렬(left)로 되돌린다. 정렬 미설정(null)이 곧 기본이므로
     * "되돌리기"는 별도 상태가 아니라 값의 부재로 표현된다(너비와 같은 규칙).
     */
    @Transactional
    public DatasetResponse resetColumnAlign(Long memberId, Long datasetId, String key) {
        return updateAlign(memberId, datasetId, key, null);
    }

    private DatasetResponse updateAlign(Long memberId, Long datasetId, String key, String align) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        int at = indexOfColumn(columns, key);
        if (at < 0) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        List<DatasetColumn> updated = new java.util.ArrayList<>(columns);
        updated.set(at, columns.get(at).withAlign(align));
        dataset.updateColumns(serialize(updated));
        return metaResponse(dataset, updated);
    }

    /**
     * 열 푸터 요약 함수를 설정/해제한다(멱등 교체). {@code summary}가 null이면 그 열 요약을 지운다.
     * <b>값(집계)은 여기서 계산하지 않는다</b> — 함수만 columns_json에 담고, 계산된 값은 응답의
     * {@code summaries} 맵으로 따로 온다(#907 표면; 값 채우기는 #908).
     */
    @Transactional
    public DatasetResponse setColumnSummary(Long memberId, Long datasetId, String key,
                                            String summary) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        int at = indexOfColumn(columns, key);
        if (at < 0) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        List<DatasetColumn> updated = new java.util.ArrayList<>(columns);
        updated.set(at, columns.get(at).withSummary(summary));
        dataset.updateColumns(serialize(updated));
        return metaResponse(dataset, updated);
    }

    /**
     * 열 숫자 서식을 설정/해제한다(멱등 교체). null이면 지운다. <b>표시 전용</b> — 값·수식은 안
     * 건드리고 토큰만 columns_json에 담는다. 포맷은 FE가 화면에만 적용한다.
     */
    @Transactional
    public DatasetResponse setColumnFormat(Long memberId, Long datasetId, String key,
                                           String format) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        int at = indexOfColumn(columns, key);
        if (at < 0) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        List<DatasetColumn> updated = new java.util.ArrayList<>(columns);
        updated.set(at, columns.get(at).withFormat(format));
        dataset.updateColumns(serialize(updated));
        return metaResponse(dataset, updated);
    }

    /** 허용값 목록 상한. 드롭다운 편의용이라 과도한 목록은 막는다. */
    static final int MAX_OPTIONS = 200;

    /**
     * 열 허용값 목록(enum)을 설정/해제한다(멱등 교체). 빈 목록·null이면 해제(자유 입력). 값은
     * <b>강제하지 않고</b>(느슨) 목록만 정규화해 저장한다 — 공백 정리·중복 제거·순서 보존.
     */
    @Transactional
    public DatasetResponse setColumnOptions(Long memberId, Long datasetId, String key,
                                            List<String> options) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        int at = indexOfColumn(columns, key);
        if (at < 0) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        List<DatasetColumn> updated = new java.util.ArrayList<>(columns);
        updated.set(at, columns.get(at).withOptions(normalizeOptions(options)));
        dataset.updateColumns(serialize(updated));
        return metaResponse(dataset, updated);
    }

    /** 공백 정리·빈값/중복 제거(순서 보존). 비면 null(해제). 상한을 넘으면 거부. */
    private List<String> normalizeOptions(List<String> options) {
        if (options == null) {
            return null;
        }
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (String o : options) {
            if (o == null) {
                continue;
            }
            String trimmed = o.trim();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        if (set.isEmpty()) {
            return null;
        }
        if (set.size() > MAX_OPTIONS) {
            throw new CustomException(ErrorCode.INVALID_REQUEST,
                    "허용값은 " + MAX_OPTIONS + "개를 넘을 수 없습니다");
        }
        return List.copyOf(set);
    }

    /**
     * 표 이름을 설정/해제한다(멱등). 빈 값(공백·null)이면 무명으로 되돌린다. 유일성은 강제하지
     * 않는다 — BE는 표가 어느 노트에 있는지 모르므로, 이름 해석은 #915에서 노트 단위(FE)로 한다.
     */
    @Transactional
    public DatasetResponse setName(Long memberId, Long datasetId, String name) {
        Dataset dataset = getOwned(memberId, datasetId);
        String trimmed = name == null || name.isBlank() ? null : name.trim();
        dataset.updateName(trimmed);
        return metaResponse(dataset, parseColumns(dataset.getColumns()));
    }

    /** 메타 응답을 조립한다 — 요약 값(집계)을 계산해 함께 싣는다(#908). */
    private DatasetResponse metaResponse(Dataset dataset, List<DatasetColumn> columns) {
        return DatasetResponse.of(dataset, columns,
                formulaService.computeSummaries(dataset.getId(), columns));
    }

    private int indexOfColumn(List<DatasetColumn> columns, String key) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 한 셀의 수식을 그 열 전체에 채운다 — 계산 열 만들기.
     * 셀 단위 수식(D5)이라 계산 열을 만들려면 같은 수식을 모든 행에 두어야 한다.
     */
    @Transactional
    public DatasetResponse fillDownColumn(Long memberId, Long datasetId, String colKey,
                                          int fromRowIndex) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        if (columns.stream().noneMatch(c -> c.key().equals(colKey))) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        DatasetRow source = rowRepository.findByDatasetIdAndRowIndex(datasetId, fromRowIndex)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        formulaService.fillDownColumn(datasetId, colKey, source, columns);
        return metaResponse(dataset, columns);
    }

    /**
     * 채우기 핸들(세로 드래그) — 소스 블록을 대상 행들에 타일링해 채운다. 대상은 소스와 겹치지
     * 않고 바로 위/아래로 인접해야 한다. 값이 바뀐(대상 + 전파) 행들을 돌려준다 — FE가 재조회
     * 없이 반영한다.
     */
    @Transactional
    public List<RowView> fillCells(Long memberId, Long datasetId, FillCellsRequest request) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        for (String col : request.cols()) {
            if (indexOfColumn(columns, col) < 0) {
                throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
            }
        }
        int rowCount = dataset.getRowCount();
        int srcR0 = request.srcR0();
        int srcR1 = request.srcR1();
        int dstR0 = request.dstR0();
        int dstR1 = request.dstR1();
        // 범위가 표 안에 있고, 소스·대상 각각 정상 순서인지.
        if (srcR0 > srcR1 || dstR0 > dstR1 || srcR1 >= rowCount || dstR1 >= rowCount) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        // 대상은 소스와 겹치지 않고 바로 아래(dstR0 = srcR1+1) 또는 바로 위(dstR1 = srcR0-1)여야 한다.
        boolean below = dstR0 == srcR1 + 1;
        boolean above = dstR1 == srcR0 - 1;
        if (!below && !above) {
            throw new CustomException(ErrorCode.INVALID_REQUEST,
                    "채우기 대상은 소스 바로 위/아래로 인접해야 합니다");
        }

        List<DatasetRow> srcRows = rowRepository
                .findByDatasetIdAndRowIndexGreaterThanEqualAndRowIndexLessThanOrderByRowIndexAsc(
                        datasetId, srcR0, srcR1 + 1);
        List<DatasetRow> dstRows = rowRepository
                .findByDatasetIdAndRowIndexGreaterThanEqualAndRowIndexLessThanOrderByRowIndexAsc(
                        datasetId, dstR0, dstR1 + 1);
        if (srcRows.isEmpty() || dstRows.isEmpty()) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        Set<Long> affected = formulaService.fillRange(
                datasetId, request.cols(), srcR0, srcRows, dstRows, columns);
        return buildRowViews(datasetId, affected, columns);
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
        return metaResponse(dataset, columns);
    }

    public RowsResponse getRows(Long memberId, Long datasetId, Integer offset, Integer limit) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        int off = offset == null ? 0 : Math.max(0, offset);
        int lim = clampLimit(limit);
        List<DatasetRow> found = rowRepository
                .findByDatasetIdAndRowIndexGreaterThanEqualAndRowIndexLessThanOrderByRowIndexAsc(
                        datasetId, off, off + lim);
        // 페이지의 수식·서식을 한 번에 가져온다. 있는 셀만 담기므로 대개 비어 있다.
        // 병합은 페이지가 아니라 dataset 단위(GET /merges)라 여기서 안 싣는다.
        List<Long> rowIds = found.stream().map(DatasetRow::getId).toList();
        Map<Long, Map<String, String>> formulas =
                formulaService.displayFormulas(datasetId, rowIds, columns);
        Map<Long, Map<String, CellStyle>> styles = styleService.stylesByRow(rowIds);
        List<RowView> rows = found.stream()
                .map(r -> new RowView(r.getId(), r.getRowIndex(), toCellList(r.getCells(), columns),
                        formulas.getOrDefault(r.getId(), Map.of()),
                        styles.getOrDefault(r.getId(), Map.of())))
                .toList();
        return new RowsResponse(rows, off, lim);
    }

    /**
     * 셀/행 편집. {@code =}로 시작하는 값은 수식으로 보고 파싱·평가해 <b>계산된 값</b>을 셀에 담는다
     * (수식 원본은 {@code dataset_formula}에 따로). 엑셀과 같은 진입이라 별도 API가 없다.
     */
    @Transactional
    public UpdateRowResponse updateRow(Long memberId, Long datasetId, int rowIndex,
                                       UpdateRowRequest request) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        DatasetRow row = rowRepository.findByDatasetIdAndRowIndex(datasetId, rowIndex)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        // 리터럴을 먼저 채운다 — 같은 행 참조가 이번 요청에서 바뀐 값을 봐야 한다.
        // (cells=["5","={c0}*2"] 면 c1은 옛 c0이 아니라 5를 써야 한다)
        Map<String, String> cells = DatasetCells.toMap(request.cells(), columns);
        Map<String, String> formulas = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : cells.entrySet()) {
            if (DatasetFormulaService.isFormula(entry.getValue())) {
                formulas.put(entry.getKey(), entry.getValue());
            } else {
                formulaService.removeIfAny(row.getId(), entry.getKey());
            }
        }
        // 수식은 열 순서로 계산한다. 앞쪽 수식이 뒤쪽 수식 셀을 참조하면 옛 값을 보는데,
        // 그 재계산은 전파(#813)가 맡는다.
        for (Map.Entry<String, String> entry : formulas.entrySet()) {
            cells.put(entry.getKey(), formulaService.saveAndEvaluate(
                    datasetId, row, entry.getKey(), entry.getValue(), columns, cells,
                    request.tableRefs(), memberId));
        }

        Map<String, String> before = DatasetCells.parse(row.getCells());
        row.updateCells(DatasetCells.serialize(cells));

        // 값이 실제로 바뀐 셀만 전파한다 — 안 바뀐 셀까지 번지면 헛일이다.
        Set<String> recomputed = new HashSet<>();
        for (Map.Entry<String, String> entry : cells.entrySet()) {
            if (!entry.getValue().equals(before.get(entry.getKey()))) {
                formulaService.propagateFrom(datasetId, row.getId(), entry.getKey(), recomputed);
            }
        }

        // 전파로 값이 바뀐 행들. recomputed는 "rowId:colKey"라 행 id만 추리고, 편집 행은 뺀다.
        Set<Long> affectedRowIds = new LinkedHashSet<>();
        for (String cell : recomputed) {
            affectedRowIds.add(DatasetFormulaService.rowIdOf(cell));
        }
        affectedRowIds.remove(row.getId());

        // 전파는 표간으로도 번진다(표간 참조). 이 표 행은 값으로 돌려주고, 다른 표는 id만 알려
        // FE가 그 표 그리드를 다시 받게 한다 — 이 응답은 이 표 스코프라 다른 표 행을 못 싣는다.
        Set<Long> sameDsRowIds = new LinkedHashSet<>();
        Set<Long> otherDatasets = new LinkedHashSet<>();
        for (DatasetRow r : rowRepository.findAllById(affectedRowIds)) {
            if (r.getDatasetId().equals(datasetId)) {
                sameDsRowIds.add(r.getId());
            } else {
                otherDatasets.add(r.getDatasetId());
            }
        }

        // 편집 행: 전파가 이 행의 다른 셀도 고쳤을 수 있어 다시 읽는다.
        RowView edited = buildRowView(datasetId, row, rowIndex, columns);
        return new UpdateRowResponse(edited, buildRowViews(datasetId, sameDsRowIds, columns),
                List.copyOf(otherDatasets));
    }

    /**
     * 여러 행의 현재 상태를 한 번에 조립한다(수식·서식은 배치 조회). 행 번호 오름차순으로 돌려준다.
     * 전파가 셀을 고친 뒤라 각 행 엔티티는 최신 값을 담고 있다(같은 트랜잭션의 영속 컨텍스트).
     */
    private List<RowView> buildRowViews(Long datasetId, Set<Long> rowIds,
                                        List<DatasetColumn> columns) {
        if (rowIds.isEmpty()) {
            return List.of();
        }
        List<DatasetRow> rows = rowRepository.findAllById(rowIds);
        List<Long> ids = rows.stream().map(DatasetRow::getId).toList();
        Map<Long, Map<String, String>> formulas =
                formulaService.displayFormulas(datasetId, ids, columns);
        Map<Long, Map<String, CellStyle>> styles = styleService.stylesByRow(ids);
        return rows.stream()
                .sorted(Comparator.comparingInt(DatasetRow::getRowIndex))
                .map(r -> new RowView(r.getId(), r.getRowIndex(),
                        toCellList(r.getCells(), columns),
                        formulas.getOrDefault(r.getId(), Map.of()),
                        styles.getOrDefault(r.getId(), Map.of())))
                .toList();
    }

    /**
     * 셀 서식(배경색·정렬) 지정. 값·수식과 무관한 표시 속성이라 cells를 건드리지 않는다.
     * 서식을 통째로 교체하며, 둘 다 비면 그 셀 서식을 지운다.
     */
    @Transactional
    public RowView setCellStyle(Long memberId, Long datasetId, int rowIndex, String colKey,
                                SetCellStyleRequest request) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        if (indexOfColumn(columns, colKey) < 0) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        DatasetRow row = rowRepository.findByDatasetIdAndRowIndex(datasetId, rowIndex)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        styleService.setStyle(
                datasetId, row.getId(), colKey, request.bg(), request.align(), request.valign());
        return buildRowView(datasetId, row, rowIndex, columns);
    }

    /**
     * 여러 셀 서식을 한 번에 지정한다(선택 범위·행·열·표 전체 적용). 소유권은 한 번만 확인하고
     * 대상마다 서식을 통째로 교체한다(단건과 같은 의미). 영향받은 행들의 최신 상태를
     * rowIndex 오름차순으로 돌려준다 — FE가 행별로 styles 캐시를 갱신한다.
     */
    @Transactional
    public List<RowView> setCellStylesBulk(Long memberId, Long datasetId,
                                           BulkCellStyleRequest request) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        Map<Integer, DatasetRow> rowByIndex = new LinkedHashMap<>();
        for (BulkCellStyleRequest.Target t : request.cells()) {
            if (indexOfColumn(columns, t.colKey()) < 0) {
                throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            DatasetRow row = rowByIndex.computeIfAbsent(t.rowIndex(), idx ->
                    rowRepository.findByDatasetIdAndRowIndex(datasetId, idx)
                            .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND)));
            styleService.setStyle(
                    datasetId, row.getId(), t.colKey(), t.bg(), t.align(), t.valign());
        }
        return rowByIndex.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> buildRowView(datasetId, e.getValue(), e.getKey(), columns))
                .toList();
    }

    /** 그 dataset의 병합 전체. 세로 병합을 그리려면 FE가 앵커 밖 행까지 알아야 해서 통째로 준다. */
    public MergesResponse getMerges(Long memberId, Long datasetId) {
        getOwned(memberId, datasetId);
        return new MergesResponse(mergeService.allMerges(datasetId));
    }

    /**
     * 셀 병합. 앵커(rowIndex·colKey) 기준으로 영역을 병합한다. 값·수식과 무관한 표시 오버레이라
     * cells를 건드리지 않는다 — 덮인 셀의 값은 보존되고 분할하면 되살아난다.
     * 갱신된 병합 전체를 돌려준다(FE가 오버레이를 다시 그리기 위해).
     */
    @Transactional
    public MergesResponse setCellMerge(Long memberId, Long datasetId, int rowIndex, String colKey,
                                       SetCellMergeRequest request) {
        Dataset dataset = getOwned(memberId, datasetId);
        List<DatasetColumn> columns = parseColumns(dataset.getColumns());
        DatasetRow row = rowRepository.findByDatasetIdAndRowIndex(datasetId, rowIndex)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        mergeService.setMerge(datasetId, row, colKey,
                request.rowSpan(), request.colSpan(), columns, dataset.getRowCount());
        return new MergesResponse(mergeService.allMerges(datasetId));
    }

    /** 병합 해제. 덮여 있던 셀 값은 cells에 그대로 있었으므로 그 자리에 되살아난다. */
    @Transactional
    public MergesResponse unmergeCell(Long memberId, Long datasetId, int rowIndex, String colKey) {
        getOwned(memberId, datasetId);
        DatasetRow row = rowRepository.findByDatasetIdAndRowIndex(datasetId, rowIndex)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        mergeService.unmerge(row.getId(), colKey);
        return new MergesResponse(mergeService.allMerges(datasetId));
    }

    /** 한 행의 현재 상태를 API 형태로 조립한다(값·수식·서식을 함께 싣는다). */
    private RowView buildRowView(Long datasetId, DatasetRow row, int rowIndex,
                                 List<DatasetColumn> columns) {
        List<Long> ids = List.of(row.getId());
        return new RowView(row.getId(), rowIndex, toCellList(row.getCells(), columns),
                formulaService.displayFormulas(datasetId, ids, columns).getOrDefault(row.getId(), Map.of()),
                styleService.stylesByRow(ids).getOrDefault(row.getId(), Map.of()));
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

        // 삽입 위치를 세로 span이 가로지르는 병합은 해제한다(행 인덱스가 밀리기 전에). 끝에 추가면 no-op.
        mergeService.invalidateOnRowInsert(datasetId, at);
        if (at < rowCount) {
            rowRepository.shiftUp(datasetId, at); // 뒤 행을 밀어 자리 확보(flush)
        }
        Map<String, String> cells = DatasetCells.toMap(request.cells(), columns);
        DatasetRow row = rowRepository.save(new DatasetRow(datasetId, at,
                DatasetCells.serialize(cells)));
        dataset.setRowCount(rowCount + 1);

        // 계산 열이면 새 행도 수식을 물려받는다(D10) — 셀 단위 수식이라 안 물려주면 빈 칸이 된다.
        formulaService.inheritFormulasForNewRow(datasetId, row, rowCount, columns, cells);
        // 행이 하나 늘었으니 열 집계를 다시 계산한다.
        Set<String> seen = new HashSet<>();
        for (DatasetColumn column : columns) {
            formulaService.propagateFrom(datasetId, row.getId(), column.key(), seen);
        }
        return new InsertRowResponse(at);
    }

    @Transactional
    public void deleteRow(Long memberId, Long datasetId, int rowIndex) {
        Dataset dataset = getOwned(memberId, datasetId);
        DatasetRow row = rowRepository.findByDatasetIdAndRowIndex(datasetId, rowIndex)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        Long rowId = row.getId();
        rowRepository.delete(row);
        rowRepository.shiftDown(datasetId, rowIndex); // 삭제 flush 후 뒤 행 당김
        dataset.setRowCount(dataset.getRowCount() - 1);
        // 지운 뒤라야 참조가 끊긴 게 드러난다. 그 행을 콕 집어 참조하던 수식은 #REF!,
        // 열 집계는 값이 하나 줄었으니 다시 계산한다.
        formulaService.invalidateAfterRowDelete(datasetId, rowId,
                parseColumns(dataset.getColumns()));
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
