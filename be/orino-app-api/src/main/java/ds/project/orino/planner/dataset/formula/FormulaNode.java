package ds.project.orino.planner.dataset.formula;

import ds.project.orino.domain.planner.dataset.entity.FormulaRefKind;

import java.math.BigDecimal;
import java.util.List;

/**
 * 수식 구문 트리. 파싱이 끝난 뒤엔 label·행 번호가 남지 않고 <b>열 key와 행 id로만</b> 이뤄진다 —
 * 그래야 열 이름을 바꾸거나 행이 밀려도 수식이 안 깨진다.
 *
 * <p>표시 문자열은 이 트리에서 그때그때 만든다({@link FormulaWriter}).
 */
public sealed interface FormulaNode {

    /** 리터럴 숫자. */
    record Num(BigDecimal value) implements FormulaNode {
    }

    /** 단항 부호. {@code op}는 '+' 또는 '-'. */
    record Unary(char op, FormulaNode operand) implements FormulaNode {
    }

    /** 이항 산술. {@code op}는 '+' '-' '*' '/'. */
    record Binary(char op, FormulaNode left, FormulaNode right) implements FormulaNode {
    }

    /**
     * 셀 참조.
     *
     * <ul>
     *   <li>{@link FormulaRefKind#SAME_ROW} — {@code rowId}는 null. 각 행이 자기 행을 가리킨다
     *   <li>{@link FormulaRefKind#ABSOLUTE} — {@code rowId}에 값
     * </ul>
     */
    record Ref(FormulaRefKind kind, String colKey, Long rowId) implements FormulaNode {
    }

    /**
     * 열 집계. 인자는 <b>열 key 목록</b>이다 — 범위({@code {a}:{c}})는 파싱 시점에
     * 그 사이의 열들로 펼쳐져 집합으로 굳는다(D7). 그래서 이후 순서를 바꿔도 의미가 안 변하고,
     * 저장형·표시형 어디에도 범위 문법이 남지 않는다.
     */
    record Agg(String func, List<String> colKeys) implements FormulaNode {
    }
}
