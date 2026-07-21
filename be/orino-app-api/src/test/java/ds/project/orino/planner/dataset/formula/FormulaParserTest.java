package ds.project.orino.planner.dataset.formula;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.dataset.entity.FormulaRefKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 파서·표시 왕복 검증.
 *
 * <p>label은 <b>운영 데이터에서 실제로 쓰이는 것</b>을 그대로 쓴다 — 숫자로 끝나는 {@code 열 1},
 * 공백이 든 {@code SFI Rank}, 괄호가 든 {@code Adjusted Frequency per Million (U)}.
 * 구분자 문법이 이론이 아니라 이 데이터 때문에 필요하다.
 */
class FormulaParserTest {

    /** 운영 데이터의 열 구성을 본뜬 컨텍스트. 행 번호 N ↔ 행 id 100+N. */
    private static class FakeContext implements FormulaContext {
        private final Map<String, String> labelByKey = new LinkedHashMap<>();
        private final int rowCount;

        FakeContext(int rowCount, String... keyLabelPairs) {
            this.rowCount = rowCount;
            for (int i = 0; i < keyLabelPairs.length; i += 2) {
                labelByKey.put(keyLabelPairs[i], keyLabelPairs[i + 1]);
            }
        }

        @Override
        public List<String> columnKeys() {
            return List.copyOf(labelByKey.keySet());
        }

        @Override
        public Optional<String> keyByLabel(String label) {
            return labelByKey.entrySet().stream()
                    .filter(e -> e.getValue().equals(label))
                    .map(Map.Entry::getKey)
                    .findFirst();
        }

        @Override
        public Optional<String> labelByKey(String key) {
            return Optional.ofNullable(labelByKey.get(key));
        }

        @Override
        public Optional<Long> rowIdByNumber(int rowNumber) {
            return rowNumber >= 1 && rowNumber <= rowCount
                    ? Optional.of(100L + rowNumber) : Optional.empty();
        }

        @Override
        public Optional<Integer> rowNumberById(long rowId) {
            int n = (int) (rowId - 100L);
            return n >= 1 && n <= rowCount ? Optional.of(n) : Optional.empty();
        }
    }

    private final FakeContext ctx = new FakeContext(5,
            "c0", "Lemma",
            "c1", "SFI Rank",
            "c2", "SFI",
            "c3", "Adjusted Frequency per Million (U)");

    private final FakeContext simple = new FakeContext(3, "c0", "열 1", "c1", "열 2", "c2", "열 3");

    @Nested
    @DisplayName("구분자 — 실제 label이 요구하는 것")
    class Delimiter {

        @Test
        @DisplayName("공백이 든 label을 통째로 읽는다")
        void labelWithSpace() {
            FormulaNode n = FormulaParser.parseInput("={SFI Rank}", ctx);
            assertThat(n).isEqualTo(new FormulaNode.Ref(FormulaRefKind.SAME_ROW, "c1", null));
        }

        @Test
        @DisplayName("괄호가 든 label을 함수 괄호와 헷갈리지 않는다")
        void labelWithParens() {
            FormulaNode n = FormulaParser.parseInput(
                    "=SUM({Adjusted Frequency per Million (U)})", ctx);
            assertThat(n).isEqualTo(new FormulaNode.Agg("SUM", List.of("c3")));
        }

        @Test
        @DisplayName("숫자로 끝나는 label과 행 번호를 구분한다 — 구분자가 없으면 불가능했던 것")
        void labelEndingWithDigitVsRowNumber() {
            // {열 1} → 같은 행. 뒤에 붙은 2가 행 번호다.
            assertThat(FormulaParser.parseInput("={열 1}2", simple))
                    .isEqualTo(new FormulaNode.Ref(FormulaRefKind.ABSOLUTE, "c0", 102L));
            // 행 번호가 없으면 같은 행.
            assertThat(FormulaParser.parseInput("={열 1}", simple))
                    .isEqualTo(new FormulaNode.Ref(FormulaRefKind.SAME_ROW, "c0", null));
        }
    }

    @Nested
    @DisplayName("참조 3종 바인딩 (D9)")
    class Refs {

        @Test
        @DisplayName("산술 속 열 참조는 같은 행 — 행 id를 쓰지 않는다")
        void arithmeticIsSameRow() {
            FormulaNode n = FormulaParser.parseInput("={SFI Rank} * {SFI}", ctx);
            assertThat(FormulaParser.collectRefs(n)).containsExactly(
                    new FormulaNode.Ref(FormulaRefKind.SAME_ROW, "c1", null),
                    new FormulaNode.Ref(FormulaRefKind.SAME_ROW, "c2", null));
        }

        @Test
        @DisplayName("행 번호를 붙이면 절대 참조 — 파싱 시점에 행 id로 굳는다")
        void rowNumberBecomesRowId() {
            FormulaNode n = FormulaParser.parseInput("={SFI}3", ctx);
            assertThat(n).isEqualTo(new FormulaNode.Ref(FormulaRefKind.ABSOLUTE, "c2", 103L));
        }

        @Test
        @DisplayName("집계 속 열 참조는 열 전체")
        void aggregateIsColumnAll() {
            FormulaNode n = FormulaParser.parseInput("=SUM({SFI})", ctx);
            assertThat(FormulaParser.collectRefs(n)).containsExactly(
                    new FormulaNode.Ref(FormulaRefKind.COLUMN_ALL, "c2", null));
        }

        @Test
        @DisplayName("같은 표기가 문맥에 따라 다르게 바인딩된다")
        void sameNotationDiffersByContext() {
            FormulaNode n = FormulaParser.parseInput("=SUM({SFI}) + {SFI}", ctx);
            assertThat(FormulaParser.collectRefs(n)).containsExactlyInAnyOrder(
                    new FormulaNode.Ref(FormulaRefKind.COLUMN_ALL, "c2", null),
                    new FormulaNode.Ref(FormulaRefKind.SAME_ROW, "c2", null));
        }
    }

    @Nested
    @DisplayName("열 범위 — 입력 시 집합으로 굳는다 (D7)")
    class Range {

        @Test
        @DisplayName("범위는 현재 순서로 펼쳐진다")
        void rangeExpands() {
            FormulaNode n = FormulaParser.parseInput("=SUM({Lemma}:{SFI})", ctx);
            assertThat(n).isEqualTo(new FormulaNode.Agg("SUM", List.of("c0", "c1", "c2")));
        }

        @Test
        @DisplayName("저장형·표시형 어디에도 범위 문법이 남지 않는다")
        void rangeDoesNotSurviveAsRange() {
            FormulaNode n = FormulaParser.parseInput("=SUM({Lemma}:{SFI})", ctx);
            assertThat(FormulaWriter.toStored(n)).isEqualTo("=SUM({c0}, {c1}, {c2})");
            assertThat(FormulaWriter.toDisplay(n, ctx))
                    .isEqualTo("=SUM({Lemma}, {SFI Rank}, {SFI})");
        }

        @Test
        @DisplayName("거꾸로 준 범위도 받아준다")
        void reversedRange() {
            assertThat(FormulaParser.parseInput("=SUM({SFI}:{Lemma})", ctx))
                    .isEqualTo(new FormulaNode.Agg("SUM", List.of("c0", "c1", "c2")));
        }

        @Test
        @DisplayName("열을 나열해도 된다")
        void explicitList() {
            assertThat(FormulaParser.parseInput("=SUM({Lemma}, {SFI})", ctx))
                    .isEqualTo(new FormulaNode.Agg("SUM", List.of("c0", "c2")));
        }
    }

    @Nested
    @DisplayName("왕복 — 저장은 주소로, 표시는 위치로")
    class RoundTrip {

        @Test
        @DisplayName("저장형은 key·행 id로만 이뤄진다")
        void storedHasNoLabels() {
            FormulaNode n = FormulaParser.parseInput("={SFI Rank} * {SFI}3 + 2", ctx);
            assertThat(FormulaWriter.toStored(n))
                    .isEqualTo("=(({c1} * {c2}@103) + 2)")
                    .doesNotContain("SFI");
        }

        @Test
        @DisplayName("저장형을 다시 파싱하면 같은 트리다")
        void storedReparsesIdentically() {
            FormulaNode a = FormulaParser.parseInput("=SUM({Lemma}:{SFI}) / {SFI Rank}2", ctx);
            FormulaNode b = FormulaParser.parseStored(FormulaWriter.toStored(a), ctx);
            assertThat(b).isEqualTo(a);
        }

        @Test
        @DisplayName("열 이름을 바꾸면 저장형은 그대로인데 표시만 새 이름이 된다")
        void renameChangesDisplayOnly() {
            FormulaNode n = FormulaParser.parseInput("={SFI Rank}", ctx);
            String stored = FormulaWriter.toStored(n);

            FakeContext renamed = new FakeContext(5, "c0", "Lemma", "c1", "순위",
                    "c2", "SFI", "c3", "x");
            FormulaNode reparsed = FormulaParser.parseStored(stored, renamed);

            assertThat(FormulaWriter.toStored(reparsed)).isEqualTo(stored);
            assertThat(FormulaWriter.toDisplay(reparsed, renamed)).isEqualTo("={순위}");
        }

        @Test
        @DisplayName("지워진 열·행을 가리키면 표시에서 #REF!")
        void danglingShowsRef() {
            FormulaNode n = FormulaParser.parseInput("={SFI}3", ctx);
            // 그 열도 그 행도 없는 컨텍스트
            FakeContext shrunk = new FakeContext(1, "c0", "Lemma");
            assertThat(FormulaWriter.toDisplay(n, shrunk)).isEqualTo("={#REF!}#REF!");
        }
    }

    @Nested
    @DisplayName("문법 오류")
    class Errors {

        private void expectSyntaxError(String input) {
            assertThatThrownBy(() -> FormulaParser.parseInput(input, ctx))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.FORMULA_SYNTAX_ERROR);
        }

        @Test
        @DisplayName("= 로 시작하지 않으면 오류")
        void mustStartWithEquals() {
            expectSyntaxError("{SFI} + 1");
        }

        @Test
        @DisplayName("없는 열·행이면 오류")
        void unknownRefs() {
            expectSyntaxError("={없는열}");
            expectSyntaxError("={SFI}99");
        }

        @Test
        @DisplayName("닫히지 않은 괄호·중괄호면 오류")
        void unbalanced() {
            expectSyntaxError("={SFI");
            expectSyntaxError("=({SFI}");
            expectSyntaxError("=SUM({SFI}");
        }

        @Test
        @DisplayName("D8 범위 밖 함수면 오류 — IF는 아직 없다")
        void unsupportedFunction() {
            expectSyntaxError("=IF({SFI} > 1, 1, 0)");
            expectSyntaxError("=COUNTA({SFI})");
        }

        @Test
        @DisplayName("집계 안에서 행을 지정하면 오류 — 열 전체와 뜻이 충돌한다")
        void rowInsideAggregate() {
            expectSyntaxError("=SUM({SFI}3)");
        }

        @Test
        @DisplayName("집계 인자에 산술을 넣으면 오류 — 의미가 정의되지 않는다")
        void expressionInsideAggregate() {
            expectSyntaxError("=SUM({SFI} * 2)");
        }

        @Test
        @DisplayName("수식 뒤에 군더더기가 있으면 오류")
        void trailingGarbage() {
            expectSyntaxError("={SFI} 1 2");
        }
    }

    @Nested
    @DisplayName("산술")
    class Arithmetic {

        @Test
        @DisplayName("사칙연산 우선순위를 지킨다")
        void precedence() {
            FormulaNode n = FormulaParser.parseInput("=1 + 2 * 3", ctx);
            assertThat(FormulaWriter.toStored(n)).isEqualTo("=(1 + (2 * 3))");
        }

        @Test
        @DisplayName("괄호로 우선순위를 바꾼다")
        void parens() {
            FormulaNode n = FormulaParser.parseInput("=(1 + 2) * 3", ctx);
            assertThat(FormulaWriter.toStored(n)).isEqualTo("=((1 + 2) * 3)");
        }

        @Test
        @DisplayName("단항 부호")
        void unary() {
            assertThat(FormulaWriter.toStored(FormulaParser.parseInput("=-{SFI}", ctx)))
                    .isEqualTo("=-{c2}");
        }

        @Test
        @DisplayName("소수를 지수 표기 없이 쓴다")
        void decimals() {
            assertThat(FormulaWriter.toStored(FormulaParser.parseInput("=0.50", ctx)))
                    .isEqualTo("=0.5");
        }
    }

    @Nested
    @DisplayName("스칼라 함수 (#893) — 집계와 달리 인자가 식")
    class Scalar {

        @Test
        @DisplayName("ABS는 Call 노드로 파싱된다")
        void absParses() {
            assertThat(FormulaParser.parseInput("=ABS({SFI})", ctx))
                    .isEqualTo(new FormulaNode.Call("ABS",
                            List.of(new FormulaNode.Ref(FormulaRefKind.SAME_ROW, "c2", null))));
        }

        @Test
        @DisplayName("집계와 달리 산술 인자를 받는다")
        void takesExpressionArg() {
            // SUM({SFI} * 2)는 오류지만 ABS({SFI} * 2)는 정상이다.
            assertThat(FormulaWriter.toStored(FormulaParser.parseInput("=ABS({SFI} * 2)", ctx)))
                    .isEqualTo("=ABS(({c2} * 2))");
        }

        @Test
        @DisplayName("저장형·표시형이 같은 모양이고 인자를 재귀로 쓴다")
        void writesBothForms() {
            FormulaNode n = FormulaParser.parseInput("=ABS({SFI Rank} * {SFI})", ctx);
            assertThat(FormulaWriter.toStored(n)).isEqualTo("=ABS(({c1} * {c2}))");
            assertThat(FormulaWriter.toDisplay(n, ctx)).isEqualTo("=ABS(({SFI Rank} * {SFI}))");
        }

        @Test
        @DisplayName("인자 속 참조가 의존성으로 잡힌다")
        void argRefsCollected() {
            FormulaNode n = FormulaParser.parseInput("=ABS({SFI Rank} * {SFI})", ctx);
            assertThat(FormulaParser.collectRefs(n)).containsExactlyInAnyOrder(
                    new FormulaNode.Ref(FormulaRefKind.SAME_ROW, "c1", null),
                    new FormulaNode.Ref(FormulaRefKind.SAME_ROW, "c2", null));
        }

        @Test
        @DisplayName("인자 개수가 안 맞으면 오류")
        void wrongArity() {
            assertThatThrownBy(() -> FormulaParser.parseInput("=ABS()", ctx))
                    .isInstanceOf(CustomException.class);
            assertThatThrownBy(() -> FormulaParser.parseInput("=ABS({SFI}, {SFI})", ctx))
                    .isInstanceOf(CustomException.class);
        }
    }
}
