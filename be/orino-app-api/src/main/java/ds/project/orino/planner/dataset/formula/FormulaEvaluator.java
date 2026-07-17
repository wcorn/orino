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
    }

    private FormulaEvaluator() {
    }

    public static FormulaValue evaluate(FormulaNode node, ValueSource src) {
        return switch (node) {
            case FormulaNode.Num n -> new FormulaValue.Num(n.value());
            case FormulaNode.Unary u -> {
                FormulaValue v = evaluate(u.operand(), src);
                yield switch (v) {
                    case FormulaValue.Err e -> e;
                    case FormulaValue.Num n ->
                            new FormulaValue.Num(u.op() == '-' ? n.value().negate() : n.value());
                };
            }
            case FormulaNode.Binary b -> binary(b, src);
            case FormulaNode.Ref r -> ref(r, src);
            case FormulaNode.Agg a -> agg(a, src);
        };
    }

    private static FormulaValue binary(FormulaNode.Binary b, ValueSource src) {
        FormulaValue l = evaluate(b.left(), src);
        if (l instanceof FormulaValue.Err) {
            return l;
        }
        FormulaValue r = evaluate(b.right(), src);
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

    /** 산술 속 참조. 빈 칸은 0으로 보고(엑셀과 같다), 숫자가 아니면 {@code #VALUE!}. */
    private static FormulaValue ref(FormulaNode.Ref r, ValueSource src) {
        Optional<String> raw = r.kind() == FormulaRefKind.ABSOLUTE
                ? src.absolute(r.rowId(), r.colKey())
                : src.sameRow(r.colKey());
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
                .orElseGet(() -> new FormulaValue.Err(FormulaValue.Err.VALUE));
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
