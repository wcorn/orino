package ds.project.orino.planner.shortlink.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.planner.shortlink.config.ShortlinkProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * 목적지 검증(명세 §4.3).
 *
 * <p><b>도달 가능성은 검사하지 않는다.</b> 지금 죽어 있는 URL도 발급된다 — 나중에 갈아끼우면
 * 되고, 애초에 "죽은 목적지를 살리는 것"이 이 모듈의 용도다.
 *
 * <p>막는 것은 둘뿐이다: 브라우저가 위험하게 해석할 스킴({@code javascript:} 등)과,
 * 자기 자신을 가리켜 루프가 되는 주소.
 */
@Component
public class TargetUrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https", "mailto", "tel");
    private static final int MAX_LENGTH = 2048;

    private final ShortlinkProperties properties;

    public TargetUrlValidator(ShortlinkProperties properties) {
        this.properties = properties;
    }

    /**
     * 검증하고 저장할 형태로 다듬는다(양끝 공백 제거). 대소문자는 건드리지 않는다 —
     * 경로와 쿼리는 대소문자를 구분하고, 서명된 URL에서는 한 글자만 달라져도 서명이 깨진다.
     *
     * @throws CustomException {@code SL-ERR-001} 형식·스킴 위반 · {@code SL-ERR-002} 자기참조
     */
    public String validate(String raw) {
        String target = raw == null ? "" : raw.strip();
        if (target.isEmpty() || target.length() > MAX_LENGTH) {
            throw new CustomException(ErrorCode.SHORTLINK_INVALID_TARGET);
        }

        URI uri = parse(target);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new CustomException(ErrorCode.SHORTLINK_INVALID_TARGET);
        }

        if (scheme.equals("http") || scheme.equals("https")) {
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new CustomException(ErrorCode.SHORTLINK_INVALID_TARGET);
            }
            // 자기 자신을 가리키는 링크는 루프다. 리다이렉트가 리다이렉트를 부른다.
            String shortlinkHost = properties.host();
            if (!shortlinkHost.isEmpty() && host.toLowerCase(Locale.ROOT).equals(shortlinkHost)) {
                throw new CustomException(ErrorCode.SHORTLINK_SELF_REFERENCE);
            }
        }
        return target;
    }

    private URI parse(String target) {
        try {
            return new URI(target);
        } catch (URISyntaxException e) {
            throw new CustomException(ErrorCode.SHORTLINK_INVALID_TARGET);
        }
    }
}
