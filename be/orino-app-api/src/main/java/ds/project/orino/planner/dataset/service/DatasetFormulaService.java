package ds.project.orino.planner.dataset.service;

import ds.project.orino.domain.planner.dataset.entity.DatasetFormula;
import ds.project.orino.domain.planner.dataset.entity.DatasetFormulaRef;
import ds.project.orino.domain.planner.dataset.entity.DatasetRow;
import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.dataset.repository.DatasetFormulaRefRepository;
import ds.project.orino.domain.planner.dataset.repository.DatasetFormulaRepository;
import ds.project.orino.domain.planner.dataset.repository.DatasetRepository;
import ds.project.orino.domain.planner.dataset.repository.DatasetRowRepository;
import ds.project.orino.planner.dataset.dto.DatasetColumn;
import ds.project.orino.planner.dataset.formula.FormulaContext;
import ds.project.orino.planner.dataset.formula.FormulaEvaluator;
import ds.project.orino.planner.dataset.formula.FormulaNode;
import ds.project.orino.planner.dataset.formula.FormulaParser;
import ds.project.orino.planner.dataset.formula.FormulaValue;
import ds.project.orino.planner.dataset.formula.FormulaWriter;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 수식 저장·평가. 파서·평가기를 DB에 잇는다.
 *
 * <p>값은 {@code dataset_row.cells}에 그대로 들어가고 수식만 {@code dataset_formula}에 담긴다 —
 * 그래서 읽기 경로가 안 바뀐다.
 *
 * <p>셀이 바뀌면 그 셀을 참조하던 수식을 전이적으로 다시 계산한다({@link #propagateFrom}).
 * 순환 참조는 <b>쓰기 시점에 거부</b>한다 — 쓸 때 계산하는 구조(O1)에서 순환이 저장되면
 * 재계산이 끝나지 않는다.
 */
@Service
public class DatasetFormulaService {

    /** 셀 값이 이걸로 시작하면 수식이다. 엑셀과 같다. */
    static final String PREFIX = "=";

    /**
     * 한 번의 쓰기가 다시 계산할 수 있는 수식 수의 상한.
     *
     * <p>병적인 표(예: 모든 행이 {@code =SUM(c0)})에선 셀 하나를 고치는 데 행 수만큼의 집계가
     * 재계산되고 각 집계가 다시 전체 행을 훑어 폭발한다. 조용히 멈추면 값이 낡은 채 남으므로
     * 차라리 거부한다. 실사용에서 걸리면 값을 올리기 전에 그 표의 설계를 먼저 볼 것.
     */
    static final int MAX_PROPAGATION = 1_000;

    /**
     * 전파 중복 방지 셋({@code seen})의 셀 키 형식({@code rowId:colKey}). 이 형식은 여기서만
     * 만들고 여기서만 되읽는다({@link #rowIdOf}) — 호출부가 값이 바뀐 행을 추려낼 때 쓴다.
     */
    static String seenKey(Long rowId, String colKey) {
        return rowId + ":" + colKey;
    }

    /** {@link #seenKey}가 만든 키에서 행 id를 되읽는다. */
    static Long rowIdOf(String seenKey) {
        return Long.parseLong(seenKey.substring(0, seenKey.indexOf(':')));
    }

    private final DatasetFormulaRepository formulaRepository;
    private final DatasetFormulaRefRepository refRepository;
    private final DatasetRowRepository rowRepository;
    private final DatasetRepository datasetRepository;

    public DatasetFormulaService(DatasetFormulaRepository formulaRepository,
                                 DatasetFormulaRefRepository refRepository,
                                 DatasetRowRepository rowRepository,
                                 DatasetRepository datasetRepository) {
        this.formulaRepository = formulaRepository;
        this.refRepository = refRepository;
        this.rowRepository = rowRepository;
        this.datasetRepository = datasetRepository;
    }

    /** 재계산은 그 dataset의 열 구성을 다시 읽어야 한다(호출부가 안 넘겨주는 경로). */
    private List<DatasetColumn> columnsOf(Long datasetId) {
        return datasetRepository.findById(datasetId)
                .map(d -> DatasetColumns.parse(d.getColumns()))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    static boolean isFormula(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * 수식을 저장하고 계산해 셀에 담길 값을 돌려준다.
     *
     * @param rowCells 지금 만들고 있는 그 행의 값들. 같은 행 참조가 이걸 본다 —
     *                 같은 요청에서 바뀐 값을 즉시 반영하기 위함이다.
     */
    String saveAndEvaluate(Long datasetId, DatasetRow row, String colKey, String input,
                           List<DatasetColumn> columns, Map<String, String> rowCells,
                           Map<String, Long> tableRefs, Long memberId) {
        // 표간 참조 이름 해석·소유권 검증에 tableRefs·memberId가 필요하다(입력 파싱 한정).
        FormulaContext ctx = new DbContext(datasetId, columns, tableRefs, memberId);
        FormulaNode node = FormulaParser.parseInput(input, ctx);

        DatasetFormula formula = formulaRepository.findByRowIdAndColKey(row.getId(), colKey)
                .orElseGet(() -> formulaRepository.save(
                        new DatasetFormula(datasetId, row.getId(), colKey, FormulaWriter.toStored(node))));
        formula.updateRaw(FormulaWriter.toStored(node));
        formulaRepository.save(formula);

        // 참조는 통째로 갈아 끼운다. 수식이 바뀌면 무엇을 참조하는지도 통째로 바뀐다.
        refRepository.deleteByFormulaId(formula.getId());
        for (FormulaNode.Ref ref : FormulaParser.collectRefs(node)) {
            refRepository.save(toEntity(formula.getId(), datasetId, ref));
        }

        // 참조를 저장한 뒤에 본다 — 자기 참조를 그래프에서 따라갈 수 있어야 한다.
        assertNoCycle(datasetId, row.getId(), colKey, node);

        FormulaValue value = FormulaEvaluator.evaluate(node, new DbValues(datasetId, row, rowCells));
        if (value instanceof FormulaValue.Err e) {
            formula.markError(e.code());
        } else {
            formula.clearError();
        }
        return value.asCell();
    }

    /**
     * 셀 {@code (rowId, colKey)}가 바뀌었을 때 그 셀을 참조하던 수식들을 전이적으로 다시 계산한다.
     *
     * <p>전파 범위는 참조 종류가 가른다(D9) — {@code SAME_ROW}는 <b>같은 행 안에서만</b> 번지므로
     * 계산 열(가장 흔한 패턴)의 편집이 다른 행을 건드리지 않는다. {@code COLUMN_ALL}(집계)만
     * 열의 아무 행이 바뀌어도 걸린다.
     *
     * @param seen 이미 다시 계산한 셀. 재귀 사이에 공유해 같은 셀을 두 번 계산하지 않는다.
     */
    void propagateFrom(Long datasetId, Long rowId, String colKey, Set<String> seen) {
        propagateFrom(datasetId, rowId, colKey, seen, MAX_PROPAGATION);
    }

    /** 예산을 정해 전파한다. fill down처럼 <b>본래 행 수만큼</b> 번지는 작업은 상한이 달라야 한다. */
    void propagateFrom(Long datasetId, Long rowId, String colKey, Set<String> seen, int budget) {
        for (Long formulaId : refRepository.findDependentFormulaIds(datasetId, rowId, colKey)) {
            DatasetFormula formula = formulaRepository.findById(formulaId).orElse(null);
            if (formula == null) {
                continue;
            }
            String cell = seenKey(formula.getRowId(), formula.getColKey());
            if (!seen.add(cell)) {
                continue;
            }
            if (seen.size() > budget) {
                throw new CustomException(ErrorCode.FORMULA_PROPAGATION_TOO_WIDE,
                        "다시 계산할 수식이 " + budget + "개를 넘습니다");
            }
            // 의존 수식은 다른 표에 있을 수 있다(표간 참조). 재계산·다음 전파는 그 수식의 표 기준.
            Long formulaDs = formula.getDatasetId();
            recompute(formulaDs, formula);
            // 이 수식의 셀도 값이 바뀌었으니 그걸 참조하던 것들로 계속 번진다.
            propagateFrom(formulaDs, formula.getRowId(), formula.getColKey(), seen, budget);
        }
    }

    /** 수식 하나를 다시 계산해 그 셀에 값을 쓴다. */
    private void recompute(Long datasetId, DatasetFormula formula) {
        DatasetRow row = rowRepository.findById(formula.getRowId()).orElse(null);
        if (row == null) {
            return;
        }
        List<DatasetColumn> columns = columnsOf(datasetId);
        Map<String, String> cells = DatasetCells.parse(row.getCells());

        FormulaValue value;
        try {
            FormulaNode node = FormulaParser.parseStored(formula.getRaw(),
                    new DbContext(datasetId, columns));
            value = FormulaEvaluator.evaluate(node, new DbValues(datasetId, row, cells));
        } catch (CustomException e) {
            // 저장된 수식이 지금 구성으로 파싱이 안 된다 = 참조하던 열이 사라졌다는 뜻.
            value = new FormulaValue.Err(FormulaValue.Err.REF);
        }

        if (value instanceof FormulaValue.Err e) {
            formula.markError(e.code());
        } else {
            formula.clearError();
        }
        cells.put(formula.getColKey(), value.asCell());
        row.updateCells(DatasetCells.serialize(cells));
    }

    /**
     * 새 수식이 자기 자신에 닿는지 본다. 닿으면 저장 자체를 막는다.
     *
     * <p>참조를 따라 <b>앞으로</b> 걷는다 — 이 수식이 무엇을 참조하고, 그것들이 또 무엇을
     * 참조하는지. 도중에 자기 셀을 만나면 순환이다.
     */
    private void assertNoCycle(Long datasetId, Long rowId, String colKey, FormulaNode node) {
        Set<Long> visited = new HashSet<>();
        for (FormulaNode.Ref ref : FormulaParser.collectRefs(node)) {
            for (DatasetFormula target : targetsOf(datasetId, rowId, ref)) {
                walk(rowId, colKey, target, visited);
            }
        }
    }

    /**
     * 참조 그래프를 앞으로 걸으며 자기 셀에 닿는지 본다. 표간 참조가 있어 각 수식은 <b>자기 표</b>
     * 기준으로 파싱·추적한다(rowId는 전역 유일이라 표가 달라도 self 셀 판정이 정확하다).
     */
    private void walk(Long selfRow, String selfCol, DatasetFormula formula, Set<Long> visited) {
        if (formula.getRowId().equals(selfRow) && formula.getColKey().equals(selfCol)) {
            throw new CustomException(ErrorCode.FORMULA_CIRCULAR_REFERENCE);
        }
        if (!visited.add(formula.getId())) {
            return;
        }
        Long ds = formula.getDatasetId();
        FormulaNode node;
        try {
            node = FormulaParser.parseStored(formula.getRaw(), new DbContext(ds, columnsOf(ds)));
        } catch (CustomException e) {
            return; // 이미 깨진 수식은 순환 판정에 쓰지 않는다.
        }
        for (FormulaNode.Ref ref : FormulaParser.collectRefs(node)) {
            for (DatasetFormula next : targetsOf(ds, formula.getRowId(), ref)) {
                walk(selfRow, selfCol, next, visited);
            }
        }
    }

    /** 참조가 가리키는 자리에 있는 수식들. 값만 있는 셀은 더 따라갈 게 없다. */
    private List<DatasetFormula> targetsOf(Long datasetId, Long fromRowId, FormulaNode.Ref ref) {
        return switch (ref.kind()) {
            case SAME_ROW -> formulaRepository.findByRowIdAndColKey(fromRowId, ref.colKey())
                    .map(List::of).orElseGet(List::of);
            case ABSOLUTE -> formulaRepository.findByRowIdAndColKey(ref.rowId(), ref.colKey())
                    .map(List::of).orElseGet(List::of);
            // 집계는 그 열의 모든 수식에 닿는다. 표간 집계면 대상 표의 열이다(같은 colKey가
            // 이 표에도 있어 자기 자신으로 오인하지 않게 — #915a-2).
            case COLUMN_ALL -> formulaRepository.findByDatasetIdAndColKey(
                    ref.datasetId() == null ? datasetId : ref.datasetId(), ref.colKey());
        };
    }

    /**
     * 그 행들의 수식을 <b>표시형</b>(열 이름·행 번호)으로. 수식 없는 셀은 담기지 않는다.
     *
     * <p>표시형은 저장형과 같은 문법이라 클라이언트가 그대로 돌려주면 다시 파싱된다 —
     * 그게 행을 수정해도 수식이 안 지워지는 방법이다.
     */
    Map<Long, Map<String, String>> displayFormulas(Long datasetId, List<Long> rowIds,
                                                   List<DatasetColumn> columns) {
        if (rowIds.isEmpty()) {
            return Map.of();
        }
        FormulaContext ctx = new DbContext(datasetId, columns);
        Map<Long, Map<String, String>> out = new LinkedHashMap<>();
        for (DatasetFormula f : formulaRepository.findByRowIdIn(rowIds)) {
            String display;
            try {
                display = FormulaWriter.toDisplay(
                        FormulaParser.parseStored(f.getRaw(), ctx), ctx);
            } catch (CustomException e) {
                // 참조하던 열이 사라져 파싱이 안 되면 표시할 수식이 없다(#814가 #REF!로 정리).
                display = FormulaValue.Err.REF;
            }
            out.computeIfAbsent(f.getRowId(), k -> new LinkedHashMap<>())
                    .put(f.getColKey(), display);
        }
        return out;
    }

    /**
     * 열이 지워졌을 때. 두 가지를 한다.
     *
     * <ol>
     *   <li><b>그 열에 있던 수식은 지운다</b> — 담겨 있던 셀 자체가 사라졌다
     *   <li><b>그 열을 참조하던 수식은 {@code #REF!}로</b> 만든다. 저장형이 없는 열을 가리키면
     *       파싱이 실패하고, 그게 곧 참조가 끊겼다는 신호다
     * </ol>
     *
     * <p>비용은 <b>O(그 열에 얽힌 수식 수)</b>지 O(행 수)가 아니다 — 열 삭제가 O(1)인 것(#800)과
     * 양립한다.
     */
    void invalidateColumn(Long datasetId, String colKey) {
        // 1. 그 열에 있던 수식 — 셀이 없어졌으니 수식도 없앤다.
        for (DatasetFormula f : formulaRepository.findByDatasetIdAndColKey(datasetId, colKey)) {
            refRepository.deleteByFormulaId(f.getId());
            formulaRepository.delete(f);
        }
        // 2. 그 열을 참조하던 수식 — #REF!로.
        Set<String> seen = new HashSet<>();
        for (Long formulaId : refRepository.findFormulaIdsReferencingColumn(datasetId, colKey)) {
            formulaRepository.findById(formulaId).ifPresent(f -> {
                recompute(datasetId, f);
                // 그 셀 값이 바뀌었으니 그걸 참조하던 것들로 계속 번진다.
                propagateFrom(datasetId, f.getRowId(), f.getColKey(), seen);
            });
        }
    }

    /**
     * 행이 지워진 <b>뒤에</b> 부른다. 순서가 중요하다 — 지우기 전에 부르면 평가할 때 그 행이
     * 아직 있어 {@code #REF!}가 안 나온다. 지운 뒤라야 참조가 끊긴 게 드러난다
     * ({@code to_row_id}엔 FK가 없어 끊긴 참조가 남는다).
     *
     * <p>두 가지를 한다.
     * <ol>
     *   <li>그 행을 <b>콕 집어</b> 참조하던 수식({@code ABSOLUTE})을 {@code #REF!}로
     *   <li>모든 열의 <b>집계</b>({@code COLUMN_ALL})를 다시 계산 — 행이 하나 줄었으니 합계가 바뀐다
     * </ol>
     *
     * <p>그 행에 있던 수식은 FK cascade로 이미 사라졌고, {@code SAME_ROW}는 자기 행만 가리키므로
     * 따로 손댈 게 없다.
     */
    void invalidateAfterRowDelete(Long datasetId, Long rowId, List<DatasetColumn> columns) {
        Set<String> seen = new HashSet<>();
        for (Long formulaId : refRepository.findFormulaIdsReferencingRow(datasetId, rowId)) {
            formulaRepository.findById(formulaId).ifPresent(f -> {
                recompute(datasetId, f);
                propagateFrom(datasetId, f.getRowId(), f.getColKey(), seen);
            });
        }
        // 행이 줄어 값 집합이 바뀐 열들의 집계.
        for (DatasetColumn column : columns) {
            propagateFrom(datasetId, rowId, column.key(), seen);
        }
    }

    /**
     * 한 셀의 수식을 그 열의 모든 행에 채운다(fill down) — 계산 열을 만드는 방법.
     *
     * <p><b>복사가 그냥 되는 이유는 D9다.</b> 저장형의 같은 행 참조({@code {c0}})엔 행 id가 없어
     * 각 행이 자기 행을 가리킨다. 절대 참조({@code {c0}@142})는 복사해도 그 행을 계속 가리킨다 —
     * 엑셀의 {@code $A$1}과 같은 성질이라 저장형을 <b>글자 그대로</b> 옮기면 된다.
     *
     * <p><b>먼저 다 쓰고 나중에 전파한다.</b> 한 행씩 쓰면서 전파하면 열 집계가 반쯤 채워진
     * 상태로 계산돼 틀린 값이 나오고, 행 수만큼 다시 계산된다.
     *
     * @return 채운 행 수
     */
    int fillDownColumn(Long datasetId, String colKey, DatasetRow source,
                       List<DatasetColumn> columns) {
        DatasetFormula origin = formulaRepository.findByRowIdAndColKey(source.getId(), colKey)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST,
                        "채울 수식이 없는 셀입니다"));
        String raw = origin.getRaw();

        List<DatasetRow> rows = rowRepository.findByDatasetIdOrderByRowIndexAsc(datasetId);
        int filled = 0;
        // 1단계 — 전부 쓴다. 아직 전파하지 않는다.
        for (DatasetRow row : rows) {
            if (row.getId().equals(source.getId())) {
                continue;
            }
            writeFormula(datasetId, row, colKey, raw, columns);
            filled++;
        }
        // 2단계 — 값이 다 확정된 뒤에 한 번 전파한다. seen을 공유하므로 열 집계는
        // 첫 행에서 한 번만(최종 값으로) 다시 계산된다.
        Set<String> seen = new HashSet<>();
        int budget = rows.size() + MAX_PROPAGATION;
        for (DatasetRow row : rows) {
            propagateFrom(datasetId, row.getId(), colKey, seen, budget);
        }
        return filled;
    }

    /**
     * 소스 블록을 대상 행들에 <b>세로로 타일링</b>해 채운다(엑셀 채우기 핸들). 소스 셀이 수식이면
     * 저장형을 글자 그대로 옮긴다(D9: 같은 행 참조는 행마다 상대, 절대 참조는 핀 고정). 리터럴이면
     * 값을 옮기고 대상의 기존 수식은 지운다.
     *
     * <p><b>전부 쓴 뒤 한 번만 전파한다</b>({@link #fillDownColumn}과 같은 이유) — 채우는 도중
     * 열 집계가 반쯤 채워진 상태로 재계산돼 틀린 값이 나오지 않게.
     *
     * @param srcR0   소스 첫 행의 rowIndex(타일링 기준점)
     * @param srcRows rowIndex 오름차순 소스 행들(선택 블록의 행)
     * @param dstRows 채울 대상 행들(소스 위/아래로 인접, 소스와 겹치지 않음)
     * @return 값이 바뀐(대상 + 전파) 행 id들
     */
    Set<Long> fillRange(Long datasetId, List<String> cols, int srcR0, List<DatasetRow> srcRows,
                        List<DatasetRow> dstRows, List<DatasetColumn> columns) {
        int h = srcRows.size();
        // 1단계 — 전부 쓴다(아직 전파하지 않는다).
        for (DatasetRow dst : dstRows) {
            int offset = Math.floorMod(dst.getRowIndex() - srcR0, h);
            DatasetRow src = srcRows.get(offset);
            Map<String, String> srcCells = DatasetCells.parse(src.getCells());
            for (String col : cols) {
                DatasetFormula srcFormula =
                        formulaRepository.findByRowIdAndColKey(src.getId(), col).orElse(null);
                if (srcFormula != null) {
                    writeFormula(datasetId, dst, col, srcFormula.getRaw(), columns);
                } else {
                    Map<String, String> cells = DatasetCells.parse(dst.getCells());
                    cells.put(col, srcCells.getOrDefault(col, ""));
                    dst.updateCells(DatasetCells.serialize(cells));
                    removeIfAny(dst.getId(), col);
                }
            }
        }
        // 2단계 — 값이 다 확정된 뒤 한 번 전파한다. seen을 공유해 열 집계는 최종 값으로 한 번만.
        Set<String> seen = new HashSet<>();
        int budget = dstRows.size() * cols.size() + MAX_PROPAGATION;
        for (DatasetRow dst : dstRows) {
            for (String col : cols) {
                propagateFrom(datasetId, dst.getId(), col, seen, budget);
            }
        }
        // 영향 행 = 대상 + 전파로 바뀐 행.
        Set<Long> affected = new LinkedHashSet<>();
        for (DatasetRow dst : dstRows) {
            affected.add(dst.getId());
        }
        for (String cell : seen) {
            affected.add(rowIdOf(cell));
        }
        return affected;
    }

    /**
     * 행이 추가됐을 때 <b>계산 열</b>의 수식을 물려준다(D10).
     *
     * <p>그 열의 모든 셀이 같은 수식일 때만 물려준다 — 섞여 있으면 사용자가 의도한 게 아니다.
     * 엑셀 표(ListObject)의 calculated column과 같은 규칙. "바로 윗 행 복사"는 윗 행만
     * 예외적으로 수식을 가졌을 때 의도와 어긋난다.
     */
    void inheritFormulasForNewRow(Long datasetId, DatasetRow row, int rowCountBefore,
                                  List<DatasetColumn> columns, Map<String, String> cells) {
        for (DatasetColumn column : columns) {
            String key = column.key();
            // 균일 = 이전 모든 행에 수식이 있고, 그 수식이 하나뿐이다.
            if (rowCountBefore == 0
                    || formulaRepository.countByDatasetIdAndColKey(datasetId, key) != rowCountBefore
                    || formulaRepository.countDistinctRawByColumn(datasetId, key) != 1) {
                continue;
            }
            String raw = formulaRepository.findByDatasetIdAndColKey(datasetId, key)
                    .getFirst().getRaw();
            cells.put(key, writeFormula(datasetId, row, key, raw, columns));
        }
    }

    /** 저장형 수식을 그 셀에 쓰고 계산해 값을 남긴다. 전파는 호출부가 정한다. */
    private String writeFormula(Long datasetId, DatasetRow row, String colKey, String raw,
                                List<DatasetColumn> columns) {
        DatasetFormula formula = formulaRepository.findByRowIdAndColKey(row.getId(), colKey)
                .orElseGet(() -> formulaRepository.save(
                        new DatasetFormula(datasetId, row.getId(), colKey, raw)));
        formula.updateRaw(raw);
        formulaRepository.save(formula);

        refRepository.deleteByFormulaId(formula.getId());
        FormulaNode node = FormulaParser.parseStored(raw, new DbContext(datasetId, columns));
        for (FormulaNode.Ref ref : FormulaParser.collectRefs(node)) {
            refRepository.save(toEntity(formula.getId(), datasetId, ref));
        }

        Map<String, String> cells = DatasetCells.parse(row.getCells());
        FormulaValue value = FormulaEvaluator.evaluate(node, new DbValues(datasetId, row, cells));
        if (value instanceof FormulaValue.Err e) {
            formula.markError(e.code());
        } else {
            formula.clearError();
        }
        cells.put(colKey, value.asCell());
        row.updateCells(DatasetCells.serialize(cells));
        return value.asCell();
    }

    /** 셀이 더 이상 수식이 아니면(리터럴로 덮어씀) 수식과 참조를 지운다. */
    void removeIfAny(Long rowId, String colKey) {
        formulaRepository.findByRowIdAndColKey(rowId, colKey).ifPresent(f -> {
            refRepository.deleteByFormulaId(f.getId());
            formulaRepository.delete(f);
        });
    }

    private DatasetFormulaRef toEntity(Long formulaId, Long datasetId, FormulaNode.Ref ref) {
        return switch (ref.kind()) {
            case SAME_ROW -> DatasetFormulaRef.sameRow(formulaId, datasetId, ref.colKey());
            case ABSOLUTE -> ref.datasetId() == null
                    ? DatasetFormulaRef.absolute(formulaId, datasetId, ref.rowId(), ref.colKey())
                    : DatasetFormulaRef.crossAbsolute(formulaId, datasetId, ref.datasetId(),
                            ref.rowId(), ref.colKey());
            case COLUMN_ALL -> ref.datasetId() == null
                    ? DatasetFormulaRef.columnAll(formulaId, datasetId, ref.colKey())
                    : DatasetFormulaRef.crossColumnAll(formulaId, datasetId, ref.datasetId(),
                            ref.colKey());
        };
    }

    /** 파서가 필요로 하는 열·행 조회를 DB로 잇는다. */
    private final class DbContext implements FormulaContext {
        private final Long datasetId;
        private final List<DatasetColumn> columns;
        // 표간 참조 이름 해석용(입력 파싱). 이름→대상 표 id. null이면 표간 이름 해석 불가(recompute).
        private final Map<String, Long> tableRefs;
        // 표간 대상 소유권 검증용. null이면 검증 없이(recompute — 저장 때 이미 검증됨).
        private final Long memberId;

        private DbContext(Long datasetId, List<DatasetColumn> columns) {
            this(datasetId, columns, null, null);
        }

        private DbContext(Long datasetId, List<DatasetColumn> columns,
                          Map<String, Long> tableRefs, Long memberId) {
            this.datasetId = datasetId;
            this.columns = columns;
            this.tableRefs = tableRefs;
            this.memberId = memberId;
        }

        @Override
        public List<String> columnKeys() {
            return columns.stream().map(DatasetColumn::key).toList();
        }

        @Override
        public Optional<String> keyByLabel(String label) {
            return columns.stream()
                    .filter(c -> c.label().equals(label))
                    .map(DatasetColumn::key)
                    .findFirst();
        }

        @Override
        public Optional<String> labelByKey(String key) {
            return columns.stream()
                    .filter(c -> c.key().equals(key))
                    .map(DatasetColumn::label)
                    .findFirst();
        }

        @Override
        public Optional<Long> rowIdByNumber(int rowNumber) {
            // 화면은 1-base, row_index는 0-base.
            return rowRepository.findByDatasetIdAndRowIndex(datasetId, rowNumber - 1)
                    .map(DatasetRow::getId);
        }

        @Override
        public Optional<Integer> rowNumberById(long rowId) {
            return rowRepository.findById(rowId)
                    .filter(r -> r.getDatasetId().equals(datasetId))
                    .map(r -> r.getRowIndex() + 1);
        }

        @Override
        public Optional<Long> tableIdByName(String name) {
            if (tableRefs == null) {
                return Optional.empty();
            }
            Long id = tableRefs.get(name);
            if (id == null) {
                return Optional.empty();
            }
            // 남의 표 참조 차단 — 대상이 같은 회원 것일 때만 해석된다(없으면 "없는 표").
            boolean owned = memberId != null
                    && datasetRepository.findByIdAndMemberId(id, memberId).isPresent();
            return owned ? Optional.of(id) : Optional.empty();
        }

        @Override
        public Optional<String> tableNameById(long targetId) {
            return datasetRepository.findById(targetId)
                    .map(d -> d.getName())
                    .filter(n -> n != null && !n.isBlank());
        }

        @Override
        public FormulaContext forDataset(long targetId) {
            // 대상 표의 열로 새 컨텍스트. 지워졌으면 빈 열 → 열·행 해석이 empty → #REF!.
            List<DatasetColumn> targetColumns = datasetRepository.findById(targetId)
                    .map(d -> DatasetColumns.parse(d.getColumns()))
                    .orElseGet(List::of);
            return new DbContext(targetId, targetColumns, tableRefs, memberId);
        }
    }

    /** 요약 함수 토큰(FE·columns_json) → 엔진 집계 함수명. AVERAGE만 엔진에선 AVG다. */
    private static final Map<String, String> SUMMARY_ENGINE_FUNC = Map.of(
            "SUM", "SUM", "AVERAGE", "AVG", "COUNT", "COUNT", "MIN", "MIN", "MAX", "MAX");

    /**
     * 요약 함수가 설정된 열들의 값을 계산한다(#908). 기존 엔진의 열 집계({@link FormulaNode.Agg},
     * {@code COLUMN_ALL})를 그대로 재사용한다 — 새 계산 로직·ref kind가 없다. 전체 행을 한 번만
     * 읽어 여러 열에 나눠 쓴다. 요약이 없으면 빈 맵.
     *
     * @return 열 key → 계산된 값(문자열). 열 집계는 숫자만 세고 빈/텍스트는 규칙대로 다룬다.
     */
    Map<String, String> computeSummaries(Long datasetId, List<DatasetColumn> columns) {
        List<DatasetColumn> withSummary = columns.stream()
                .filter(c -> c.summary() != null)
                .toList();
        Map<String, String> out = new LinkedHashMap<>();
        if (withSummary.isEmpty()) {
            return out;
        }
        FormulaEvaluator.ValueSource src = new SummaryValues(datasetId);
        for (DatasetColumn col : withSummary) {
            String func = SUMMARY_ENGINE_FUNC.get(col.summary());
            FormulaValue value = FormulaEvaluator.evaluate(
                    new FormulaNode.Agg(func, List.of(col.key())), src);
            out.put(col.key(), value.asCell());
        }
        return out;
    }

    /**
     * 열 집계 계산용 읽기 소스. 전체 행을 한 번 로드해 {@code column()}에만 답한다(요약은 열
     * 전체 집계뿐이라 {@code sameRow}/{@code absolute}는 쓰지 않는다).
     */
    private final class SummaryValues implements FormulaEvaluator.ValueSource {
        private final List<Map<String, String>> rows;

        private SummaryValues(Long datasetId) {
            this.rows = rowRepository.findByDatasetIdOrderByRowIndexAsc(datasetId).stream()
                    .map(r -> DatasetCells.parse(r.getCells()))
                    .toList();
        }

        @Override
        public Optional<String> sameRow(String colKey) {
            return Optional.empty();
        }

        @Override
        public Optional<String> absolute(long rowId, String colKey) {
            return Optional.empty();
        }

        @Override
        public Optional<List<String>> column(String colKey) {
            return Optional.of(rows.stream()
                    .map(cells -> cells.getOrDefault(colKey, ""))
                    .toList());
        }
    }

    /** 평가기가 읽을 셀 값을 DB(와 지금 만드는 행)에서 가져온다. */
    private final class DbValues implements FormulaEvaluator.ValueSource {
        private final Long datasetId;
        private final DatasetRow row;
        private final Map<String, String> rowCells;

        private DbValues(Long datasetId, DatasetRow row, Map<String, String> rowCells) {
            this.datasetId = datasetId;
            this.row = row;
            this.rowCells = rowCells;
        }

        @Override
        public Optional<String> sameRow(String colKey) {
            // 지금 저장 중인 값을 본다 — 같은 요청에서 c0을 고치며 c1=c0*2를 넣으면 새 c0을 써야 한다.
            return Optional.ofNullable(rowCells.get(colKey));
        }

        @Override
        public Optional<String> absolute(long rowId, String colKey) {
            if (row.getId() != null && row.getId().equals(rowId)) {
                return sameRow(colKey);
            }
            return rowRepository.findById(rowId)
                    .filter(r -> r.getDatasetId().equals(datasetId))
                    .map(r -> DatasetCells.parse(r.getCells()).getOrDefault(colKey, ""));
        }

        /** 표간 절대셀 — 대상 표의 그 행·열 값. 행이 없거나 대상 표가 다르면 empty → #REF!. */
        @Override
        public Optional<String> crossAbsolute(long targetDatasetId, long targetRowId,
                                              String colKey) {
            return rowRepository.findById(targetRowId)
                    .filter(r -> r.getDatasetId().equals(targetDatasetId))
                    .map(r -> DatasetCells.parse(r.getCells()).getOrDefault(colKey, ""));
        }

        /** 표간 열 집계 — 대상 표의 그 열 전체 값(행 순서). */
        @Override
        public Optional<List<String>> crossColumn(long targetDatasetId, String colKey) {
            List<String> values = rowRepository
                    .findByDatasetIdOrderByRowIndexAsc(targetDatasetId).stream()
                    .map(r -> DatasetCells.parse(r.getCells()).getOrDefault(colKey, ""))
                    .toList();
            return Optional.of(values);
        }

        /** 열이 실재하는지는 파서가 저장 시점에 이미 검증했다. 지워진 열은 #814가 다룬다. */
        @Override
        public Optional<List<String>> column(String colKey) {
            // 열 집계는 전체 행을 읽는다. O1(쓸 때 계산)이 감수하기로 한 비용이다 —
            // 읽을 때 계산하면 페이지를 볼 때마다 이 비용을 치른다.
            List<String> values = rowRepository.findByDatasetIdOrderByRowIndexAsc(datasetId).stream()
                    .map(r -> r.getId().equals(row.getId())
                            ? rowCells.getOrDefault(colKey, "")
                            : DatasetCells.parse(r.getCells()).getOrDefault(colKey, ""))
                    .toList();
            return Optional.of(values);
        }
    }
}
