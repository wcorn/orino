package ds.project.orino.planner.shortlink;

import ds.project.orino.planner.shortlink.redirect.TargetUrlAssembler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code Location} 조립 규칙(명세 §6.4 · 결정 기록 D-13). 스프링 없이 도는 순수 로직이다.
 */
class TargetUrlAssemblerTest {

    private static final String SIGNED =
            "https://img.orino.dev/photos/aug.zip?X-Amz-Algorithm=AWS4-HMAC-SHA256"
                    + "&X-Amz-Signature=deadbeef";

    @Nested
    @DisplayName("방문 쿼리가 없을 때")
    class WithoutVisitQuery {

        @Test
        @DisplayName("목적지를 그대로 쓴다 — ?를 새로 붙이지 않는다")
        void keepsTargetUntouched() {
            assertThat(TargetUrlAssembler.assemble("https://example.com/a", null))
                    .isEqualTo("https://example.com/a");
            assertThat(TargetUrlAssembler.assemble("https://example.com/a", ""))
                    .isEqualTo("https://example.com/a");
        }
    }

    @Nested
    @DisplayName("방문 쿼리가 있을 때")
    class WithVisitQuery {

        @Test
        @DisplayName("목적지에 쿼리가 없으면 ?로, 있으면 &로 이어붙인다")
        void appendsWithCorrectSeparator() {
            assertThat(TargetUrlAssembler.assemble("https://example.com/a", "utm=kakao"))
                    .isEqualTo("https://example.com/a?utm=kakao");
            assertThat(TargetUrlAssembler.assemble("https://example.com/a?page=2", "utm=kakao"))
                    .isEqualTo("https://example.com/a?page=2&utm=kakao");
        }

        @Test
        @DisplayName("프래그먼트 앞에 붙인다 — 뒤에 붙이면 목적지 서버가 영영 보지 못한다")
        void insertsBeforeFragment() {
            assertThat(TargetUrlAssembler.assemble("https://example.com/a#top", "utm=kakao"))
                    .isEqualTo("https://example.com/a?utm=kakao#top");
        }
    }

    @Nested
    @DisplayName("서명된 목적지")
    class SignedTarget {

        @Test
        @DisplayName("방문 쿼리를 버린다 — 파라미터 하나면 서명 검증이 깨진다")
        void dropsVisitQuery() {
            assertThat(TargetUrlAssembler.assemble(SIGNED, "utm=kakao")).isEqualTo(SIGNED);
        }

        @Test
        @DisplayName("X-Goog-Signature · Signature도 같게 다룬다(대소문자 무관)")
        void recognizesOtherSignatureParams() {
            String goog = "https://storage.example.com/a?x-goog-signature=abc";
            String plain = "https://cdn.example.com/a?expires=1&Signature=abc";
            assertThat(TargetUrlAssembler.assemble(goog, "utm=x")).isEqualTo(goog);
            assertThat(TargetUrlAssembler.assemble(plain, "utm=x")).isEqualTo(plain);
        }

        @Test
        @DisplayName("경로에 우연히 들어간 문자열에는 속지 않는다 — 파라미터 이름만 본다")
        void looksAtParameterNamesOnly() {
            String path = "https://example.com/signature/guide?page=2";
            assertThat(TargetUrlAssembler.assemble(path, "utm=x"))
                    .isEqualTo("https://example.com/signature/guide?page=2&utm=x");
        }

        @Test
        @DisplayName("값에 signature가 들어 있어도 파라미터 이름이 아니면 전달한다")
        void ignoresSignatureInValues() {
            String value = "https://example.com/a?note=signature";
            assertThat(TargetUrlAssembler.assemble(value, "utm=x"))
                    .isEqualTo("https://example.com/a?note=signature&utm=x");
        }
    }
}
