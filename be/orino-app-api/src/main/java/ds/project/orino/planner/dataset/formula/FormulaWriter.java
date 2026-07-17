package ds.project.orino.planner.dataset.formula;

import ds.project.orino.domain.planner.dataset.entity.FormulaRefKind;

import java.math.BigDecimal;
import java.util.stream.Collectors;

/**
 * 구문 트리를 문자열로. 두 방향이 있다.
 *
 * <ul>
 *   <li>{@link #toStored} — 열 key·행 id. DB에 담기는 형태. 이름을 바꾸거나 행이 밀려도 안 변한다
 *   <li>{@link #toDisplay} — 열 label·행 번호. 사용자에게 보이는 형태. <b>읽을 때마다 새로 만든다</b>
 * </ul>
 *
 * <p>저장은 주소로, 표시는 위치로 — {@code cells}가 key 맵이고 API가 위치 배열인 것과 같은 전략이다.
 * 덕분에 열 이름을 바꾸면 저장된 수식은 그대로인데 표시만 새 이름으로 따라온다.
 */
public final class FormulaWriter {

    private FormulaWriter() {
    }

    /** 저장형. {@code =SUM({c2})*{c0}} / {@code ={c1}@142} */
    public static String toStored(FormulaNode node) {
        StringBuilder sb = new StringBuilder("=");
        write(node, sb, null);
        return sb.toString();
    }

    /**
     * 표시형. {@code =SUM({점수})*{과목}} / {@code ={점수}5}
     *
     * <p>지워진 열·행을 가리키면 {@code #REF!}로 보여준다 — 저장형은 끊긴 참조를 그대로 안고 있고,
     * 그게 {@code #REF!}를 만들 수 있는 근거다.
     */
    public static String toDisplay(FormulaNode node, FormulaContext ctx) {
        StringBuilder sb = new StringBuilder("=");
        write(node, sb, ctx);
        return sb.toString();
    }

    /** {@code ctx}가 null이면 저장형, 아니면 표시형. 트리를 한 번만 순회하도록 합쳤다. */
    private static void write(FormulaNode node, StringBuilder sb, FormulaContext ctx) {
        switch (node) {
            case FormulaNode.Num n -> sb.append(plain(n.value()));
            case FormulaNode.Unary u -> {
                sb.append(u.op());
                write(u.operand(), sb, ctx);
            }
            case FormulaNode.Binary b -> {
                // 트리 모양을 잃지 않도록 괄호를 항상 친다. 우선순위를 되짚어 생략하지 않는다.
                sb.append('(');
                write(b.left(), sb, ctx);
                sb.append(' ').append(b.op()).append(' ');
                write(b.right(), sb, ctx);
                sb.append(')');
            }
            case FormulaNode.Agg a -> sb.append(a.func()).append('(')
                    .append(a.colKeys().stream()
                            .map(k -> "{" + name(k, ctx) + "}")
                            .collect(Collectors.joining(", ")))
                    .append(')');
            case FormulaNode.Ref r -> {
                sb.append('{').append(name(r.colKey(), ctx)).append('}');
                if (r.kind() == FormulaRefKind.ABSOLUTE) {
                    sb.append(rowToken(r.rowId(), ctx));
                }
            }
        }
    }

    private static String name(String key, FormulaContext ctx) {
        if (ctx == null) {
            return key;
        }
        return ctx.labelByKey(key).orElse("#REF!");
    }

    private static String rowToken(Long rowId, FormulaContext ctx) {
        if (ctx == null) {
            return "@" + rowId;
        }
        return ctx.rowNumberById(rowId).map(String::valueOf).orElse("#REF!");
    }

    /** 지수 표기를 피한다 — {@code 1E+2} 같은 게 수식에 보이면 곤란하다. */
    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
