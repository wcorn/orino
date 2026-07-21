package ds.project.orino.planner.dataset.formula;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수식 <b>평가</b>의 골든(conformance) 스위트. 파서 트리 모양이 아니라 "입력 수식 → 셀에 담길 값"을
 * 데이터 주도로 못 박는다.
 *
 * <p>이 스위트가 존재하는 이유(#892 §13 ADR-2): 나중에 반응성용 FE 경량 평가기를 붙일 때
 * <b>BE와 FE 두 구현을 교차검증할 SSOT</b>가 된다. 그래서 기능이 아니라 스켈레톤 단계(#893)부터 심는다 —
 * 지금 기존 동작(산술·참조·집계·에러 전파)과 새 스칼라 함수({@code ABS})를 골든으로 봉인해 둔다.
 */
class FormulaConformanceTest {

    /**
     * 인메모리 표. {@link FormulaContext}(파싱용)와 {@link FormulaEvaluator.ValueSource}(평가용)를 겸한다.
     * 행 번호 N(1-base) ↔ 행 id 100+N. sameRow는 0번 행(id 101)을 현재 행으로 본다.
     */
    private static final class Model
            implements FormulaContext, FormulaEvaluator.ValueSource {

        private final Map<String, String> labelByKey = new LinkedHashMap<>();
        private final List<Map<String, String>> rows = new ArrayList<>();
        private static final int CURRENT = 0;

        Model column(String key, String label) {
            labelByKey.put(key, label);
            return this;
        }

        /** 열 순서대로 셀 값을 받아 한 행을 붙인다. */
        Model row(String... cells) {
            Map<String, String> row = new LinkedHashMap<>();
            List<String> keys = List.copyOf(labelByKey.keySet());
            for (int i = 0; i < keys.size(); i++) {
                row.put(keys.get(i), i < cells.length ? cells[i] : "");
            }
            rows.add(row);
            return this;
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
            return rowNumber >= 1 && rowNumber <= rows.size()
                    ? Optional.of(100L + rowNumber) : Optional.empty();
        }

        @Override
        public Optional<Integer> rowNumberById(long rowId) {
            int n = (int) (rowId - 100L);
            return n >= 1 && n <= rows.size() ? Optional.of(n) : Optional.empty();
        }

        @Override
        public Optional<String> sameRow(String colKey) {
            return labelByKey.containsKey(colKey)
                    ? Optional.of(rows.get(CURRENT).get(colKey)) : Optional.empty();
        }

        @Override
        public Optional<String> absolute(long rowId, String colKey) {
            int n = (int) (rowId - 100L);
            if (n < 1 || n > rows.size() || !labelByKey.containsKey(colKey)) {
                return Optional.empty();
            }
            return Optional.of(rows.get(n - 1).get(colKey));
        }

        @Override
        public Optional<List<String>> column(String colKey) {
            if (!labelByKey.containsKey(colKey)) {
                return Optional.empty();
            }
            return Optional.of(rows.stream().map(r -> r.get(colKey)).toList());
        }
    }

    /** 운영에 가까운 작은 표. 현재 행(1행)은 수량=2·단가=3·메모=a·재고 비었고·상태는 에러값. */
    private static Model model() {
        return new Model()
                .column("c0", "수량")
                .column("c1", "단가")
                .column("c2", "메모")
                .column("c3", "재고")
                .column("c4", "상태")
                //       수량   단가   메모   재고   상태
                .row("2", "3", "a", "", "#DIV/0!")
                .row("4", "5", "", "1", "")
                .row("", "10", "x", "2", "");
    }

    private static Stream<Arguments> cases() {
        return Stream.of(
                // ── 산술 ──
                Arguments.of("사칙연산 우선순위", "=1 + 2 * 3", "7"),
                Arguments.of("같은 행 참조 곱", "={수량} * {단가}", "6"),
                Arguments.of("빈 셀은 0", "={재고} + {수량}", "2"),
                Arguments.of("0으로 나누면 #DIV/0!", "=1 / 0", "#DIV/0!"),
                // ── 참조 에러 ──
                Arguments.of("텍스트를 산술하면 #VALUE!", "={메모} + 1", "#VALUE!"),
                Arguments.of("에러 셀은 그대로 번진다", "={상태} + 1", "#DIV/0!"),
                Arguments.of("절대 참조(행 번호)", "={단가}2", "5"),
                // ── 집계 ──
                Arguments.of("SUM은 빈 셀을 무시", "=SUM({수량})", "6"),
                Arguments.of("COUNT는 숫자만 센다", "=COUNT({수량})", "2"),
                Arguments.of("AVG", "=AVG({단가})", "6"),
                Arguments.of("숫자 없는 열 AVG는 #DIV/0!", "=AVG({메모})", "#DIV/0!"),
                Arguments.of("MIN", "=MIN({단가})", "3"),
                Arguments.of("MAX", "=MAX({단가})", "10"),
                Arguments.of("집계는 텍스트를 무시(합 0)", "=SUM({메모})", "0"),
                // ── 스칼라 함수 ABS (#893) ──
                Arguments.of("ABS 리터럴", "=ABS(-5)", "5"),
                Arguments.of("ABS 식 인자", "=ABS({수량} - {단가})", "1"),
                Arguments.of("ABS 빈 셀 인자", "=ABS({재고} - 3)", "3"),
                Arguments.of("ABS 인자 에러 전파", "=ABS({메모})", "#VALUE!"),
                // ── 비교 → boolean (#895) ──
                Arguments.of("숫자 비교", "={수량} < {단가}", "TRUE"),
                Arguments.of("숫자 같음", "={수량} = 2", "TRUE"),
                Arguments.of("다름", "={수량} <> 2", "FALSE"),
                Arguments.of("텍스트 비교(대소문자 무시)", "={메모} = \"A\"", "TRUE"),
                Arguments.of("교차 타입: 텍스트 > 숫자", "={메모} > 999", "TRUE"),
                Arguments.of("비교 피연산자 에러 전파", "={상태} = 1", "#DIV/0!"),
                // ── IF (#895) ──
                Arguments.of("IF 참 가지", "=IF({수량} = 2, {단가}, -1)", "3"),
                Arguments.of("IF 거짓 가지", "=IF({수량} = 9, {단가}, -1)", "-1"),
                Arguments.of("환율 자동환산 예시", "=IF({메모} = \"a\", {수량} * 10, {수량})", "20"),
                Arguments.of("IF 지연 평가 — 안 고른 가지 에러 무시", "=IF({수량} > 0, 7, 1 / 0)", "7"),
                Arguments.of("IF 조건 에러 전파", "=IF({상태} = 1, 1, 0)", "#DIV/0!"),
                // ── AND/OR/NOT (#895) ──
                Arguments.of("AND", "=AND({수량} = 2, {단가} = 3)", "TRUE"),
                Arguments.of("AND 하나 거짓", "=AND({수량} = 2, {단가} = 9)", "FALSE"),
                Arguments.of("OR", "=OR({수량} = 9, {단가} = 3)", "TRUE"),
                Arguments.of("NOT", "=NOT({수량} = 2)", "FALSE"),
                Arguments.of("숫자 0은 거짓", "=NOT({재고})", "TRUE"),
                Arguments.of("문자열 인자는 #VALUE!", "=AND({메모}, 1 = 1)", "#VALUE!"),
                // ── 산술이 boolean·문자열을 만나면 ──
                Arguments.of("boolean은 산술에서 1", "=(1 < 2) + 5", "6"),
                Arguments.of("문자열 산술은 #VALUE!", "=\"a\" + 1", "#VALUE!"),
                // ── 조건부 집계 SUMIF·COUNTIF (#897) ──
                Arguments.of("COUNTIF 텍스트 정확일치", "=COUNTIF({메모}, \"a\")", "1"),
                Arguments.of("COUNTIF 숫자 정확일치", "=COUNTIF({단가}, 3)", "1"),
                Arguments.of("COUNTIF 연산자 criteria", "=COUNTIF({단가}, \">4\")", "2"),
                Arguments.of("COUNTIF 부정 — 빈 셀은 스킵", "=COUNTIF({메모}, \"<>a\")", "1"),
                Arguments.of("SUMIF 텍스트 조건", "=SUMIF({메모}, \"a\", {수량})", "2"),
                Arguments.of("SUMIF 숫자 조건 — 합 열 빈칸 무시", "=SUMIF({단가}, \">4\", {수량})", "4"),
                Arguments.of("SUMIF — 조건 열 빈 행 스킵", "=SUMIF({수량}, \">0\", {단가})", "8"),
                Arguments.of("criteria가 식이어도 된다", "=COUNTIF({수량}, {단가} - 1)", "1"),
                Arguments.of("조건 열 에러는 번진다", "=COUNTIF({상태}, \"x\")", "#DIV/0!"));
    }

    @ParameterizedTest(name = "{0}: {1} → {2}")
    @MethodSource("cases")
    @DisplayName("입력 수식 → 셀 값 (골든)")
    void evaluatesTo(String name, String formula, String expected) {
        Model m = model();
        FormulaNode node = FormulaParser.parseInput(formula, m);
        assertThat(FormulaEvaluator.evaluate(node, m).asCell()).isEqualTo(expected);
    }
}
