package ds.project.orino.planner.shortlink;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.planner.shortlink.config.ShortlinkProperties;
import ds.project.orino.planner.shortlink.service.SlugPolicy;
import ds.project.orino.planner.shortlink.service.TargetUrlValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 목적지·슬러그 검증(명세 §3 · §4.3). 스프링 없이 도는 순수 로직이라 단위 테스트로 둔다.
 */
class TargetUrlValidatorTest {

    private final TargetUrlValidator validator =
            new TargetUrlValidator(new ShortlinkProperties("https://s.orino.dev/"));

    @ParameterizedTest
    @ValueSource(strings = {
            "https://img.orino.dev/a.jpg",
            "http://example.com",
            "mailto:dsk08208@gmail.com",
            "tel:+821012345678"
    })
    @DisplayName("허용 스킴은 http · https · mailto · tel 넷이다")
    void acceptsAllowedSchemes(String url) {
        assertThat(validator.validate(url)).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "data:text/html;base64,PHNjcmlwdD4=",
            "file:///etc/passwd",
            "example.com/no-scheme",
            "https://"
    })
    @DisplayName("그 외 스킴과 호스트 없는 주소는 SL-ERR-001")
    void rejectsOtherSchemes(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHORTLINK_INVALID_TARGET);
    }

    @Test
    @DisplayName("죽어 있는 주소도 발급된다 — 도달 가능성은 검사하지 않는다")
    void doesNotCheckReachability() {
        String dead = "https://files.example.invalid/gone.zip?X-Amz-Signature=expired";
        assertThat(validator.validate(dead)).isEqualTo(dead);
    }

    @Test
    @DisplayName("자기 자신을 가리키면 SL-ERR-002 — 리다이렉트가 리다이렉트를 부른다")
    void rejectsSelfReference() {
        assertThatThrownBy(() -> validator.validate("https://S.Orino.dev/ab3k9"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHORTLINK_SELF_REFERENCE);
    }

    @Test
    @DisplayName("2048자를 넘으면 SL-ERR-001")
    void rejectsTooLongTarget() {
        String tooLong = "https://example.com/" + "a".repeat(2048);
        assertThatThrownBy(() -> validator.validate(tooLong))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("짧은 주소는 base-url 끝의 슬래시와 무관하게 한 벌로 조립된다")
    void assemblesShortUrl() {
        assertThat(new ShortlinkProperties("https://s.orino.dev/").shortUrl("ab3k9"))
                .isEqualTo("https://s.orino.dev/ab3k9");
        assertThat(new ShortlinkProperties("https://s.orino.dev").shortUrl("ab3k9"))
                .isEqualTo("https://s.orino.dev/ab3k9");
    }

    @ParameterizedTest
    @ValueSource(strings = {"hello", "je ju", "je-ju", "0ab3k", "l1ne", ""})
    @DisplayName("문자셋·길이를 벗어난 커스텀 슬러그는 SL-ERR-004")
    void rejectsSlugOutsideAlphabet(String slug) {
        assertThatThrownBy(() -> SlugPolicy.normalizeCustom(slug))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHORTLINK_INVALID_SLUG);
    }

    @Test
    @DisplayName("대문자 슬러그는 소문자로 정규화한다")
    void normalizesUppercaseSlug() {
        assertThat(SlugPolicy.normalizeCustom(" JeJu ")).isEqualTo("jeju");
    }
}
