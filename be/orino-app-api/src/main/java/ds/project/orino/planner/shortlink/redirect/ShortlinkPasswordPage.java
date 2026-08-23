package ds.project.orino.planner.shortlink.redirect;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * 비밀번호 확인 화면(명세 §10 · 화면 설계 §8). <b>404 단일 원칙의 명시적 예외다.</b>
 *
 * <p>이 화면이 뜨는 순간 <b>"이 슬러그는 존재한다"가 드러난다</b> — 없는 슬러그는 여전히
 * 404이므로 방문자는 둘을 구분할 수 있다. 알고 켠 예외이고(D-10), 그래서 기본은 꺼짐이다.
 *
 * <p>실패 문구를 <b>나누지 않는다</b>: 틀렸을 때와 시도가 많을 때 말고는 전부 같은 화면이다.
 * 특히 "비밀번호가 없는 링크입니다" 같은 문구를 만들지 않는다 — 그건 방문자에게
 * 링크의 성질을 알려주는 것이다.
 *
 * <p>{@code <form>}에 {@code action}이 없다. 지금 주소로 그대로 POST되므로
 * <b>공개 주소({@code s.orino.dev/{slug}})가 HTML에 박히지 않는다</b> — 내부 경로도 마찬가지다.
 */
@Component
public class ShortlinkPasswordPage {

    private static final String RESOURCE_PATH = "shortlink/password.html";
    private static final String MESSAGE_TOKEN = "__MESSAGE__";

    public static final String ASK = "비밀번호를 입력해 주세요";
    public static final String WRONG = "비밀번호가 맞지 않아요";
    public static final String TOO_MANY = "잠시 후 다시 시도해 주세요";

    private final String template;

    public ShortlinkPasswordPage() {
        this.template = load();
    }

    public String html(String message) {
        return template.replace(MESSAGE_TOKEN, message);
    }

    private static String load() {
        try {
            return new ClassPathResource(RESOURCE_PATH).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("확인 화면 리소스를 읽지 못했습니다: " + RESOURCE_PATH, e);
        }
    }
}
