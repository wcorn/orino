package ds.project.orino.planner.dataset.formula;

import ds.project.orino.domain.planner.dataset.entity.FormulaRefKind;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 구문 트리 평가.
 *
 * <p>엑셀 모델을 따른다(O11) — <b>열에 타입이 없고 셀 단위로 평가·에러</b>한다.
 * 집계는 숫자가 아닌 칸(텍스트·빈 칸)을 <b>무시</b>하지만, 산술은 무시하지 않고
 * {@code #VALUE!}를 낸다. "합계는 숫자만 더한다"와 "곱셈에 글자가 오면 잘못이다"가 둘 다 자연스럽다.
 */
public final class FormulaEvaluator {

    /** 나눗셈은 무한소수가 될 수 있어 정밀도를 정해야 한다. */
    private static final MathContext DIV = new MathContext(20, RoundingMode.HALF_UP);

    /** 평가에 필요한 셀 값을 읽어 온다. 없는 열·행이면 empty — 호출부가 {@code #REF!}로 만든다. */
    public interface ValueSource {

        /** 계산 중인 그 행의 열 값. */
        Optional<String> sameRow(String colKey);

        /** 특정 행의 열 값. 행이 지워졌으면 empty. */
        Optional<String> absolute(long rowId, String colKey);

        /** 열 전체 값. 열이 없으면 empty. */
        Optional<List<String>> column(String colKey);

        /**
         * 표간 절대셀({@code {요약!환율}1})의 <b>대상 표</b> 셀. 표간 참조를 지원하지 않는
         * 소스(테스트 등)는 기본 empty → {@code #REF!}.
         */
        default Optional<String> crossAbsolute(long datasetId, long rowId, String colKey) {
            return Optional.empty();
        }
    }

    private FormulaEvaluator() {
    }

    public static FormulaValue evaluate(FormulaNode node, ValueSource src) {
        return switch (node) {
            case FormulaNode.Num n -> new FormulaValue.Num(n.value());
            case FormulaNode.Str s -> new FormulaValue.Text(s.value());
            case FormulaNode.Unary u -> unary(u, src);
            case FormulaNode.Binary b -> binary(b, src);
            case FormulaNode.Compare c -> compare(c, src);
            case FormulaNode.Ref r -> ref(r, src);
            case FormulaNode.Agg a -> agg(a, src);
            case FormulaNode.AggIf a -> aggIf(a, src);
            case FormulaNode.Call c -> call(c, src);
        };
    }

    private static FormulaValue unary(FormulaNode.Unary u, ValueSource src) {
        FormulaValue n = asNumber(evaluate(u.operand(), src));
        if (n instanceof FormulaValue.Err) {
            return n;
        }
        BigDecimal v = ((FormulaValue.Num) n).value();
        return new FormulaValue.Num(u.op() == '-' ? v.negate() : v);
    }

    /**
     * 스칼라 함수. {@code IF}는 <b>고른 가지만</b> 평가(지연)하고, 나머지는 인자를 평가해 값 규칙을 적용한다.
     * 어디서든 에러는 좌→우 첫 에러가 번진다. arity는 파서가 보장한다.
     */
    private static FormulaValue call(FormulaNode.Call c, ValueSource src) {
        List<FormulaNode> a = c.args();
        return switch (c.func()) {
            case "ABS" -> {
                FormulaValue n = asNumber(evaluate(a.get(0), src));
                yield n instanceof FormulaValue.Err ? n
                        : new FormulaValue.Num(((FormulaValue.Num) n).value().abs());
            }
            case "IF" -> {
                FormulaValue cond = asBool(evaluate(a.get(0), src));
                if (cond instanceof FormulaValue.Err) {
                    yield cond;
                }
                // 지연 평가: 고른 가지만 계산한다(안 고른 가지의 에러·비용 억제).
                yield evaluate(((FormulaValue.Bool) cond).value() ? a.get(1) : a.get(2), src);
            }
            case "NOT" -> {
                FormulaValue v = asBool(evaluate(a.get(0), src));
                yield v instanceof FormulaValue.Err ? v
                        : new FormulaValue.Bool(!((FormulaValue.Bool) v).value());
            }
            case "AND", "OR" -> logical(c.func(), a, src);
            default -> new FormulaValue.Err(FormulaValue.Err.VALUE);
        };
    }

    /** {@code AND}/{@code OR} — 인자를 다 평가하되(엑셀식) 에러는 전파한다. */
    private static FormulaValue logical(String func, List<FormulaNode> args, ValueSource src) {
        boolean and = func.equals("AND");
        boolean acc = and;
        for (FormulaNode arg : args) {
            FormulaValue v = asBool(evaluate(arg, src));
            if (v instanceof FormulaValue.Err) {
                return v;
            }
            boolean b = ((FormulaValue.Bool) v).value();
            acc = and ? acc && b : acc || b;
        }
        return new FormulaValue.Bool(acc);
    }

    private static FormulaValue binary(FormulaNode.Binary b, ValueSource src) {
        FormulaValue l = asNumber(evaluate(b.left(), src));
        if (l instanceof FormulaValue.Err) {
            return l;
        }
        FormulaValue r = asNumber(evaluate(b.right(), src));
        if (r instanceof FormulaValue.Err) {
            return r;
        }
        BigDecimal x = ((FormulaValue.Num) l).value();
        BigDecimal y = ((FormulaValue.Num) r).value();
        return switch (b.op()) {
            case '+' -> new FormulaValue.Num(x.add(y));
            case '-' -> new FormulaValue.Num(x.subtract(y));
            case '*' -> new FormulaValue.Num(x.multiply(y));
            case '/' -> y.signum() == 0
                    ? new FormulaValue.Err(FormulaValue.Err.DIV0)
                    : new FormulaValue.Num(x.divide(y, DIV));
            default -> new FormulaValue.Err(FormulaValue.Err.VALUE);
        };
    }

    /**
     * 비교 → boolean. 같은 타입끼리는 그 타입으로, 다르면 <b>number &lt; text &lt; boolean</b> 순위로 견준다
     * (엑셀 타입 순서 — 임의 텍스트가 임의 숫자보다 크다). 텍스트는 대소문자를 무시한다.
     */
    private static FormulaValue compare(FormulaNode.Compare c, ValueSource src) {
        FormulaValue l = evaluate(c.left(), src);
        if (l instanceof FormulaValue.Err) {
            return l;
        }
        FormulaValue r = evaluate(c.right(), src);
        if (r instanceof FormulaValue.Err) {
            return r;
        }
        return new FormulaValue.Bool(satisfies(c.op(), order(l, r)));
    }

    /** 비교 연산자를 {@code order()} 결과(음수/0/양수)에 적용한다. 비교·조건부 집계가 공유한다. */
    private static boolean satisfies(String op, int cmp) {
        return switch (op) {
            case "=" -> cmp == 0;
            case "<>" -> cmp != 0;
            case "<" -> cmp < 0;
            case ">" -> cmp > 0;
            case "<=" -> cmp <= 0;
            case ">=" -> cmp >= 0;
            default -> false;
        };
    }

    private static int order(FormulaValue l, FormulaValue r) {
        if (l instanceof FormulaValue.Num a && r instanceof FormulaValue.Num b) {
            return a.value().compareTo(b.value());
        }
        if (l instanceof FormulaValue.Text a && r instanceof FormulaValue.Text b) {
            return a.value().compareToIgnoreCase(b.value());
        }
        if (l instanceof FormulaValue.Bool a && r instanceof FormulaValue.Bool b) {
            return Boolean.compare(a.value(), b.value());
        }
        return Integer.compare(rank(l), rank(r));
    }

    private static int rank(FormulaValue v) {
        return switch (v) {
            case FormulaValue.Num ignored -> 0;
            case FormulaValue.Text ignored -> 1;
            case FormulaValue.Bool ignored -> 2;
            case FormulaValue.Err ignored -> 3;
        };
    }

    /** 산술·ABS·비교 피연산자를 숫자로 강제한다. Bool→1/0, Text→{@code #VALUE!}(빈 셀은 이미 0). */
    private static FormulaValue asNumber(FormulaValue v) {
        return switch (v) {
            case FormulaValue.Num n -> n;
            case FormulaValue.Bool b -> new FormulaValue.Num(b.value() ? BigDecimal.ONE : BigDecimal.ZERO);
            case FormulaValue.Text ignored -> new FormulaValue.Err(FormulaValue.Err.VALUE);
            case FormulaValue.Err e -> e;
        };
    }

    /** IF 조건·AND/OR/NOT 인자를 boolean으로 강제한다. Num→0이면 거짓, Text는 "TRUE"/"FALSE"만 허용. */
    private static FormulaValue asBool(FormulaValue v) {
        return switch (v) {
            case FormulaValue.Bool b -> b;
            case FormulaValue.Num n -> new FormulaValue.Bool(n.value().signum() != 0);
            case FormulaValue.Text t -> {
                if (t.value().equalsIgnoreCase("TRUE")) {
                    yield new FormulaValue.Bool(true);
                }
                yield t.value().equalsIgnoreCase("FALSE")
                        ? new FormulaValue.Bool(false)
                        : new FormulaValue.Err(FormulaValue.Err.VALUE);
            }
            case FormulaValue.Err e -> e;
        };
    }

    /** 참조 → 셀 내용을 타입으로. 빈 칸은 0(엑셀식), 숫자는 Num, 에러 문자열은 번지고, 그 외는 Text. */
    private static FormulaValue ref(FormulaNode.Ref r, ValueSource src) {
        Optional<String> raw;
        if (r.kind() == FormulaRefKind.ABSOLUTE) {
            // 표간 참조(datasetId 있음)는 대상 표의 셀을, 아니면 이 표의 셀을 읽는다.
            raw = r.datasetId() == null
                    ? src.absolute(r.rowId(), r.colKey())
                    : src.crossAbsolute(r.datasetId(), r.rowId(), r.colKey());
        } else {
            raw = src.sameRow(r.colKey());
        }
        if (raw.isEmpty()) {
            return new FormulaValue.Err(FormulaValue.Err.REF);
        }
        String s = raw.get().trim();
        if (s.isEmpty()) {
            return new FormulaValue.Num(BigDecimal.ZERO);
        }
        // 참조한 셀이 이미 에러면 그 에러가 번진다.
        if (s.startsWith("#")) {
            return new FormulaValue.Err(s);
        }
        return number(s)
                .<FormulaValue>map(FormulaValue.Num::new)
                .orElseGet(() -> new FormulaValue.Text(s));
    }

    /**
     * 집계. O11 — 숫자가 아닌 칸과 빈 칸은 <b>무시</b>한다.
     * 참조한 열이 없으면 {@code #REF!}, 셀이 에러면 그 에러가 번진다.
     */
    private static FormulaValue agg(FormulaNode.Agg a, ValueSource src) {
        List<BigDecimal> nums = new ArrayList<>();
        for (String key : a.colKeys()) {
            Optional<List<String>> col = src.column(key);
            if (col.isEmpty()) {
                return new FormulaValue.Err(FormulaValue.Err.REF);
            }
            for (String cell : col.get()) {
                String s = cell == null ? "" : cell.trim();
                if (s.startsWith("#")) {
                    return new FormulaValue.Err(s);
                }
                number(s).ifPresent(nums::add);
            }
        }

        return switch (a.func()) {
            // 숫자만 센다. 비어있지 않은 걸 세는 COUNTA는 D8 범위 밖.
            case "COUNT" -> new FormulaValue.Num(BigDecimal.valueOf(nums.size()));
            case "SUM" -> new FormulaValue.Num(
                    nums.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
            // 분모는 숫자인 셀 수. 하나도 없으면 나눌 게 없다.
            case "AVG" -> nums.isEmpty()
                    ? new FormulaValue.Err(FormulaValue.Err.DIV0)
                    : new FormulaValue.Num(nums.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(nums.size()), DIV));
            // 엑셀은 숫자가 없으면 0을 낸다.
            case "MIN" -> new FormulaValue.Num(
                    nums.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
            case "MAX" -> new FormulaValue.Num(
                    nums.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
            default -> new FormulaValue.Err(FormulaValue.Err.VALUE);
        };
    }

    /**
     * 조건부 집계. criteria를 한 번 평가해 (연산자, 기준값)으로 풀고, 조건 열을 행별로 견준다.
     * 빈 셀은 매치 안 하고, 에러 셀은 번진다. {@code SUMIF}는 매치된 행의 합 열 숫자만 더한다.
     */
    private static FormulaValue aggIf(FormulaNode.AggIf a, ValueSource src) {
        FormulaValue critRaw = evaluate(a.criteria(), src);
        if (critRaw instanceof FormulaValue.Err) {
            return critRaw;
        }
        Criteria crit = parseCriteria(critRaw);

        Optional<List<String>> critCol = src.column(a.critCol());
        if (critCol.isEmpty()) {
            return new FormulaValue.Err(FormulaValue.Err.REF);
        }
        List<String> critCells = critCol.get();

        boolean sumif = a.sumCol() != null;
        List<String> sumCells = List.of();
        if (sumif) {
            Optional<List<String>> sc = src.column(a.sumCol());
            if (sc.isEmpty()) {
                return new FormulaValue.Err(FormulaValue.Err.REF);
            }
            sumCells = sc.get();
        }

        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (int i = 0; i < critCells.size(); i++) {
            String cell = critCells.get(i) == null ? "" : critCells.get(i).trim();
            if (cell.isEmpty()) {
                continue; // 빈 셀은 어떤 조건에도 매치하지 않는다(엑셀식)
            }
            if (cell.startsWith("#")) {
                return new FormulaValue.Err(cell);
            }
            if (!matches(crit.op(), cellValue(cell), crit.target())) {
                continue;
            }
            count++;
            if (sumif) {
                String sc = i < sumCells.size() && sumCells.get(i) != null
                        ? sumCells.get(i).trim() : "";
                if (sc.startsWith("#")) {
                    return new FormulaValue.Err(sc);
                }
                Optional<BigDecimal> n = number(sc);
                if (n.isPresent()) {
                    sum = sum.add(n.get());
                }
            }
        }
        return new FormulaValue.Num(sumif ? sum : BigDecimal.valueOf(count));
    }

    /** criteria(연산자 접두 + 기준값). 연산자가 없으면 정확 일치({@code =}). */
    private record Criteria(String op, FormulaValue target) {
    }

    private static final String[] CRITERIA_OPS = {"<=", ">=", "<>", "<", ">", "="};

    private static Criteria parseCriteria(FormulaValue v) {
        if (v instanceof FormulaValue.Text t) {
            String s = t.value();
            for (String op : CRITERIA_OPS) {
                if (s.startsWith(op)) {
                    return new Criteria(op, cellValue(s.substring(op.length()).trim()));
                }
            }
            return new Criteria("=", cellValue(s)); // "80"도 숫자로 봐 셀 80과 맞춘다
        }
        return new Criteria("=", v); // 숫자·불린은 그대로 정확 일치
    }

    /** 셀·기준 문자열을 타입으로. 숫자로 읽히면 Num, 아니면 Text. */
    private static FormulaValue cellValue(String s) {
        return number(s).<FormulaValue>map(FormulaValue.Num::new)
                .orElseGet(() -> new FormulaValue.Text(s));
    }

    /**
     * 셀이 criteria에 맞는가. 같은 종류(둘 다 숫자/둘 다 텍스트)면 {@code order()}로 견주고,
     * 종류가 다르면 같을 수 없으니 {@code <>}만 참이다 — 텍스트 셀은 {@code >80}에 안 걸린다.
     */
    private static boolean matches(String op, FormulaValue cell, FormulaValue target) {
        boolean sameKind = (cell instanceof FormulaValue.Num && target instanceof FormulaValue.Num)
                || (cell instanceof FormulaValue.Text && target instanceof FormulaValue.Text);
        return sameKind ? satisfies(op, order(cell, target)) : op.equals("<>");
    }

    private static Optional<BigDecimal> number(String s) {
        if (s.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(s));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
