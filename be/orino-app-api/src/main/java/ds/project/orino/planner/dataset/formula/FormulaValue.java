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

    /** 문자열. 텍스트 셀·문자열 리터럴·문자열 비교의 결과가 여기로 온다. */
    record Text(String value) implements FormulaValue {
    }

    /** 참/거짓. 비교연산자·논리 함수(IF 조건·AND/OR/NOT)의 결과. 엑셀처럼 셀엔 TRUE/FALSE로 담긴다. */
    record Bool(boolean value) implements FormulaValue {
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
            case Text t -> t.value();
            case Bool b -> b.value() ? "TRUE" : "FALSE";
            case Err e -> e.code();
        };
    }
}
