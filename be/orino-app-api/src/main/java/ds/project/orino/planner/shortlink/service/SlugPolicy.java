package ds.project.orino.planner.shortlink.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;

import java.util.Locale;

/**
 * 슬러그 문자셋과 정규화 규칙(명세 §3).
 *
 * <p>문자셋은 영소문자 + 숫자에서 <b>{@code 0} {@code o} {@code 1} {@code l}을 뺀 32자</b>다 —
 * 입으로 부르거나 손으로 옮겨 적을 때 갈리는 글자들이다. 커스텀 슬러그도 같은 문자셋을 쓴다:
 * 게이트웨이가 문자셋 밖 요청을 BE에 닿기 전에 걸러내므로(결정 기록 D-3), 문자셋을 벗어난
 * 커스텀 슬러그는 <b>발급되더라도 열리지 않는다</b>.
 *
 * <p>대문자로 들어온 슬러그는 소문자로 정규화한다. 저장도 조회도 소문자 하나뿐이다.
 */
public final class SlugPolicy {

    /** 32자. 순서가 바뀌어도 무해하지만 글자 구성이 바뀌면 이미 뿌린 주소와 어긋난다. */
    public static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";

    /** 자동 발급 길이. 이 값이 명세 §3의 "17자 고정"을 만든다(11 + 1 + 5). */
    public static final int AUTO_LENGTH = 5;

    /** 커스텀 슬러그 상한. */
    public static final int MAX_LENGTH = 32;

    private SlugPolicy() {
    }

    /**
     * 커스텀 슬러그를 정규화하고 문자셋·길이를 검사한다.
     *
     * @throws CustomException {@code SL-ERR-004} 문자셋·길이 위반
     */
    public static String normalizeCustom(String raw) {
        String slug = normalize(raw);
        if (slug.isEmpty() || slug.length() > MAX_LENGTH) {
            throw new CustomException(ErrorCode.SHORTLINK_INVALID_SLUG);
        }
        for (int i = 0; i < slug.length(); i++) {
            if (ALPHABET.indexOf(slug.charAt(i)) < 0) {
                throw new CustomException(ErrorCode.SHORTLINK_INVALID_SLUG);
            }
        }
        return slug;
    }

    /** 조회용 정규화. 대문자로 들어와도 같은 링크를 찾는다. */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }
}
