package ds.project.orino.planner.dataset.formula;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.dataset.entity.FormulaRefKind;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 수식 파서. 재귀 하강.
 *
 * <pre>
 * formula := '=' expr EOF
 * expr    := term (('+' | '-') term)*
 * term    := unary (('*' | '/') unary)*
 * unary   := ('-' | '+') unary | primary
 * primary := NUMBER | '(' expr ')' | agg | ref
 * agg     := FUNC '(' colRef (':' colRef | (',' colRef)*) ')'
 * ref     := '{' label '}' row?
 * row     := DIGITS          (입력형: 화면 행 번호)
 *          | '@' DIGITS      (저장형: 행 id)
 * </pre>
 *
 * <p><b>열 이름은 반드시 중괄호로 감싼다.</b> 실제 데이터의 label이 {@code 열 1}처럼 숫자로 끝나고
 * ({@code =열 15}가 "열 1의 5행"인지 "열 15"인지 모호) 공백·괄호를 품기 때문에
 * ({@code Adjusted Frequency per Million (U)}), 구분자 없이는 파싱 자체가 성립하지 않는다.
 * 중괄호 안은 통째로 이름으로 읽는다.
 *
 * <p><b>같은 표기가 문맥에 따라 다른 참조가 된다.</b> 집계 함수 안의 {@code {점수}}는 열 전체이고,
 * 산술 속 {@code {점수}}는 같은 행이다. 그래서 집계 인자는 <b>열 참조만</b> 받는다 —
 * {@code SUM({a} * 2)} 같은 걸 허용하면 "열을 2배해서 합"의 의미가 정의되지 않는다.
 */
public final class FormulaParser {

    /** 집계 함수 — 인자가 열 참조(열 전체). */
    private static final Set<String> AGGREGATES = Set.of("SUM", "AVG", "COUNT", "MIN", "MAX");

    /** 조건부 집계 — 열 인자 + criteria 식. 행별 조건으로 견준다. */
    private static final Set<String> COND_AGGREGATES = Set.of("SUMIF", "COUNTIF");

    /**
     * 스칼라 함수 — 인자가 식(셀·산술·비교). 값은 [최소, 최대] 인자 개수(arity). {@code Integer.MAX_VALUE}는 가변.
     * {@code IF}/{@code AND}/{@code OR}/{@code NOT}은 여기로 들어오되 평가는 값 타입 규칙을 따른다(#892 §13).
     */
    private static final java.util.Map<String, int[]> SCALAR_FUNCS = java.util.Map.of(
            "ABS", new int[] {1, 1},
            "IF", new int[] {3, 3},
            "AND", new int[] {1, Integer.MAX_VALUE},
            "OR", new int[] {1, Integer.MAX_VALUE},
            "NOT", new int[] {1, 1});

    private final String src;
    private final FormulaContext ctx;
    private final boolean stored;
    private int pos;

    private FormulaParser(String src, FormulaContext ctx, boolean stored) {
        this.src = src;
        this.ctx = ctx;
        this.stored = stored;
    }

    /** 사용자가 친 수식(label·행 번호) → 바인딩된 트리. */
    public static FormulaNode parseInput(String text, FormulaContext ctx) {
        return new FormulaParser(text, ctx, false).parseAll();
    }

    /** 저장된 수식(열 key·행 id) → 트리. */
    public static FormulaNode parseStored(String text, FormulaContext ctx) {
        return new FormulaParser(text, ctx, true).parseAll();
    }

    /** 트리가 참조하는 것들. 중복은 합친다. */
    public static List<FormulaNode.Ref> collectRefs(FormulaNode node) {
        List<FormulaNode.Ref> out = new ArrayList<>();
        collect(node, out);
        return List.copyOf(new LinkedHashSet<>(out));
    }

    private static void collect(FormulaNode node, List<FormulaNode.Ref> out) {
        switch (node) {
            case FormulaNode.Ref r -> out.add(r);
            case FormulaNode.Agg a -> a.colKeys()
                    .forEach(k -> out.add(new FormulaNode.Ref(FormulaRefKind.COLUMN_ALL, k, null)));
            case FormulaNode.CrossAgg a -> out.add(
                    new FormulaNode.Ref(FormulaRefKind.COLUMN_ALL, a.colKey(), null, a.datasetId()));
            case FormulaNode.AggIf a -> {
                out.add(new FormulaNode.Ref(FormulaRefKind.COLUMN_ALL, a.critCol(), null));
                if (a.sumCol() != null) {
                    out.add(new FormulaNode.Ref(FormulaRefKind.COLUMN_ALL, a.sumCol(), null));
                }
                collect(a.criteria(), out);
            }
            case FormulaNode.Binary b -> {
                collect(b.left(), out);
                collect(b.right(), out);
            }
            case FormulaNode.Unary u -> collect(u.operand(), out);
            case FormulaNode.Compare cmp -> {
                collect(cmp.left(), out);
                collect(cmp.right(), out);
            }
            case FormulaNode.Call c -> c.args().forEach(arg -> collect(arg, out));
            case FormulaNode.Num ignored -> {
            }
            case FormulaNode.Str ignored -> {
            }
        }
    }

    private FormulaNode parseAll() {
        skipSpace();
        expect('=');
        FormulaNode node = expr();
        skipSpace();
        if (pos < src.length()) {
            throw syntaxError("수식 뒤에 남은 문자가 있습니다");
        }
        return node;
    }

    /** 최상위 = 비교. 산술보다 우선순위가 낮다(엑셀과 같다). 좌결합. */
    private FormulaNode expr() {
        FormulaNode left = additive();
        while (true) {
            skipSpace();
            String op = peekCompareOp();
            if (op == null) {
                return left;
            }
            pos += op.length();
            left = new FormulaNode.Compare(op, left, additive());
        }
    }

    /** 현재 위치의 비교 연산자를 본다. {@code = <> <= >= < >} 중 하나거나 null. */
    private String peekCompareOp() {
        char c = peek();
        char n = pos + 1 < src.length() ? src.charAt(pos + 1) : '\0';
        return switch (c) {
            case '=' -> "=";
            case '<' -> n == '=' ? "<=" : n == '>' ? "<>" : "<";
            case '>' -> n == '=' ? ">=" : ">";
            default -> null;
        };
    }

    private FormulaNode additive() {
        FormulaNode left = term();
        while (true) {
            skipSpace();
            char c = peek();
            if (c != '+' && c != '-') {
                return left;
            }
            pos++;
            left = new FormulaNode.Binary(c, left, term());
        }
    }

    private FormulaNode term() {
        FormulaNode left = unary();
        while (true) {
            skipSpace();
            char c = peek();
            if (c != '*' && c != '/') {
                return left;
            }
            pos++;
            left = new FormulaNode.Binary(c, left, unary());
        }
    }

    private FormulaNode unary() {
        skipSpace();
        char c = peek();
        if (c == '-' || c == '+') {
            pos++;
            return new FormulaNode.Unary(c, unary());
        }
        return primary();
    }

    private FormulaNode primary() {
        skipSpace();
        char c = peek();
        if (c == '(') {
            pos++;
            FormulaNode inner = expr();
            skipSpace();
            expect(')');
            return inner;
        }
        if (c == '{') {
            return ref(false);
        }
        if (c == '"') {
            return string();
        }
        if (Character.isDigit(c) || c == '.') {
            return number();
        }
        if (Character.isLetter(c)) {
            return functionCall();
        }
        throw syntaxError("예상하지 못한 문자: '" + c + "'");
    }

    /** {@code "..."} — 안의 {@code ""}는 큰따옴표 하나로 읽는다(엑셀식 이스케이프). */
    private FormulaNode string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw syntaxError("문자열을 닫는 '\"'가 없습니다");
            }
            char c = src.charAt(pos++);
            if (c == '"') {
                if (peek() == '"') {
                    sb.append('"');
                    pos++;
                } else {
                    return new FormulaNode.Str(sb.toString());
                }
            } else {
                sb.append(c);
            }
        }
    }

    private FormulaNode number() {
        int start = pos;
        while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
            pos++;
        }
        try {
            return new FormulaNode.Num(new BigDecimal(src.substring(start, pos)));
        } catch (NumberFormatException e) {
            throw syntaxError("숫자를 읽을 수 없습니다: " + src.substring(start, pos));
        }
    }

    /** {@code NAME(...)} — 함수 이름을 읽고 집계/스칼라로 분기한다. */
    private FormulaNode functionCall() {
        int start = pos;
        while (pos < src.length() && Character.isLetter(src.charAt(pos))) {
            pos++;
        }
        String name = src.substring(start, pos).toUpperCase(Locale.ROOT);
        if (AGGREGATES.contains(name)) {
            return aggregate(name);
        }
        if (COND_AGGREGATES.contains(name)) {
            return conditionalAggregate(name);
        }
        if (SCALAR_FUNCS.containsKey(name)) {
            return scalar(name);
        }
        throw syntaxError("지원하지 않는 함수: " + name);
    }

    /**
     * 조건부 집계 — {@code COUNTIF(열, criteria)} · {@code SUMIF(열, criteria, 열)}.
     * 열 인자는 집계처럼 열 참조({@code ref(true)})로, criteria는 스칼라 식으로 읽는다.
     */
    private FormulaNode conditionalAggregate(String name) {
        boolean sumif = name.equals("SUMIF");
        skipSpace();
        expect('(');
        String critCol = ((FormulaNode.Ref) ref(true)).colKey();
        skipSpace();
        expect(',');
        FormulaNode criteria = expr();
        String sumCol = null;
        if (sumif) {
            skipSpace();
            expect(',');
            sumCol = ((FormulaNode.Ref) ref(true)).colKey();
        }
        skipSpace();
        expect(')');
        return new FormulaNode.AggIf(name, critCol, criteria, sumCol);
    }

    /** 스칼라 함수 — 식 인자를 콤마로 나열. arity를 검증한다. */
    private FormulaNode scalar(String name) {
        skipSpace();
        expect('(');
        List<FormulaNode> args = new ArrayList<>();
        skipSpace();
        if (peek() != ')') {
            args.add(expr());
            skipSpace();
            while (peek() == ',') {
                pos++;
                args.add(expr());
                skipSpace();
            }
        }
        expect(')');
        int[] arity = SCALAR_FUNCS.get(name);
        if (args.size() < arity[0] || args.size() > arity[1]) {
            throw syntaxError("함수 " + name + "의 인자 개수가 맞지 않습니다: " + args.size());
        }
        return new FormulaNode.Call(name, List.copyOf(args));
    }

    /** 집계 함수 — 열 참조(범위·나열)를 인자로. */
    private FormulaNode aggregate(String name) {
        skipSpace();
        expect('(');

        List<String> keys = new ArrayList<>();
        FormulaNode.Ref first = (FormulaNode.Ref) ref(true);
        skipSpace();
        // 표간 집계 — 다른 표의 열 하나만(v1). 범위·나열은 후속(#915a-2 범위 밖).
        if (first.datasetId() != null) {
            if (peek() == ':' || peek() == ',') {
                throw syntaxError("표간 집계는 열 하나만 지원합니다");
            }
            skipSpace();
            expect(')');
            return new FormulaNode.CrossAgg(name, first.datasetId(), first.colKey());
        }
        if (peek() == ':') {
            // 범위 — 지금 그 사이에 있는 열들로 펼쳐 집합으로 굳힌다(D7).
            pos++;
            FormulaNode.Ref last = (FormulaNode.Ref) ref(true);
            keys.addAll(expandRange(first.colKey(), last.colKey()));
        } else {
            keys.add(first.colKey());
            while (peek() == ',') {
                pos++;
                keys.add(((FormulaNode.Ref) ref(true)).colKey());
                skipSpace();
            }
        }
        skipSpace();
        expect(')');
        return new FormulaNode.Agg(name, List.copyOf(new LinkedHashSet<>(keys)));
    }

    /** {@code {a}:{c}} → 현재 열 순서에서 a..c 구간. 역방향으로 줘도 받아준다. */
    private List<String> expandRange(String fromKey, String toKey) {
        List<String> all = ctx.columnKeys();
        int from = all.indexOf(fromKey);
        int to = all.indexOf(toKey);
        if (from < 0 || to < 0) {
            throw syntaxError("범위의 열을 찾을 수 없습니다");
        }
        return new ArrayList<>(all.subList(Math.min(from, to), Math.max(from, to) + 1));
    }

    /**
     * {@code '{' 이름 '}' 행?} — 열 참조.
     *
     * @param columnOnly 집계 인자 자리면 true. 행을 붙일 수 없고 열 전체를 뜻한다.
     */
    private FormulaNode ref(boolean columnOnly) {
        skipSpace();
        expect('{');
        int close = src.indexOf('}', pos);
        if (close < 0) {
            throw syntaxError("열 이름을 닫는 '}'가 없습니다");
        }
        String content = src.substring(pos, close);
        pos = close + 1;
        if (content.isBlank()) {
            throw syntaxError("열 이름이 비었습니다");
        }

        // 표간 참조 {표!열}행 — '!' 앞은 표(입력형 이름·저장형 id), 뒤는 대상 표의 열.
        int bang = content.indexOf('!');
        if (bang >= 0) {
            return crossRef(content.substring(0, bang).trim(),
                    content.substring(bang + 1).trim(), columnOnly);
        }

        String key = resolveKey(ctx, content);
        Long rowId = rowSuffix(ctx);
        if (rowId != null && columnOnly) {
            throw syntaxError("집계 함수 안에서는 행을 지정할 수 없습니다: {" + content + "}");
        }
        if (columnOnly) {
            return new FormulaNode.Ref(FormulaRefKind.COLUMN_ALL, key, null);
        }
        return rowId == null
                ? new FormulaNode.Ref(FormulaRefKind.SAME_ROW, key, null)
                : new FormulaNode.Ref(FormulaRefKind.ABSOLUTE, key, rowId);
    }

    /**
     * 표간 절대셀 참조 {@code {표!열}행}(#918). 대상 표를 해석하고 열·행을 <b>대상 표 기준</b>으로
     * 푼다. 다른 표엔 "같은 행"이 없으므로 행 번호가 반드시 있어야 한다(ABSOLUTE).
     */
    private FormulaNode crossRef(String tablePart, String colPart, boolean columnOnly) {
        long targetId = resolveTable(tablePart);
        FormulaContext target = ctx.forDataset(targetId);
        String key = resolveKey(target, colPart);
        if (columnOnly) {
            // 표간 집계 인자 — 대상 표의 열 전체(행 없음). CrossAgg 조립은 aggregate()가 한다.
            return new FormulaNode.Ref(FormulaRefKind.COLUMN_ALL, key, null, targetId);
        }
        Long rowId = rowSuffix(target);
        if (rowId == null) {
            throw syntaxError("표간 셀 참조는 행 번호가 필요합니다: {" + tablePart + "!" + colPart + "}");
        }
        return new FormulaNode.Ref(FormulaRefKind.ABSOLUTE, key, rowId, targetId);
    }

    /** 표 부분 → 대상 표 id. 저장형은 datasetId 숫자, 입력형은 표 이름(FE tableRefs로 해석). */
    private long resolveTable(String tablePart) {
        if (stored) {
            try {
                return Long.parseLong(tablePart);
            } catch (NumberFormatException e) {
                throw syntaxError("잘못된 표 id: " + tablePart);
            }
        }
        return ctx.tableIdByName(tablePart)
                .orElseThrow(() -> syntaxError("없는 표: " + tablePart));
    }

    private String resolveKey(FormulaContext c, String name) {
        if (stored) {
            if (!c.columnKeys().contains(name)) {
                throw syntaxError("없는 열: " + name);
            }
            return name;
        }
        return c.keyByLabel(name)
                .orElseThrow(() -> syntaxError("없는 열: " + name));
    }

    /** 행 지정을 읽는다. 없으면 null. 저장형은 {@code @id}, 입력형은 화면 행 번호(그 표 기준). */
    private Long rowSuffix(FormulaContext c) {
        if (stored) {
            if (peek() != '@') {
                return null;
            }
            pos++;
            return digits("행 id");
        }
        if (!Character.isDigit(peek())) {
            return null;
        }
        long number = digits("행 번호");
        // 화면 번호 → 행 id. 파싱 시점에 한 번만 해석하므로 이후 행이 밀려도 안 깨진다.
        return c.rowIdByNumber((int) number)
                .orElseThrow(() -> syntaxError("없는 행: " + number));
    }

    private long digits(String what) {
        int start = pos;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
            pos++;
        }
        if (start == pos) {
            throw syntaxError(what + "가 필요합니다");
        }
        try {
            return Long.parseLong(src.substring(start, pos));
        } catch (NumberFormatException e) {
            throw syntaxError(what + "가 너무 큽니다");
        }
    }

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private void expect(char c) {
        if (peek() != c) {
            throw syntaxError("'" + c + "'가 필요합니다");
        }
        pos++;
    }

    private void skipSpace() {
        while (pos < src.length() && src.charAt(pos) == ' ') {
            pos++;
        }
    }

    private CustomException syntaxError(String detail) {
        return new CustomException(ErrorCode.FORMULA_SYNTAX_ERROR, detail);
    }
}
