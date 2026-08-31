package ds.project.orino.planner.dataset.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.dataset.entity.DatasetRow;
import ds.project.orino.domain.planner.dataset.repository.DatasetRowRepository;
import ds.project.orino.planner.dataset.dto.BulkRowsRequest;
import ds.project.orino.planner.dataset.dto.CreateDatasetRequest;
import ds.project.orino.planner.dataset.dto.DatasetColumn;
import ds.project.orino.planner.dataset.dto.DatasetResponse;
import ds.project.orino.planner.dataset.formula.FormulaNode;
import ds.project.orino.planner.dataset.xlsx.DatasetSheetReader;
import ds.project.orino.planner.dataset.xlsx.FormulaA1Reader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * .xlsx 시트 한 장을 표 하나로 들인다(#1310 · Epic #892 c).
 *
 * <p><b>매핑 단계가 없다.</b> 표에는 목적지 스키마가 없어서 시트가 곧 표고 첫 줄이 곧 열 이름이다 —
 * 원장 이관({@code LedgerImportService})이 「이 열이 날짜인가」를 물어야 했던 것과 다른 점이다.
 *
 * <p><b>두 번에 나눠 넣는다.</b> 수식이 특정 행을 가리키려면 그 행의 <b>id</b>가 있어야 하는데,
 * id는 행을 만든 뒤에야 생긴다. 그래서 값으로 먼저 넣어 id를 받고, 그 id 표로 A1을 옮겨 두 번째로
 * 쓴다. 한 번에 하려면 임시 id라는 없는 개념을 만들어야 한다.
 */
@Service
public class DatasetXlsxImportService {

    /** 푸터 요약으로 인정할 함수. 열 설정이 받는 것과 같아야 한다. */
    private static final List<String> SUMMARY_FUNCTIONS =
            List.of(DatasetColumn.ALLOWED_SUMMARY.split("\\|"));

    private final DatasetSheetReader reader;
    private final DatasetService datasetService;
    private final DatasetFormulaService formulaService;
    private final DatasetCellStyleService styleService;
    private final DatasetMergeService mergeService;
    private final DatasetRowRepository rowRepository;

    public DatasetXlsxImportService(DatasetSheetReader reader,
                                    DatasetService datasetService,
                                    DatasetFormulaService formulaService,
                                    DatasetCellStyleService styleService,
                                    DatasetMergeService mergeService,
                                    DatasetRowRepository rowRepository) {
        this.reader = reader;
        this.datasetService = datasetService;
        this.formulaService = formulaService;
        this.styleService = styleService;
        this.mergeService = mergeService;
        this.rowRepository = rowRepository;
    }

    /** 파일에 어떤 시트가 들었는지. 고르기 전에 보여줄 미리보기까지 함께 준다. */
    public List<SheetSummary> analyze(MultipartFile file, int previewRows) {
        List<SheetSummary> out = new ArrayList<>();
        for (DatasetSheetReader.RawSheet sheet : reader.read(file)) {
            List<List<String>> preview = sheet.rows().stream()
                    .limit(previewRows)
                    .map(row -> row.stream().map(DatasetSheetReader.RawCell::value).toList())
                    .toList();
            out.add(new SheetSummary(
                    sheet.name(), sheet.rows().size(), sheet.columnCount(), preview));
        }
        return out;
    }

    /**
     * 시트 하나를 표로 들인다.
     *
     * @param firstRowAsHeader 첫 줄을 열 이름으로 쓸지. 아니면 {@code 열 1}… 로 짓고 첫 줄도 데이터다
     */
    @Transactional
    public ImportResult importSheet(Long memberId, MultipartFile file, String sheetName,
                                    boolean firstRowAsHeader) {
        DatasetSheetReader.RawSheet sheet = pickSheet(reader.read(file), sheetName);

        int headerOffset = firstRowAsHeader ? 1 : 0;
        List<List<DatasetSheetReader.RawCell>> dataRows =
                sheet.rows().subList(Math.min(headerOffset, sheet.rows().size()),
                        sheet.rows().size());
        int columnCount = Math.max(sheet.columnCount(), 1);

        // 요약줄은 데이터가 아니다 — 내보낼 때 마지막에 붙인 그 줄이면 열 설정으로 되돌린다.
        // 요약이 가리킬 구간은 요약줄 자신을 뺀 곳이라 끝 행이 하나 앞이다.
        FormulaA1Reader summaryProbe = new FormulaA1Reader(new FormulaA1Reader.Layout(
                Map.of(), Map.of(), headerOffset + 1, headerOffset + dataRows.size() - 1));
        Map<Integer, String> summaries = detectSummaryRow(dataRows, columnCount, summaryProbe);
        if (!summaries.isEmpty()) {
            dataRows = dataRows.subList(0, dataRows.size() - 1);
        }

        List<DatasetColumn> columns = buildColumns(
                sheet, firstRowAsHeader, columnCount, summaries);
        DatasetResponse created = datasetService.create(
                memberId, new CreateDatasetRequest(columns));
        // 이름·중복 정리를 서비스가 했으므로, 이후 열 key는 돌려받은 것을 쓴다.
        List<DatasetColumn> stored = created.columns();

        // 시트 이름이 곧 표 이름이다 — 내보낼 때 표 이름을 시트 이름으로 적었으므로 왕복이 닫히고,
        // 남의 파일에서도 사람이 엑셀에서 보던 그 이름이 그대로 붙는다. 표간 참조(#915)가
        // 이름으로 표를 지목하므로 이름이 있어야 바로 가리킬 수 있다.
        datasetService.setName(memberId, created.id(), sheet.name());

        writeValues(memberId, created.id(), dataRows, stored);

        List<DatasetRow> rows = rowRepository
                .findByDatasetIdAndRowIndexGreaterThanEqualAndRowIndexLessThanOrderByRowIndexAsc(
                        created.id(), 0, Integer.MAX_VALUE);
        int formulasImported = writeFormulas(
                created.id(), dataRows, rows, stored, headerOffset);
        int formulasAsValue = countFormulas(dataRows) - formulasImported;

        writeStyles(created.id(), dataRows, rows, stored);
        writeMerges(created.id(), sheet, rows, stored, headerOffset);

        return new ImportResult(created.id(), rows.size(), stored.size(),
                formulasImported, formulasAsValue);
    }

    private DatasetSheetReader.RawSheet pickSheet(List<DatasetSheetReader.RawSheet> sheets,
                                                 String sheetName) {
        if (sheets.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "시트가 없는 파일입니다.");
        }
        if (sheetName == null || sheetName.isBlank()) {
            return sheets.get(0);
        }
        return sheets.stream()
                .filter(s -> s.name().equals(sheetName))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 마지막 줄이 요약줄인지 본다.
     *
     * <p>비어 있지 않은 칸이 <b>모두</b> 자기 열의 데이터 구간을 집계하는 수식일 때만 요약으로
     * 읽는다. 하나라도 아니면 평범한 데이터 줄이다 — 느슨하게 보면 남의 파일의 마지막 줄을
     * 삼켜 행 하나가 사라진다.
     */
    private Map<Integer, String> detectSummaryRow(List<List<DatasetSheetReader.RawCell>> rows,
                                                  int columnCount, FormulaA1Reader probe) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<DatasetSheetReader.RawCell> last = rows.get(rows.size() - 1);
        Map<Integer, String> found = new LinkedHashMap<>();
        for (int c = 0; c < columnCount; c++) {
            DatasetSheetReader.RawCell cell = c < last.size() ? last.get(c) : null;
            if (cell == null || (cell.formulaA1() == null && cell.value().isEmpty())) {
                continue;
            }
            Optional<String> func = cell.formulaA1() == null
                    ? Optional.empty()
                    : probe.summaryFunction(cell.formulaA1(), c, SUMMARY_FUNCTIONS);
            if (func.isEmpty()) {
                return Map.of();
            }
            found.put(c, func.get());
        }
        return found;
    }

    private List<DatasetColumn> buildColumns(DatasetSheetReader.RawSheet sheet,
                                             boolean firstRowAsHeader, int columnCount,
                                             Map<Integer, String> summaries) {
        List<String> header = firstRowAsHeader && !sheet.rows().isEmpty()
                ? sheet.rows().get(0).stream().map(DatasetSheetReader.RawCell::value).toList()
                : List.of();
        List<DatasetColumn> columns = new ArrayList<>(columnCount);
        for (int c = 0; c < columnCount; c++) {
            String label = c < header.size() && !header.get(c).isBlank()
                    ? header.get(c) : "열 " + (c + 1);
            // 열 key는 부르는 쪽이 정한다 — 생성 API가 발급하지 않는다(표 삽입·이관도 같다).
            columns.add(new DatasetColumn(
                    "c" + c, label, clampWidth(sheet.widths().get(c)), null,
                    summaries.get(c), null, null));
        }
        return columns;
    }

    /** 우리 폭 범위 밖은 버린다 — 잘라 맞추면 파일에 없던 폭을 새로 정하는 셈이다. */
    private Integer clampWidth(Integer width) {
        if (width == null || width < DatasetColumn.MIN_WIDTH || width > DatasetColumn.MAX_WIDTH) {
            return null;
        }
        return width;
    }

    private void writeValues(Long memberId, Long datasetId,
                             List<List<DatasetSheetReader.RawCell>> dataRows,
                             List<DatasetColumn> columns) {
        if (dataRows.isEmpty()) {
            return;
        }
        List<List<String>> values = dataRows.stream()
                .map(row -> {
                    List<String> cells = new ArrayList<>(columns.size());
                    for (int c = 0; c < columns.size(); c++) {
                        cells.add(c < row.size() ? row.get(c).value() : "");
                    }
                    return cells;
                })
                .toList();
        datasetService.bulkAppend(memberId, datasetId, new BulkRowsRequest(values));
    }

    /**
     * 2패스의 두 번째 — 이제 행 id가 있으니 A1을 우리 주소로 옮긴다.
     *
     * @return 살려서 들인 수식 개수
     */
    private int writeFormulas(Long datasetId, List<List<DatasetSheetReader.RawCell>> dataRows,
                              List<DatasetRow> rows, List<DatasetColumn> columns,
                              int headerOffset) {
        Map<Integer, String> columnKey = new LinkedHashMap<>();
        for (int c = 0; c < columns.size(); c++) {
            columnKey.put(c, columns.get(c).key());
        }
        Map<Integer, Long> rowIdByNumber = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            rowIdByNumber.put(headerOffset + 1 + i, rows.get(i).getId());
        }
        int lastDataRow = headerOffset + rows.size();
        FormulaA1Reader a1 = new FormulaA1Reader(new FormulaA1Reader.Layout(
                columnKey, rowIdByNumber, headerOffset + 1, lastDataRow));

        int imported = 0;
        for (int i = 0; i < rows.size() && i < dataRows.size(); i++) {
            List<DatasetSheetReader.RawCell> source = dataRows.get(i);
            DatasetRow row = rows.get(i);
            int sheetRowNumber = headerOffset + 1 + i;
            Map<String, String> rowCells = new LinkedHashMap<>();
            for (int c = 0; c < columns.size(); c++) {
                rowCells.put(columns.get(c).key(),
                        c < source.size() ? source.get(c).value() : "");
            }
            for (int c = 0; c < columns.size() && c < source.size(); c++) {
                String formulaA1 = source.get(c).formulaA1();
                if (formulaA1 == null) {
                    continue;
                }
                Optional<String> storedForm = a1.toStored(formulaA1, sheetRowNumber);
                if (storedForm.isEmpty()) {
                    continue;
                }
                FormulaNode node = formulaService.parseStoredOrNull(
                        datasetId, storedForm.get(), columns);
                if (node == null) {
                    continue;
                }
                String value = formulaService.saveNode(
                        datasetId, row, columns.get(c).key(), node, rowCells);
                rowCells.put(columns.get(c).key(), value);
                imported++;
            }
            row.updateCells(DatasetCells.serialize(rowCells));
        }
        return imported;
    }

    private int countFormulas(List<List<DatasetSheetReader.RawCell>> dataRows) {
        return (int) dataRows.stream()
                .flatMap(List::stream)
                .filter(cell -> cell.formulaA1() != null)
                .count();
    }

    private void writeStyles(Long datasetId, List<List<DatasetSheetReader.RawCell>> dataRows,
                             List<DatasetRow> rows, List<DatasetColumn> columns) {
        for (int i = 0; i < rows.size() && i < dataRows.size(); i++) {
            List<DatasetSheetReader.RawCell> source = dataRows.get(i);
            for (int c = 0; c < columns.size() && c < source.size(); c++) {
                DatasetSheetReader.RawCell cell = source.get(c);
                if (cell.bg() == null && cell.align() == null && cell.valign() == null) {
                    continue;
                }
                styleService.setStyle(datasetId, rows.get(i).getId(), columns.get(c).key(),
                        cell.bg(), cell.align(), cell.valign());
            }
        }
    }

    /**
     * 병합. 머리글 줄에 걸린 병합은 버린다 — 우리 표의 머리글은 셀이 아니라 열 이름이라
     * 병합이라는 개념이 닿지 않는다.
     */
    private void writeMerges(Long datasetId, DatasetSheetReader.RawSheet sheet,
                             List<DatasetRow> rows, List<DatasetColumn> columns,
                             int headerOffset) {
        for (DatasetSheetReader.RawMerge merge : sheet.merges()) {
            int rowIndex = merge.rowIndex() - headerOffset;
            if (rowIndex < 0 || rowIndex >= rows.size() || merge.colIndex() >= columns.size()) {
                continue;
            }
            if (rowIndex + merge.rowSpan() > rows.size()
                    || merge.colIndex() + merge.colSpan() > columns.size()) {
                continue;
            }
            try {
                mergeService.setMerge(datasetId, rows.get(rowIndex),
                        columns.get(merge.colIndex()).key(),
                        merge.rowSpan(), merge.colSpan(), columns, rows.size());
            } catch (CustomException e) {
                // 겹치는 병합 등 우리 규칙에 안 맞는 것은 건너뛴다. 표가 안 들어오는 것보다 낫다.
                continue;
            }
        }
    }

    /** 파일에 든 시트 하나의 겉모습. 고르기 전에 화면이 보여줄 만큼만 담는다. */
    public record SheetSummary(String name, int rowCount, int columnCount,
                               List<List<String>> preview) {
    }

    /**
     * 들인 결과.
     *
     * @param formulasAsValue 옮기지 못해 값으로 들어간 수식의 수. <b>0이 아니면 화면이 말한다</b> —
     *                        조용히 값으로 바꾸면 사람은 수식이 들어온 줄 안다
     */
    public record ImportResult(Long datasetId, int rowCount, int columnCount,
                               int formulasImported, int formulasAsValue) {
    }
}
