package ds.project.orino.planner.dataset.formula;

import java.math.BigDecimal;

/**
 * 평가 결과. 값 아니면 에러다 — 엑셀처럼 <b>셀 단위로</b> 에러를 낸다.
 * 열에 타입을 두지 않으므로, 같은 열의 어떤 셀은 값이고 어떤 셀은 에러일 수 있다.
 */
public sealed interface FormulaValue {

    /** 계산된 숫자. */
    record Num(BigDecimal value) implements FormulaValue {
    }

    /** {@code #VALUE!} {@code #DIV/0!} {@code #REF!} 같은 셀 에러. */
    record Err(String code) implements FormulaValue {

        public static final String VALUE = "#VALUE!";
        public static final String DIV0 = "#DIV/0!";
        public static final String REF = "#REF!";
    }

    /** 셀에 담길 문자열. 에러면 에러 코드가 그대로 값 자리에 들어간다. */
    default String asCell() {
        return switch (this) {
            case Num n -> n.value().stripTrailingZeros().toPlainString();
            case Err e -> e.code();
        };
    }
}
