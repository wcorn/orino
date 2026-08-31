package ds.project.orino.planner.dataset.xlsx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A1 → 저장형 역번역(#1310). {@link FormulaA1WriterTest}의 반대 방향이다.
 *
 * <p>여기서 보는 것은 <b>주소를 바꿔 끼우는 일</b>뿐이다 — 괄호·우선순위·인자 개수는 저장형을
 * 읽는 파서가 보므로 이 클래스의 책임이 아니다.
 */
class FormulaA1ReaderTest {

    /** A=c0, B=c1, C=c2. 시트 2·3·4행이 행 id 11·12·13. 데이터 구간은 2..4행. */
    private static final FormulaA1Reader.Layout LAYOUT = new FormulaA1Reader.Layout(
            Map.of(0, "c0", 1, "c1", 2, "c2"),
            Map.of(2, 11L, 3, 12L, 4, 13L),
            2, 4);

    private final FormulaA1Reader reader = new FormulaA1Reader(LAYOUT);

    /** 3행(=행 id 12)에 놓인 수식으로 읽는다. */
    private String read(String a1) {
        return reader.toStored(a1, 3).orElse(null);
    }

    @Nested
    @DisplayName("참조")
    class Refs {

        @Test
        @DisplayName("자기 행을 가리키면 같은 행 참조가 된다 — 행이 밀려도 안 깨지는 쪽")
        void ownRowBecomesSameRow() {
            assertThat(read("B3")).isEqualTo("={c1}");
        }

        @Test
        @DisplayName("행을 고정했어도 자기 행이면 같은 행이다")
        void dollarOnOwnRowIsStillSameRow() {
            assertThat(read("B$3")).isEqualTo("={c1}");
        }

        @Test
        @DisplayName("다른 행을 가리키면 그 행의 id로 묶인다")
        void otherRowBecomesAbsolute() {
            assertThat(read("C$4")).isEqualTo("={c2}@13");
            assertThat(read("C4")).isEqualTo("={c2}@13");
        }

        @Test
        @DisplayName("데이터가 아닌 행(머리글·요약)을 가리키면 옮기지 않는다")
        void nonDataRowIsUntranslatable() {
            assertThat(read("B1")).isNull();
        }

        @Test
        @DisplayName("우리 표에 없는 열이면 옮기지 않는다")
        void unknownColumnIsUntranslatable() {
            assertThat(read("Z3")).isNull();
        }
    }

    @Nested
    @DisplayName("범위")
    class Ranges {

        @Test
        @DisplayName("데이터 구간 전체를 덮는 한 열 범위는 열 집계가 된다")
        void fullColumnRangeBecomesAggregate() {
            assertThat(read("SUM(A$2:A$4)")).isEqualTo("=SUM({c0})");
        }

        @Test
        @DisplayName("여러 열 집계도 그대로 편다")
        void multipleColumns() {
            assertThat(read("SUM(A$2:A$4, B$2:B$4)")).isEqualTo("=SUM({c0}, {c1})");
        }

        @Test
        @DisplayName("부분 범위는 담을 그릇이 없어 옮기지 않는다 — 열 전체로 넓히면 값이 달라진다")
        void partialRangeIsUntranslatable() {
            assertThat(read("SUM(A$2:A$3)")).isNull();
        }

        @Test
        @DisplayName("열이 다른 범위도 옮기지 않는다")
        void crossColumnRangeIsUntranslatable() {
            assertThat(read("SUM(A$2:B$4)")).isNull();
        }
    }

    @Nested
    @DisplayName("함수와 리터럴")
    class Functions {

        @Test
        @DisplayName("엑셀 AVERAGE는 우리 AVG가 된다 — 내보낼 때의 반대")
        void averageBecomesAvg() {
            assertThat(read("AVERAGE(A$2:A$4)")).isEqualTo("=AVG({c0})");
        }

        @Test
        @DisplayName("이름 한복판의 글자·숫자는 주소가 아니다")
        void functionNameIsNotAnAddress() {
            // LOG10 의 G10 을 참조로 읽으면 수식이 통째로 망가진다.
            assertThat(read("LOG10(B3)")).isEqualTo("=LOG10({c1})");
        }

        @Test
        @DisplayName("문자열 안의 글자는 건드리지 않는다")
        void literalsAreLeftAlone() {
            assertThat(read("IF(B3 > 10, \"B3 참고\", \"\")"))
                    .isEqualTo("=IF({c1} > 10, \"B3 참고\", \"\")");
        }

        @Test
        @DisplayName("겹따옴표 escape가 든 문자열도 그대로 지난다")
        void escapedQuotes() {
            assertThat(read("IF(B3, \"가\"\"나\", \"\")"))
                    .isEqualTo("=IF({c1}, \"가\"\"나\", \"\")");
        }

        @Test
        @DisplayName("산술은 모양 그대로 — 괄호·우선순위는 파서가 본다")
        void arithmeticPassesThrough() {
            assertThat(read("(B3 * C3)")).isEqualTo("=({c1} * {c2})");
        }
    }

    @Nested
    @DisplayName("담을 수 없는 것")
    class OutOfScope {

        @Test
        @DisplayName("다른 시트 참조는 통째로 값으로 보낸다 — 표 하나에 담을 자리가 없다")
        void otherSheetIsUntranslatable() {
            assertThat(read("Sheet2!B2 * 2")).isNull();
        }

        @Test
        @DisplayName("빈 수식은 수식이 아니다")
        void blankIsEmpty() {
            assertThat(read("")).isNull();
            assertThat(read(null)).isNull();
        }
    }

    @Nested
    @DisplayName("요약줄 알아보기")
    class SummaryRow {

        private static final List<String> ALLOWED =
                List.of("SUM", "AVERAGE", "COUNT", "MIN", "MAX");

        @Test
        @DisplayName("자기 열의 데이터 구간을 집계하면 요약이다")
        void recognisesSummary() {
            assertThat(reader.summaryFunction("SUM(B2:B4)", 1, ALLOWED)).contains("SUM");
        }

        @Test
        @DisplayName("남의 열을 집계하는 것은 요약이 아니다 — 그냥 데이터 줄이다")
        void otherColumnIsNotSummary() {
            assertThat(reader.summaryFunction("SUM(A2:A4)", 1, ALLOWED)).isEmpty();
        }

        @Test
        @DisplayName("구간이 데이터와 안 맞으면 요약이 아니다")
        void partialRangeIsNotSummary() {
            assertThat(reader.summaryFunction("SUM(B2:B3)", 1, ALLOWED)).isEmpty();
        }

        @Test
        @DisplayName("열 설정이 못 받는 함수는 요약으로 삼지 않는다")
        void unsupportedFunctionIsNotSummary() {
            assertThat(reader.summaryFunction("MEDIAN(B2:B4)", 1, ALLOWED)).isEmpty();
        }
    }

    @Test
    @DisplayName("열 문자를 번호로 되돌린다 — 내보낼 때의 반대")
    void columnLetters() {
        assertThat(FormulaA1Reader.toIndex("A")).isZero();
        assertThat(FormulaA1Reader.toIndex("Z")).isEqualTo(25);
        assertThat(FormulaA1Reader.toIndex("AA")).isEqualTo(26);
        assertThat(FormulaA1Reader.toIndex("ZZ")).isEqualTo(701);
        assertThat(FormulaA1Reader.toIndex("AAA")).isEqualTo(702);
    }
}
