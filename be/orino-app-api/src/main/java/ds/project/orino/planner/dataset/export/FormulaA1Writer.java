package ds.project.orino.planner.dataset.export;

import ds.project.orino.domain.planner.dataset.entity.FormulaRefKind;
import ds.project.orino.planner.dataset.formula.FormulaNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 수식 트리를 엑셀 A1 문자열로 옮긴다.
 *
 * <p><b>이 클래스가 ADR-1이 말한 「경계」다.</b> orino 내부 수식은 열 key와 행 id로 주소를
 * 잡는다 — 열 이름을 바꾸거나 행이 밀려도 안 깨지는 것이 그 모델의 전부다. 엑셀 A1은 격자
 * 좌표라 그 내성을 잃으므로, 내부에는 절대 들이지 않고 <b>파일로 나갈 때만</b> 번역한다.
 * 그래서 {@code FormulaWriter}(저장형·표시형)에 얹지 않고 export 패키지에 따로 둔다 —
 * 코드가 놓인 자리가 곧 「여기서만」이라는 선언이다.
 *
 * <p>번역에 필요한 것은 맵 둘뿐이다: 열 key가 몇 번째 열인가, 행 id가 시트 몇 번째 행인가.
 */
public final class FormulaA1Writer {

    /**
     * 시트 위의 자리.
     *
     * @param columnIndex 열 key → 0-base 열 번호({@code columns_json} 순서)
     * @param rowNumber   행 id → 1-base 시트 행 번호(헤더가 1행이므로 데이터는 2행부터)
     * @param firstDataRow 데이터가 시작하는 행 번호. 열 전체 집계의 범위가 여기서 시작한다
     * @param lastDataRow  데이터가 끝나는 행 번호
     */
    public record Layout(
            Map<String, Integer> columnIndex,
            Map<Long, Integer> rowNumber,
            int firstDataRow,
            int lastDataRow
    ) {
    }

    private final Layout layout;

    /**
     * 표간 참조를 대신할 값. 한 표를 한 시트로 내보내므로 다른 표를 가리킬 자리가 없다 —
     * 수식으로 쓰면 엑셀에서 {@code #REF!}가 되므로 <b>계산된 값으로 굳힌다</b>.
     */
    private final java.util.function.Supplier<String> frozenCrossValue;

    public FormulaA1Writer(Layout layout, java.util.function.Supplier<String> frozenCrossValue) {
        this.layout = layout;
        this.frozenCrossValue = frozenCrossValue;
    }

    /**
     * 한 셀의 수식을 A1로. 앞의 {@code =}는 붙이지 않는다 — POI가 붙인다.
     *
     * @param rowNumber 이 수식이 놓인 행의 시트 행 번호. 같은 행 참조가 이 값을 쓴다
     * @return 번역된 수식, 또는 번역할 수 없으면 {@code null}(그 셀은 값으로 내보낸다)
     */
    public String write(FormulaNode node, int rowNumber) {
        StringBuilder out = new StringBuilder();
        try {
            emit(node, rowNumber, out);
        } catch (Untranslatable e) {
            return null;
        }
        return out.toString();
    }

    private void emit(FormulaNode node, int row, StringBuilder out) {
        switch (node) {
            case FormulaNode.Num n -> out.append(n.value().toPlainString());
            // 엑셀 문자열 리터럴은 큰따옴표 두 번으로 escape한다.
            case FormulaNode.Str s -> out.append('"')
                    .append(s.value().replace("\"", "\"\""))
                    .append('"');
            case FormulaNode.Unary u -> {
                out.append(u.op());
                emit(u.operand(), row, out);
            }
            case FormulaNode.Binary b -> {
                // 괄호를 항상 씌운다. 우리 트리는 우선순위를 이미 반영해 묶여 있으므로,
                // 괄호를 아끼려 들면 그 우선순위를 여기서 다시 판단해야 한다.
                out.append('(');
                emit(b.left(), row, out);
                out.append(' ').append(b.op()).append(' ');
                emit(b.right(), row, out);
                out.append(')');
            }
            case FormulaNode.Compare c -> {
                out.append('(');
                emit(c.left(), row, out);
                out.append(' ').append(c.op()).append(' ');
                emit(c.right(), row, out);
                out.append(')');
            }
            case FormulaNode.Ref r -> out.append(ref(r, row));
            case FormulaNode.Agg a -> {
                out.append(excelFunc(a.func())).append('(');
                out.append(String.join(", ", columnRanges(a.colKeys())));
                out.append(')');
            }
            case FormulaNode.AggIf a -> emitAggIf(a, row, out);
            case FormulaNode.Call c -> {
                out.append(c.func()).append('(');
                for (int i = 0; i < c.args().size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    emit(c.args().get(i), row, out);
                }
                out.append(')');
            }
            // 표간 집계도 가리킬 시트가 없다 — 값으로 굳힌다.
            case FormulaNode.CrossAgg ignored -> out.append(frozen());
        }
    }

    private void emitAggIf(FormulaNode.AggIf a, int row, StringBuilder out) {
        out.append(a.func()).append('(');
        out.append(columnRange(a.critCol())).append(", ");
        emit(a.criteria(), row, out);
        if (a.sumCol() != null) {
            out.append(", ").append(columnRange(a.sumCol()));
        }
        out.append(')');
    }

    /**
     * 셀 참조.
     *
     * <p>참조 종류가 곧 엑셀의 상대/절대다 — 같은 행은 수식이 놓인 행을 따라 움직이므로
     * 상대({@code B5}), 특정 행에 묶인 것은 따라 움직이면 안 되므로 행을 고정한다({@code B$5}).
     * 열은 우리 모델에서 언제나 고정이라 열 문자에는 {@code $}를 붙이지 않는다 — 사람이
     * 파일에서 수식을 옆으로 끌어 복사할 때 열이 따라가는 편이 엑셀다운 동작이다.
     */
    private String ref(FormulaNode.Ref r, int row) {
        if (r.datasetId() != null) {
            return frozen();
        }
        String col = columnLetter(r.colKey());
        if (r.kind() == FormulaRefKind.SAME_ROW) {
            return col + row;
        }
        Integer target = layout.rowNumber().get(r.rowId());
        if (target == null) {
            // 가리키던 행이 사라졌다. 엑셀에도 그대로 #REF!가 있으니 그 말을 그대로 쓴다.
            return "#REF!";
        }
        return col + "$" + target;
    }

    private List<String> columnRanges(List<String> colKeys) {
        List<String> ranges = new ArrayList<>();
        for (String key : colKeys) {
            ranges.add(columnRange(key));
        }
        return ranges;
    }

    /** 열 전체 집계의 범위. 헤더와 푸터를 뺀 데이터 구간이고, 행은 고정한다. */
    private String columnRange(String colKey) {
        String col = columnLetter(colKey);
        return col + "$" + layout.firstDataRow() + ":" + col + "$" + layout.lastDataRow();
    }

    private String columnLetter(String colKey) {
        Integer index = layout.columnIndex().get(colKey);
        if (index == null) {
            throw new Untranslatable();
        }
        return toLetters(index);
    }

    private String frozen() {
        String value = frozenCrossValue.get();
        if (value == null || value.isBlank()) {
            return "\"\"";
        }
        // 숫자면 숫자로, 아니면 문자열 리터럴로.
        try {
            new java.math.BigDecimal(value.trim());
            return value.trim();
        } catch (NumberFormatException e) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
    }

    /** 내부 이름과 엑셀 이름이 다른 것 하나. 나머지는 그대로 통한다. */
    private static String excelFunc(String func) {
        return "AVG".equals(func) ? "AVERAGE" : func;
    }

    /** 0 → A, 25 → Z, 26 → AA. */
    static String toLetters(int index) {
        StringBuilder out = new StringBuilder();
        int n = index;
        while (n >= 0) {
            out.insert(0, (char) ('A' + (n % 26)));
            n = n / 26 - 1;
        }
        return out.toString();
    }

    /** 번역할 수 없는 수식을 만났다는 신호. 그 셀은 값으로 내보낸다. */
    private static final class Untranslatable extends RuntimeException {
        Untranslatable() {
            super(null, null, false, false);
        }
    }
}
