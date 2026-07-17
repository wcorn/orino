package ds.project.orino.planner.dataset.service;

import ds.project.orino.domain.planner.dataset.entity.DatasetFormula;
import ds.project.orino.domain.planner.dataset.entity.DatasetFormulaRef;
import ds.project.orino.domain.planner.dataset.entity.DatasetRow;
import ds.project.orino.domain.planner.dataset.repository.DatasetFormulaRefRepository;
import ds.project.orino.domain.planner.dataset.repository.DatasetFormulaRepository;
import ds.project.orino.domain.planner.dataset.repository.DatasetRowRepository;
import ds.project.orino.planner.dataset.dto.DatasetColumn;
import ds.project.orino.planner.dataset.formula.FormulaContext;
import ds.project.orino.planner.dataset.formula.FormulaEvaluator;
import ds.project.orino.planner.dataset.formula.FormulaNode;
import ds.project.orino.planner.dataset.formula.FormulaParser;
import ds.project.orino.planner.dataset.formula.FormulaValue;
import ds.project.orino.planner.dataset.formula.FormulaWriter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 수식 저장·평가. 파서·평가기를 DB에 잇는다.
 *
 * <p>값은 {@code dataset_row.cells}에 그대로 들어가고 수식만 {@code dataset_formula}에 담긴다 —
 * 그래서 읽기 경로가 안 바뀐다.
 *
 * <p><b>이 클래스는 수식 셀 자신을 저장할 때만 계산한다.</b> 참조하던 셀이 나중에 바뀌었을 때의
 * 재계산(전파)과 순환 참조 거부는 #813의 몫이다.
 */
@Service
public class DatasetFormulaService {

    /** 셀 값이 이걸로 시작하면 수식이다. 엑셀과 같다. */
    static final String PREFIX = "=";

    private final DatasetFormulaRepository formulaRepository;
    private final DatasetFormulaRefRepository refRepository;
    private final DatasetRowRepository rowRepository;

    public DatasetFormulaService(DatasetFormulaRepository formulaRepository,
                                 DatasetFormulaRefRepository refRepository,
                                 DatasetRowRepository rowRepository) {
        this.formulaRepository = formulaRepository;
        this.refRepository = refRepository;
        this.rowRepository = rowRepository;
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
                           List<DatasetColumn> columns, Map<String, String> rowCells) {
        FormulaContext ctx = new DbContext(datasetId, columns);
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

        FormulaValue value = FormulaEvaluator.evaluate(node, new DbValues(datasetId, row, rowCells));
        if (value instanceof FormulaValue.Err e) {
            formula.markError(e.code());
        } else {
            formula.clearError();
        }
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
            case ABSOLUTE -> DatasetFormulaRef.absolute(formulaId, datasetId, ref.rowId(),
                    ref.colKey());
            case COLUMN_ALL -> DatasetFormulaRef.columnAll(formulaId, datasetId, ref.colKey());
        };
    }

    /** 파서가 필요로 하는 열·행 조회를 DB로 잇는다. */
    private final class DbContext implements FormulaContext {
        private final Long datasetId;
        private final List<DatasetColumn> columns;

        private DbContext(Long datasetId, List<DatasetColumn> columns) {
            this.datasetId = datasetId;
            this.columns = columns;
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
