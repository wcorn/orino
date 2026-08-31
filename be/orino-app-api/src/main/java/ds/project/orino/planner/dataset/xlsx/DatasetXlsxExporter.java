package ds.project.orino.planner.dataset.xlsx;

import ds.project.orino.planner.dataset.dto.CellStyle;
import ds.project.orino.planner.dataset.dto.DatasetColumn;
import ds.project.orino.planner.dataset.formula.FormulaNode;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 표 하나를 .xlsx 한 장으로.
 *
 * <p><b>화면에 보이는 것을 담는다</b> — 값·수식·서식·병합·열 너비·푸터 요약. 값만 굳혀 내보내면
 * 「엑셀에서 이어서 작업한다」가 안 되므로, 수식은 A1으로 번역해 <b>살아 있는 채로</b> 내보낸다
 * ({@link FormulaA1Writer}).
 *
 * <p>불러오기(loading)는 하지 않는다 — 받은 자료로 통합문서만 만든다. 소유 확인과 조회는
 * 서비스가 하고, 여기는 순수하게 조립만 해서 테스트가 DB 없이 돈다.
 */
@Component
public class DatasetXlsxExporter {

    /** 헤더가 1행이므로 데이터는 2행부터. */
    private static final int FIRST_DATA_ROW = 2;

    /**
     * 셀 배경 팔레트.
     *
     * <p>화면은 {@code index.css}의 {@code --cell-bg-*}(oklch)를 쓰고 라이트/다크가 갈리는데,
     * xlsx에는 테마가 없다. <b>라이트 값을 sRGB로 환산해 굳힌다</b> — 눈으로 고른 근사색을 박으면
     * 화면과 파일 색이 달라지고, 그러면 사람이 둘을 오가며 맞는지 확인해야 한다.
     */
    private static final Map<String, byte[]> BG_RGB = Map.of(
            "red", new byte[]{(byte) 0xFF, (byte) 0xE2, (byte) 0xDE},
            "orange", new byte[]{(byte) 0xFF, (byte) 0xE8, (byte) 0xD1},
            "yellow", new byte[]{(byte) 0xFC, (byte) 0xF2, (byte) 0xCD},
            "green", new byte[]{(byte) 0xD9, (byte) 0xF3, (byte) 0xDD},
            "blue", new byte[]{(byte) 0xD7, (byte) 0xEF, (byte) 0xFF},
            "purple", new byte[]{(byte) 0xEF, (byte) 0xE6, (byte) 0xFF});

    /**
     * 내보낼 한 행.
     *
     * @param rowId    행 정체성. 절대 참조가 이 값으로 자리를 찾는다
     * @param cells    열 key → 계산된 값(문자열). 비어 있는 셀은 없어도 된다
     * @param formulas 열 key → 수식 트리. 수식이 있는 셀만
     * @param styles   열 key → 서식
     */
    public record SheetRow(
            Long rowId,
            Map<String, String> cells,
            Map<String, FormulaNode> formulas,
            Map<String, CellStyle> styles
    ) {
    }

    /** 병합 한 칸. 앵커 자리와 뻗는 칸 수. */
    public record SheetMerge(int rowIndex, String colKey, int rowSpan, int colSpan) {
    }

    public byte[] export(String datasetName, List<DatasetColumn> columns,
                         List<SheetRow> rows, List<SheetMerge> merges) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(safeSheetName(datasetName));

            Map<String, Integer> columnIndex = new HashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                columnIndex.put(columns.get(i).key(), i);
            }
            Map<Long, Integer> rowNumber = new HashMap<>();
            for (int i = 0; i < rows.size(); i++) {
                rowNumber.put(rows.get(i).rowId(), FIRST_DATA_ROW + i);
            }
            int lastDataRow = rows.isEmpty() ? FIRST_DATA_ROW : FIRST_DATA_ROW + rows.size() - 1;

            StyleCache styles = new StyleCache(wb);
            writeHeader(sheet, columns, styles);
            writeRows(sheet, columns, rows, columnIndex, rowNumber, lastDataRow, styles);
            writeSummary(sheet, columns, rows.size(), lastDataRow, styles);
            applyMerges(sheet, merges, columnIndex);
            applyWidths(sheet, columns);

            // 수식을 그대로 써 넣었으므로 캐시된 값이 없다. 열었을 때 바로 계산되게 한다 —
            // 안 켜면 엑셀이 빈 칸을 보여주고, 사람은 내보내기가 깨졌다고 읽는다.
            wb.setForceFormulaRecalculation(true);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeHeader(Sheet sheet, List<DatasetColumn> columns, StyleCache styles) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns.get(i).label());
            cell.setCellStyle(styles.header());
        }
    }

    private void writeRows(Sheet sheet, List<DatasetColumn> columns, List<SheetRow> rows,
                           Map<String, Integer> columnIndex, Map<Long, Integer> rowNumber,
                           int lastDataRow, StyleCache styles) {
        FormulaA1Writer.Layout layout = new FormulaA1Writer.Layout(
                columnIndex, rowNumber, FIRST_DATA_ROW, lastDataRow);

        for (int r = 0; r < rows.size(); r++) {
            SheetRow source = rows.get(r);
            Row row = sheet.createRow(FIRST_DATA_ROW - 1 + r);
            int sheetRowNumber = FIRST_DATA_ROW + r;

            for (int c = 0; c < columns.size(); c++) {
                String colKey = columns.get(c).key();
                Cell cell = row.createCell(c);
                String value = source.cells().get(colKey);

                FormulaNode formula = source.formulas().get(colKey);
                String a1 = null;
                if (formula != null) {
                    // 표간 참조는 가리킬 시트가 없어 계산된 값으로 굳힌다.
                    FormulaA1Writer writer = new FormulaA1Writer(layout, () -> value);
                    a1 = writer.write(formula, sheetRowNumber);
                }
                if (a1 != null) {
                    cell.setCellFormula(a1);
                } else {
                    writeValue(cell, value);
                }

                CellStyle style = source.styles().get(colKey);
                if (style != null) {
                    cell.setCellStyle(styles.forCell(style));
                }
            }
        }
    }

    /**
     * 푸터 요약. 화면 아래에 보이는 값을 <b>엑셀 수식으로</b> 내보낸다 — 숫자로 굳히면
     * 파일에서 행을 고쳤을 때 요약만 옛 값으로 남는다.
     */
    private void writeSummary(Sheet sheet, List<DatasetColumn> columns, int rowCount,
                              int lastDataRow, StyleCache styles) {
        boolean any = columns.stream().anyMatch(c -> c.summary() != null);
        if (!any || rowCount == 0) {
            return;
        }
        Row row = sheet.createRow(FIRST_DATA_ROW - 1 + rowCount);
        for (int c = 0; c < columns.size(); c++) {
            String summary = columns.get(c).summary();
            if (summary == null) {
                continue;
            }
            String letter = FormulaA1Writer.toLetters(c);
            Cell cell = row.createCell(c);
            cell.setCellFormula("%s(%s%d:%s%d)"
                    .formatted(summary, letter, FIRST_DATA_ROW, letter, lastDataRow));
            cell.setCellStyle(styles.summary());
        }
    }

    private void applyMerges(Sheet sheet, List<SheetMerge> merges,
                             Map<String, Integer> columnIndex) {
        for (SheetMerge merge : merges) {
            Integer col = columnIndex.get(merge.colKey());
            if (col == null || (merge.rowSpan() <= 1 && merge.colSpan() <= 1)) {
                continue;
            }
            int first = FIRST_DATA_ROW - 1 + merge.rowIndex();
            sheet.addMergedRegion(new CellRangeAddress(
                    first, first + Math.max(merge.rowSpan(), 1) - 1,
                    col, col + Math.max(merge.colSpan(), 1) - 1));
        }
    }

    /**
     * 열 너비. 우리는 px, POI는 1/256 문자폭이다. 기본 폰트에서 한 글자가 대략 7px이라
     * 그 비율로 옮긴다 — 정확한 환산은 폰트 메트릭이 필요하고, 여기서 필요한 것은
     * 「화면에서 넓던 열이 파일에서도 넓다」까지다.
     */
    private void applyWidths(Sheet sheet, List<DatasetColumn> columns) {
        for (int i = 0; i < columns.size(); i++) {
            Integer width = columns.get(i).width();
            if (width == null) {
                continue;
            }
            sheet.setColumnWidth(i, Math.min(width * 256 / 7, 255 * 256));
        }
    }

    private void writeValue(Cell cell, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        // 숫자로 읽히면 숫자 셀로. 수식 엔진의 판별과 같은 규칙이라 화면과 파일이 어긋나지 않는다.
        try {
            cell.setCellValue(new BigDecimal(value.trim()).doubleValue());
        } catch (NumberFormatException e) {
            cell.setCellValue(value);
        }
    }

    /** 시트 이름 제약: 31자, {@code : \ / ? * [ ]} 금지. 비어 있으면 「표」. */
    private static String safeSheetName(String name) {
        String base = (name == null || name.isBlank()) ? "표" : name.trim();
        String cleaned = base.replaceAll("[:\\\\/?*\\[\\]]", " ");
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    /**
     * 서식은 통합문서당 개수 제한이 있어(64k) 셀마다 새로 만들면 안 된다.
     * 같은 조합이면 같은 객체를 돌려준다.
     */
    private static final class StyleCache {
        private final XSSFWorkbook wb;
        private final Map<String, XSSFCellStyle> cache = new LinkedHashMap<>();
        private XSSFCellStyle header;
        private XSSFCellStyle summary;

        StyleCache(XSSFWorkbook wb) {
            this.wb = wb;
        }

        XSSFCellStyle header() {
            if (header == null) {
                header = wb.createCellStyle();
                XSSFFont bold = wb.createFont();
                bold.setBold(true);
                header.setFont(bold);
                header.setBorderBottom(BorderStyle.THIN);
            }
            return header;
        }

        XSSFCellStyle summary() {
            if (summary == null) {
                summary = wb.createCellStyle();
                XSSFFont bold = wb.createFont();
                bold.setBold(true);
                summary.setFont(bold);
                summary.setBorderTop(BorderStyle.THIN);
            }
            return summary;
        }

        XSSFCellStyle forCell(CellStyle style) {
            String key = style.bg() + "|" + style.align() + "|" + style.valign();
            return cache.computeIfAbsent(key, k -> build(style));
        }

        private XSSFCellStyle build(CellStyle style) {
            XSSFCellStyle out = wb.createCellStyle();
            byte[] rgb = style.bg() == null ? null : BG_RGB.get(style.bg());
            if (rgb != null) {
                out.setFillForegroundColor(new XSSFColor(rgb, null));
                out.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            if (style.align() != null) {
                out.setAlignment(switch (style.align()) {
                    case "center" -> HorizontalAlignment.CENTER;
                    case "right" -> HorizontalAlignment.RIGHT;
                    default -> HorizontalAlignment.LEFT;
                });
            }
            if (style.valign() != null) {
                out.setVerticalAlignment(switch (style.valign()) {
                    case "middle" -> VerticalAlignment.CENTER;
                    case "bottom" -> VerticalAlignment.BOTTOM;
                    default -> VerticalAlignment.TOP;
                });
            }
            return out;
        }
    }
}
