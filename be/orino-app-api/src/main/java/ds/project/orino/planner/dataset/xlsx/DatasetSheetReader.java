package ds.project.orino.planner.dataset.xlsx;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTXf;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 표 파일을 <b>격자 그대로</b> 읽는다 — 값·수식(A1 문자열)·서식·병합·열 너비.
 *
 * <p>CSV도 여기서 받는다. 값밖에 없는 형식이라 서식·수식 자리는 비고, 시트도 한 장이다.
 * 원장 이관의 {@code LedgerSheetReader}와 규칙이 같지만 코드를 나눠 쓰지 않는다 — 그쪽은
 * 원장 전용 오류 코드로 말하고, 두 기능이 한 파서를 공유하면 한쪽의 사정이 다른 쪽 오류
 * 메시지를 바꾼다.
 *
 * <p>여기서는 뜻을 정하지 않는다. 어느 줄이 머리글인지, A1이 우리 주소로 어떻게 옮겨지는지는
 * 다음 단계의 일이다({@link FormulaA1Reader} · 서비스). 읽기와 해석을 섞으면 「이 파일은 왜
 * 안 되나」를 물었을 때 파일 문제인지 해석 문제인지 갈라 볼 수 없다 — 원장 이관의
 * {@code LedgerSheetReader}가 같은 이유로 그렇게 나뉘어 있다.
 *
 * <p>{@code .xls}는 받지 않는다. HSSF가 따라 들어오는데, 쓰는 사람이 없는 형식을 위해
 * 스캔 대상을 넓히지 않는다(#1268 · #1308과 같은 원칙).
 */
@Component
public class DatasetSheetReader {

    /**
     * 한 번에 읽을 줄의 상한. 표는 「한 번 옮기고 끝」이라 넉넉해야 하지만, 상한이 없으면
     * 잘못 고른 파일 하나가 힙을 통째로 먹는다. 넘으면 <b>거절하고 말한다</b> —
     * 조용히 앞부분만 넣으면 사람은 전부 들어갔다고 믿는다.
     */
    static final int MAX_ROWS = 20_000;

    /** 열 상한은 표가 이미 정한 것을 따른다({@code DatasetService.MAX_COLUMNS}). */
    static final int MAX_COLUMNS = 100;

    /** 화면 팔레트의 sRGB → 토큰. 내보낼 때 쓴 값의 반대 표다. */
    private static final Map<String, String> BG_TOKEN = Map.of(
            "FFE2DE", "red",
            "FFE8D1", "orange",
            "FCF2CD", "yellow",
            "D9F3DD", "green",
            "D7EFFF", "blue",
            "EFE6FF", "purple");

    /** 셀 하나. {@code formulaA1}은 수식이 있을 때만. */
    public record RawCell(String value, String formulaA1, String bg, String align, String valign) {
        static final RawCell EMPTY = new RawCell("", null, null, null, null);
    }

    /** 병합 한 칸. 시트 기준 0-base. */
    public record RawMerge(int rowIndex, int colIndex, int rowSpan, int colSpan) {
    }

    /**
     * 시트 한 장.
     *
     * @param widths 0-base 열 번호 → px. 명시된 열만 담긴다
     */
    public record RawSheet(
            String name,
            List<List<RawCell>> rows,
            List<RawMerge> merges,
            Map<Integer, Integer> widths
    ) {
        public int columnCount() {
            return rows.stream().mapToInt(List::size).max().orElse(0);
        }
    }

    /** 화면에 보이는 대로 읽는다 — 날짜 서식이 걸린 셀이 42736 같은 수로 나오지 않게. */
    private final DataFormatter formatter = new DataFormatter();

    public List<RawSheet> read(MultipartFile file) {
        String name = file.getOriginalFilename() == null
                ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv") || name.endsWith(".txt")) {
            return List.of(readCsv(file));
        }
        if (!name.endsWith(".xlsx")) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        try (InputStream in = file.getInputStream(); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            // 수식 값은 캐시가 없을 수 있다(우리가 내보낸 파일이 그렇다). 값이 필요한 건
            // 옮기지 못한 수식뿐이라, 계산은 그때그때 해 보고 안 되면 비운다.
            org.apache.poi.ss.usermodel.FormulaEvaluator evaluator =
                    wb.getCreationHelper().createFormulaEvaluator();
            List<RawSheet> sheets = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                sheets.add(readSheet(wb.getSheetAt(i), evaluator, wb.getStylesSource()));
            }
            return sheets;
        } catch (IOException | RuntimeException e) {
            if (e instanceof CustomException custom) {
                throw custom;
            }
            throw new CustomException(ErrorCode.INVALID_REQUEST, e);
        }
    }

    /**
     * CSV. 시트가 없는 형식이라 한 장으로 읽고, 이름은 파일 이름을 쓴다.
     *
     * <p>따옴표 안의 쉼표와 두 겹 따옴표({@code ""})만 다룬다 — 사람들이 실제로 내보내는
     * 범위다. 그 밖의 방언까지 흉내 내려면 라이브러리를 하나 들여야 하고, 그건 이 기능이
     * 감당할 무게가 아니다.
     */
    private RawSheet readCsv(MultipartFile file) {
        try (InputStream in = file.getInputStream();
             BufferedReader lines = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<List<RawCell>> rows = new ArrayList<>();
            String line;
            boolean first = true;
            while ((line = lines.readLine()) != null) {
                if (first) {
                    // 엑셀이 UTF-8 CSV 앞에 붙이는 BOM. 남겨 두면 첫 열 이름이 안 맞는다.
                    line = line.replace("\uFEFF", "");
                    first = false;
                }
                rows.add(splitCsv(line).stream()
                        .map(value -> new RawCell(value, null, null, null, null))
                        .toList());
                if (rows.size() > MAX_ROWS) {
                    throw new CustomException(ErrorCode.INVALID_REQUEST);
                }
            }
            String name = file.getOriginalFilename() == null ? "표" : file.getOriginalFilename();
            return new RawSheet(name.replaceFirst("\\.[^.]+$", ""), rows, List.of(), Map.of());
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, e);
        }
    }

    private List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString().trim());
        return cells;
    }

    private RawSheet readSheet(XSSFSheet sheet,
                               org.apache.poi.ss.usermodel.FormulaEvaluator evaluator,
                               StylesTable styles) {
        int lastRow = sheet.getLastRowNum();
        if (lastRow + 1 > MAX_ROWS) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        List<List<RawCell>> rows = new ArrayList<>();
        int width = 0;
        for (int r = 0; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                rows.add(List.of());
                continue;
            }
            int last = Math.min(row.getLastCellNum(), MAX_COLUMNS);
            List<RawCell> cells = new ArrayList<>(Math.max(last, 0));
            for (int c = 0; c < last; c++) {
                cells.add(readCell(row.getCell(c), evaluator, styles));
            }
            width = Math.max(width, cells.size());
            rows.add(cells);
        }

        List<RawMerge> merges = new ArrayList<>();
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            merges.add(new RawMerge(
                    region.getFirstRow(), region.getFirstColumn(),
                    region.getLastRow() - region.getFirstRow() + 1,
                    region.getLastColumn() - region.getFirstColumn() + 1));
        }

        return new RawSheet(sheet.getSheetName(), rows, merges, readWidths(sheet, width));
    }

    private RawCell readCell(Cell cell,
                             org.apache.poi.ss.usermodel.FormulaEvaluator evaluator,
                             StylesTable styles) {
        if (cell == null) {
            return RawCell.EMPTY;
        }
        String formula = null;
        if (cell.getCellType() == CellType.FORMULA) {
            try {
                formula = cell.getCellFormula();
            } catch (RuntimeException e) {
                formula = null;
            }
        }
        String value = readValue(cell, evaluator);

        XSSFCellStyle style = cell.getCellStyle() instanceof XSSFCellStyle xssf ? xssf : null;
        return new RawCell(value, formula, background(style),
                align(style, styles), valign(style, styles));
    }

    private String readValue(Cell cell,
                             org.apache.poi.ss.usermodel.FormulaEvaluator evaluator) {
        try {
            return formatter.formatCellValue(cell, evaluator);
        } catch (RuntimeException e) {
            // 우리가 모르는 함수가 든 수식은 계산이 안 된다. 캐시된 값이라도 있으면 쓴다.
            try {
                return formatter.formatCellValue(cell);
            } catch (RuntimeException ignored) {
                return "";
            }
        }
    }

    /**
     * 배경색. <b>팔레트에 정확히 맞을 때만</b> 옮긴다 — 우리 모델엔 임의 색이 없어서,
     * 가까운 색으로 끌어다 붙이면 원본과 다른 색을 원본인 척 보여주게 된다.
     */
    private String background(XSSFCellStyle style) {
        if (style == null || style.getFillPattern() != FillPatternType.SOLID_FOREGROUND) {
            return null;
        }
        XSSFColor color = style.getFillForegroundColorColor();
        if (color == null || color.getRGB() == null) {
            return null;
        }
        byte[] rgb = color.getRGB();
        String hex = "%02X%02X%02X".formatted(rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
        return BG_TOKEN.get(hex);
    }

    /**
     * 정렬. <b>파일이 실제로 정한 경우만</b> 읽는다 — POI는 안 정한 셀에도 기본값(가로 GENERAL ·
     * 세로 BOTTOM)을 돌려주므로, 그대로 믿으면 서식이 없던 표가 「전부 아래 정렬」로 들어온다.
     */
    private String align(XSSFCellStyle style, StylesTable styles) {
        if (!hasAlignment(style, styles, true)) {
            return null;
        }
        return switch (style.getAlignment()) {
            case CENTER -> "center";
            case RIGHT -> "right";
            case LEFT -> "left";
            default -> null;
        };
    }

    private String valign(XSSFCellStyle style, StylesTable styles) {
        if (!hasAlignment(style, styles, false)) {
            return null;
        }
        return switch (style.getVerticalAlignment()) {
            case CENTER -> "middle";
            case BOTTOM -> "bottom";
            case TOP -> "top";
            default -> null;
        };
    }

    /**
     * 그 서식이 정렬을 <b>실제로 정했는지</b>를 원본 xml에서 본다.
     *
     * <p>{@code XSSFCellStyle}은 안 정한 셀에도 기본값을 돌려주므로(가로 GENERAL · 세로 BOTTOM)
     * 값만 봐서는 「정한 적 없음」과 「아래 정렬로 정함」이 구분되지 않는다. 구분하지 못하면
     * 서식이 없던 표가 전부 아래 정렬로 들어온다.
     */
    private boolean hasAlignment(XSSFCellStyle style, StylesTable styles, boolean horizontal) {
        if (style == null || styles == null) {
            return false;
        }
        CTXf xf = styles.getCellXfAt(style.getIndex());
        if (xf == null || !xf.isSetAlignment()) {
            return false;
        }
        return horizontal
                ? xf.getAlignment().isSetHorizontal()
                : xf.getAlignment().isSetVertical();
    }

    /**
     * 열 너비. POI는 1/256 문자폭, 우리는 px다 — 내보낼 때의 역환산이고, 안 정한 열은
     * 건너뛴다(기본 폭을 굳혀 넣으면 「균등 분배」였던 표가 고정 폭이 된다).
     */
    private Map<Integer, Integer> readWidths(XSSFSheet sheet, int columnCount) {
        int defaultWidth = sheet.getDefaultColumnWidth() * 256;
        Map<Integer, Integer> widths = new HashMap<>();
        for (int c = 0; c < columnCount; c++) {
            int raw = sheet.getColumnWidth(c);
            if (raw == defaultWidth) {
                continue;
            }
            widths.put(c, (int) Math.round(raw * 7.0 / 256));
        }
        return widths;
    }
}
