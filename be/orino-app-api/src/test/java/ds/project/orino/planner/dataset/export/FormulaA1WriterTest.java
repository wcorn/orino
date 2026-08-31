package ds.project.orino.planner.dataset.export;

import ds.project.orino.domain.planner.dataset.entity.FormulaRefKind;
import ds.project.orino.planner.dataset.formula.FormulaNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수식 트리 → 엑셀 A1 번역(#1308). ADR-1이 말한 「경계에서만 A1」이 실제로 지켜지는지는
 * 여기서 검증한다 — 내부 모델(열 key · 행 id)이 들어가 좌표가 나와야 한다.
 */
class FormulaA1WriterTest {

    /** c0=A, c1=B, c2=C. 행 id 11·12·13이 시트 2·3·4행. 데이터 구간은 2..4행. */
    private static final FormulaA1Writer.Layout LAYOUT = new FormulaA1Writer.Layout(
            Map.of("c0", 0, "c1", 1, "c2", 2),
            Map.of(11L, 2, 12L, 3, 13L, 4),
            2, 4);

    private FormulaA1Writer writer() {
        return new FormulaA1Writer(LAYOUT, () -> "1450");
    }

    private String write(FormulaNode node) {
        return writer().write(node, 3);
    }

    private static FormulaNode sameRow(String colKey) {
        return new FormulaNode.Ref(FormulaRefKind.SAME_ROW, colKey, null);
    }

    @Nested
    @DisplayName("참조 종류")
    class Refs {

        @Test
        @DisplayName("같은 행 참조는 수식이 놓인 행을 따라가는 상대 주소가 된다")
        void sameRowIsRelative() {
            assertThat(write(sameRow("c1"))).isEqualTo("B3");
        }

        @Test
        @DisplayName("특정 행 참조는 그 행 id의 실제 시트 행에 고정된다")
        void absolutePinsRow() {
            assertThat(write(new FormulaNode.Ref(FormulaRefKind.ABSOLUTE, "c2", 13L)))
                    .isEqualTo("C$4");
        }

        @Test
        @DisplayName("가리키던 행이 사라졌으면 엑셀과 같은 말로 #REF!를 쓴다")
        void missingRowBecomesRefError() {
            assertThat(write(new FormulaNode.Ref(FormulaRefKind.ABSOLUTE, "c0", 99L)))
                    .isEqualTo("#REF!");
        }

        @Test
        @DisplayName("사라진 열을 가리키는 수식은 번역하지 않는다 — 그 셀은 값으로 나간다")
        void missingColumnIsUntranslatable() {
            assertThat(write(sameRow("c9"))).isNull();
        }
    }

    @Nested
    @DisplayName("집계")
    class Aggregates {

        @Test
        @DisplayName("열 전체 집계는 헤더·푸터를 뺀 데이터 구간을 행 고정 범위로 편다")
        void aggSpansDataRows() {
            assertThat(write(new FormulaNode.Agg("SUM", List.of("c0", "c1"))))
                    .isEqualTo("SUM(A$2:A$4, B$2:B$4)");
        }

        @Test
        @DisplayName("내부 AVG는 엑셀 이름 AVERAGE로 바뀐다")
        void avgBecomesAverage() {
            assertThat(write(new FormulaNode.Agg("AVG", List.of("c0"))))
                    .isEqualTo("AVERAGE(A$2:A$4)");
        }

        @Test
        @DisplayName("SUMIF는 조건 열·조건·합 열 순서 그대로 나간다")
        void sumIfKeepsShape() {
            FormulaNode node = new FormulaNode.AggIf(
                    "SUMIF", "c0", new FormulaNode.Str(">80"), "c1");
            assertThat(write(node)).isEqualTo("SUMIF(A$2:A$4, \">80\", B$2:B$4)");
        }

        @Test
        @DisplayName("COUNTIF는 합 열이 없으므로 인자도 둘뿐이다")
        void countIfHasNoSumColumn() {
            FormulaNode node = new FormulaNode.AggIf(
                    "COUNTIF", "c0", new FormulaNode.Num(new BigDecimal("10")), null);
            assertThat(write(node)).isEqualTo("COUNTIF(A$2:A$4, 10)");
        }
    }

    @Nested
    @DisplayName("표간 참조는 값으로 굳는다")
    class CrossTable {

        @Test
        @DisplayName("다른 표의 셀 참조는 계산된 값이 된다 — 한 시트엔 가리킬 자리가 없다")
        void crossRefFreezesToValue() {
            FormulaNode node = new FormulaNode.Ref(FormulaRefKind.ABSOLUTE, "c0", 1L, 77L);
            assertThat(write(node)).isEqualTo("1450");
        }

        @Test
        @DisplayName("다른 표의 열 집계도 값으로 굳는다")
        void crossAggFreezesToValue() {
            assertThat(write(new FormulaNode.CrossAgg("SUM", 77L, "c0"))).isEqualTo("1450");
        }

        @Test
        @DisplayName("숫자가 아닌 값은 문자열 리터럴로 굳는다")
        void nonNumericFreezesAsString() {
            FormulaA1Writer w = new FormulaA1Writer(LAYOUT, () -> "엔");
            assertThat(w.write(new FormulaNode.CrossAgg("SUM", 77L, "c0"), 3))
                    .isEqualTo("\"엔\"");
        }
    }

    @Nested
    @DisplayName("식")
    class Expressions {

        @Test
        @DisplayName("산술은 우선순위가 이미 트리에 있으므로 괄호로 그대로 굳혀 내보낸다")
        void arithmeticKeepsTreeShape() {
            FormulaNode node = new FormulaNode.Binary('*', sameRow("c0"), sameRow("c1"));
            assertThat(write(node)).isEqualTo("(A3 * B3)");
        }

        @Test
        @DisplayName("스칼라 함수는 이름과 인자가 그대로 통한다")
        void callPassesThrough() {
            FormulaNode node = new FormulaNode.Call("IF", List.of(
                    new FormulaNode.Compare(">", sameRow("c0"), new FormulaNode.Num(BigDecimal.TEN)),
                    new FormulaNode.Str("크다"),
                    new FormulaNode.Str("작다")));
            assertThat(write(node)).isEqualTo("IF((A3 > 10), \"크다\", \"작다\")");
        }

        @Test
        @DisplayName("문자열 안의 큰따옴표는 엑셀 방식으로 두 번 겹쳐 escape한다")
        void quotesAreDoubled() {
            assertThat(write(new FormulaNode.Str("가\"나"))).isEqualTo("\"가\"\"나\"");
        }
    }

    @Test
    @DisplayName("열 번호는 26진 문자로 편다 — 26번째 열이 AA다")
    void columnLetters() {
        assertThat(FormulaA1Writer.toLetters(0)).isEqualTo("A");
        assertThat(FormulaA1Writer.toLetters(25)).isEqualTo("Z");
        assertThat(FormulaA1Writer.toLetters(26)).isEqualTo("AA");
        assertThat(FormulaA1Writer.toLetters(701)).isEqualTo("ZZ");
        assertThat(FormulaA1Writer.toLetters(702)).isEqualTo("AAA");
    }
}
