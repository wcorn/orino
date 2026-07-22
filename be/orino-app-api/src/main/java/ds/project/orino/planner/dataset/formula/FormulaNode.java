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

    /** 리터럴 문자열. {@code "엔"}처럼 큰따옴표로 감싼다. */
    record Str(String value) implements FormulaNode {
    }

    /** 단항 부호. {@code op}는 '+' 또는 '-'. */
    record Unary(char op, FormulaNode operand) implements FormulaNode {
    }

    /** 이항 산술. {@code op}는 '+' '-' '*' '/'. */
    record Binary(char op, FormulaNode left, FormulaNode right) implements FormulaNode {
    }

    /**
     * 비교. {@code op}는 {@code = <> < > <= >=}. 산술({@link Binary})보다 우선순위가 낮고
     * 결과는 boolean이다. 두 글자 연산자가 있어 op를 String으로 둔다.
     */
    record Compare(String op, FormulaNode left, FormulaNode right) implements FormulaNode {
    }

    /**
     * 셀 참조.
     *
     * <ul>
     *   <li>{@link FormulaRefKind#SAME_ROW} — {@code rowId}는 null. 각 행이 자기 행을 가리킨다
     *   <li>{@link FormulaRefKind#ABSOLUTE} — {@code rowId}에 값
     * </ul>
     *
     * <p>{@code datasetId}는 <b>표간 참조</b>({@code ={요약!환율}1})의 대상 표다. null이면 같은
     * 표(대부분). 표간은 대상 셀이 다른 표에 있어 ABSOLUTE(특정 행)만 의미가 있다 — 같은 표가
     * 아니면 "같은 행"이 정의되지 않는다. {@code colKey}·{@code rowId}는 <b>대상 표 기준</b>이다.
     */
    record Ref(FormulaRefKind kind, String colKey, Long rowId, Long datasetId)
            implements FormulaNode {

        /** 같은 표 참조(대부분) — datasetId 없이. */
        public Ref(FormulaRefKind kind, String colKey, Long rowId) {
            this(kind, colKey, rowId, null);
        }
    }

    /**
     * 열 집계. 인자는 <b>열 key 목록</b>이다 — 범위({@code {a}:{c}})는 파싱 시점에
     * 그 사이의 열들로 펼쳐져 집합으로 굳는다(D7). 그래서 이후 순서를 바꿔도 의미가 안 변하고,
     * 저장형·표시형 어디에도 범위 문법이 남지 않는다.
     */
    record Agg(String func, List<String> colKeys) implements FormulaNode {
    }

    /**
     * 스칼라 함수 호출. 집계({@link Agg})와 달리 인자가 <b>열 참조가 아니라 식</b>이다 —
     * {@code ABS({점수} - 10)}처럼 셀·산술을 품는다. 각 인자는 셀 단위로 평가된다.
     *
     * <p>IF·AND·OR·NOT이 올라탈 구조라 여기서 먼저 깐다(#893). 함수별 인자 개수(arity)는
     * 파싱 시점에 검증한다.
     */
    record Call(String func, List<FormulaNode> args) implements FormulaNode {
    }

    /**
     * 조건부 집계({@code COUNTIF}·{@code SUMIF}). 집계와 스칼라의 하이브리드 — 열 인자(조건 열·합 열)와
     * criteria 식(행별로 견줄 값)을 함께 든다.
     *
     * <ul>
     *   <li>{@code critCol} — 조건을 검사할 열 key
     *   <li>{@code criteria} — 한 번 평가해 얻는 조건. 문자열이면 {@code ">80"}처럼 연산자 접두를 해석하고,
     *       아니면 정확 일치
     *   <li>{@code sumCol} — 합할 열 key. {@code COUNTIF}는 null(개수만 센다)
     * </ul>
     */
    record AggIf(String func, String critCol, FormulaNode criteria, String sumCol)
            implements FormulaNode {
    }
}
