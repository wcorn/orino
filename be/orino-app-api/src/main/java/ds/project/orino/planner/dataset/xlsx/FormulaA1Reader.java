package ds.project.orino.planner.dataset.xlsx;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 엑셀 A1 수식을 우리 <b>저장형</b>으로 옮긴다. {@link FormulaA1Writer}의 쌍이다.
 *
 * <p><b>문법을 다시 만들지 않는다.</b> 여기서 하는 일은 주소를 바꿔 끼우는 것뿐이고
 * ({@code B3} → {@code {c1}}), 괄호·우선순위·인자 개수·허용 함수는 이미 있는
 * {@code FormulaParser}가 저장형을 읽으며 본다. 문법을 두 벌로 두면 한쪽만 고쳐지는 날이 온다.
 *
 * <p>그래서 결과는 트리가 아니라 <b>저장형 문자열</b>이다 — 부르는 쪽이 파서에 넘긴다.
 *
 * <p>옮길 수 없는 수식은 {@link Optional#empty()}다. 그런 셀은 <b>값으로</b> 들어간다 —
 * 모르는 것을 아는 척 옮기면 파일과 다른 뜻이 조용히 자리 잡는다.
 */
public final class FormulaA1Reader {

    /**
     * 시트 위의 자리. {@link FormulaA1Writer.Layout}의 반대 방향이다.
     *
     * @param columnKey    0-base 열 번호 → 열 key
     * @param rowIdByNumber 1-base 시트 행 번호 → 행 id
     * @param firstDataRow 데이터가 시작하는 행 번호
     * @param lastDataRow  데이터가 끝나는 행 번호
     */
    public record Layout(
            Map<Integer, String> columnKey,
            Map<Integer, Long> rowIdByNumber,
            int firstDataRow,
            int lastDataRow
    ) {
    }

    /** {@code $A$1} 꼴 한 칸. 앞뒤로 글자가 붙어 있으면(=함수 이름 일부) 안 잡는다. */
    private static final Pattern CELL = Pattern.compile("\\$?([A-Z]{1,3})\\$?([0-9]{1,7})");

    private final Layout layout;

    public FormulaA1Reader(Layout layout) {
        this.layout = layout;
    }

    /**
     * 한 셀의 A1 수식을 저장형으로. 앞의 {@code =}는 붙여서 돌려준다 — 저장형이 그렇다.
     *
     * @param rowNumber 이 수식이 놓인 시트 행 번호. 이 행을 가리키면 「같은 행」이 된다
     */
    public Optional<String> toStored(String a1, int rowNumber) {
        if (a1 == null || a1.isBlank()) {
            return Optional.empty();
        }
        // 다른 시트 참조는 우리 표 하나로 담을 수 없다. 통째로 값으로 보낸다.
        if (a1.indexOf('!') >= 0) {
            return Optional.empty();
        }
        try {
            return Optional.of("=" + rewrite(a1, rowNumber));
        } catch (Untranslatable e) {
            return Optional.empty();
        }
    }

    /**
     * 문자열 리터럴은 건드리지 않고, 그 밖에서만 주소·함수 이름을 바꾼다.
     *
     * <p>리터럴을 안 가리면 {@code "B3 참고"} 같은 글자가 참조로 바뀐다.
     */
    private String rewrite(String a1, int rowNumber) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < a1.length()) {
            char c = a1.charAt(i);
            if (c == '"') {
                i = copyLiteral(a1, i, out);
                continue;
            }
            int consumed = tryAddress(a1, i, rowNumber, out);
            if (consumed > 0) {
                i += consumed;
                continue;
            }
            consumed = tryFunctionName(a1, i, out);
            if (consumed > 0) {
                i += consumed;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** 여는 따옴표부터 닫는 따옴표까지 그대로 옮긴다. {@code ""}는 escape라 안 닫힌다. */
    private int copyLiteral(String a1, int start, StringBuilder out) {
        out.append('"');
        int i = start + 1;
        while (i < a1.length()) {
            char c = a1.charAt(i);
            out.append(c);
            i++;
            if (c == '"') {
                if (i < a1.length() && a1.charAt(i) == '"') {
                    out.append('"');
                    i++;
                    continue;
                }
                return i;
            }
        }
        // 안 닫힌 따옴표. 파서가 볼 문제이므로 여기서 판단하지 않는다.
        return i;
    }

    /**
     * 이 자리에서 시작하는 주소(범위 또는 한 칸)를 저장형으로 바꾼다.
     *
     * @return 먹은 글자 수. 주소가 아니면 0
     */
    private int tryAddress(String a1, int at, int rowNumber, StringBuilder out) {
        if (at > 0 && isIdentifierChar(a1.charAt(at - 1))) {
            // LOG10( 의 G10 같은 것. 이름 한복판은 주소가 아니다.
            return 0;
        }
        Matcher m = CELL.matcher(a1);
        if (!m.find(at) || m.start() != at) {
            return 0;
        }
        // 뒤에 여는 괄호가 붙으면 주소가 아니라 함수 이름이다({@code LOG10(} 의 LOG10).
        // 못 옮긴다는 뜻이 아니므로 통째로 포기하지 않고, 글자 그대로 흘려보낸다.
        int afterFirst = m.end();
        if (afterFirst < a1.length() && a1.charAt(afterFirst) == '(') {
            return 0;
        }

        // 범위인가 — A$2:A$4.
        if (afterFirst < a1.length() && a1.charAt(afterFirst) == ':') {
            Matcher second = CELL.matcher(a1);
            if (second.find(afterFirst + 1) && second.start() == afterFirst + 1) {
                out.append(columnRange(m.group(1), m.group(2), second.group(1), second.group(2)));
                return second.end() - at;
            }
            throw new Untranslatable();
        }

        out.append(cellRef(m.group(1), m.group(2), rowNumber));
        return afterFirst - at;
    }

    /**
     * 열 전체 집계만 우리 모델에 있다. 부분 범위({@code SUM(B2:B10)})는 담을 그릇이 없어
     * 옮기지 않는다 — 억지로 열 전체로 넓히면 파일과 다른 값이 나온다.
     */
    private String columnRange(String col1, String row1, String col2, String row2) {
        if (!col1.equals(col2)) {
            throw new Untranslatable();
        }
        if (Integer.parseInt(row1) != layout.firstDataRow()
                || Integer.parseInt(row2) != layout.lastDataRow()) {
            throw new Untranslatable();
        }
        return "{" + columnKey(col1) + "}";
    }

    private String cellRef(String col, String row, int rowNumber) {
        String key = columnKey(col);
        int target = Integer.parseInt(row);
        if (target == rowNumber) {
            // 자기 행을 가리키면 같은 행 참조다 — 행이 밀려도 안 깨지는 쪽으로 들어간다.
            return "{" + key + "}";
        }
        Long rowId = layout.rowIdByNumber().get(target);
        if (rowId == null) {
            // 머리글·요약줄처럼 데이터가 아닌 행을 가리킨다. 담을 자리가 없다.
            throw new Untranslatable();
        }
        return "{" + key + "}@" + rowId;
    }

    private String columnKey(String letters) {
        String key = layout.columnKey().get(toIndex(letters));
        if (key == null) {
            throw new Untranslatable();
        }
        return key;
    }

    /** 엑셀 이름 → 우리 이름. 내보낼 때의 반대이고, 지금도 이것 하나뿐이다. */
    private static final Map<String, String> FUNC_ALIASES = Map.of("AVERAGE", "AVG");

    private int tryFunctionName(String a1, int at, StringBuilder out) {
        if (at > 0 && isIdentifierChar(a1.charAt(at - 1))) {
            return 0;
        }
        for (Map.Entry<String, String> alias : FUNC_ALIASES.entrySet()) {
            String from = alias.getKey();
            if (a1.startsWith(from, at)
                    && at + from.length() < a1.length()
                    && a1.charAt(at + from.length()) == '(') {
                out.append(alias.getValue());
                return from.length();
            }
        }
        return 0;
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '$';
    }

    /** A → 0, Z → 25, AA → 26. {@link FormulaA1Writer#toLetters(int)}의 반대. */
    static int toIndex(String letters) {
        int n = 0;
        for (int i = 0; i < letters.length(); i++) {
            n = n * 26 + (letters.charAt(i) - 'A' + 1);
        }
        return n - 1;
    }

    /**
     * 요약줄인지 본다. 내보낼 때 마지막 줄에 {@code SUM(B2:B3)}처럼 넣은 그 줄이다.
     *
     * <p>열 문자가 <b>자기 열</b>과 같고 범위가 데이터 구간과 정확히 맞을 때만 요약으로 읽는다 —
     * 그보다 느슨하면 남의 파일의 평범한 수식 줄을 요약으로 잘못 삼킨다.
     *
     * @return 요약 함수 이름, 또는 요약이 아니면 {@link Optional#empty()}
     */
    public Optional<String> summaryFunction(String a1, int columnIndex, List<String> allowed) {
        if (a1 == null) {
            return Optional.empty();
        }
        String letter = FormulaA1Writer.toLetters(columnIndex);
        Matcher m = Pattern.compile(
                        "^([A-Z]+)\\(\\$?" + letter + "\\$?(\\d+):\\$?" + letter + "\\$?(\\d+)\\)$")
                .matcher(a1.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        if (Integer.parseInt(m.group(2)) != layout.firstDataRow()
                || Integer.parseInt(m.group(3)) != layout.lastDataRow()) {
            return Optional.empty();
        }
        String func = m.group(1);
        return allowed.contains(func) ? Optional.of(func) : Optional.empty();
    }

    /** 옮길 수 없는 수식을 만났다는 신호. 그 셀은 값으로 들어간다. */
    private static final class Untranslatable extends RuntimeException {
        Untranslatable() {
            super(null, null, false, false);
        }
    }
}
