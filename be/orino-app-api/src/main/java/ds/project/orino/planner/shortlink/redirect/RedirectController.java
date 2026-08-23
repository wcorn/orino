package ds.project.orino.planner.shortlink.redirect;

import ds.project.orino.domain.planner.shortlink.entity.Shortlink;
import ds.project.orino.domain.planner.shortlink.entity.ShortlinkStatus;
import ds.project.orino.domain.planner.shortlink.repository.ShortlinkRepository;
import ds.project.orino.planner.shortlink.service.SlugPolicy;
import ds.project.orino.redis.planner.shortlink.UnlockAttemptRepository;
import ds.project.orino.planner.shortlink.visit.VisitContext;
import ds.project.orino.planner.shortlink.visit.VisitRecorder;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * 공개 리다이렉트. <b>orino 최초의 비인증 기능 표면</b>이다.
 *
 * <p>공개 주소는 {@code s.orino.dev/{slug}}이고 여기 내부 경로는 {@code /r/{slug}}다 —
 * 게이트웨이가 재작성한다(결정 기록 D-3). 루트에 catch-all을 두지 않는 이유는 그것이
 * 스웨거·정적 리소스·오타 요청을 전부 삼키고, 인증 예외를 루트로 열게 만들기 때문이다.
 * 덕분에 <b>공개 표면의 크기가 {@code permitAll("/r/**")} 한 줄로 보인다.</b>
 *
 * <p>이 컨트롤러가 지키는 계약(명세 §6·§7):
 * <ul>
 *   <li><b>302 + {@code Cache-Control: no-store}.</b> 301이거나 캐시가 걸리면 목적지 교체가
 *       먹지 않는다 — 이 모듈의 존재 이유가 깨진다</li>
 *   <li><b>실패는 404 하나.</b> 없음 · 꺼짐 · 만료 · 삭제가 상태 · 본문 · 헤더까지 같다.
 *       410을 쓰지 않는다 — 410은 "예전엔 있었다"를 알려준다</li>
 *   <li>{@code Referrer-Policy: no-referrer} — 목적지 사이트가 유효한 슬러그를 알 이유가 없다</li>
 * </ul>
 *
 * <p>관리 API와 <b>독립</b>이다(명세 §6.1). {@code /links}가 깨져도, FE 배포가 실패해도
 * 이미 뿌린 링크는 살아 있어야 한다.
 */
@RestController
@RequestMapping("/r")
public class RedirectController {

    private static final Logger log = LoggerFactory.getLogger(RedirectController.class);

    /** 명세 §10. 틀렸을 때 이유를 나누지 않는 대신, 무한히 틀려 보지도 못하게 한다. */
    private static final int MAX_ATTEMPTS_PER_MINUTE = 10;

    private final ShortlinkRepository shortlinkRepository;
    private final ShortlinkFailurePage failurePage;
    private final VisitRecorder visitRecorder;
    private final ShortlinkPasswordPage passwordPage;
    private final UnlockAttemptRepository unlockAttempts;
    private final BCryptPasswordEncoder passwordEncoder;
    private final Clock clock;

    public RedirectController(ShortlinkRepository shortlinkRepository,
                              ShortlinkFailurePage failurePage,
                              VisitRecorder visitRecorder,
                              ShortlinkPasswordPage passwordPage,
                              UnlockAttemptRepository unlockAttempts,
                              BCryptPasswordEncoder passwordEncoder,
                              Clock clock) {
        this.shortlinkRepository = shortlinkRepository;
        this.failurePage = failurePage;
        this.visitRecorder = visitRecorder;
        this.passwordPage = passwordPage;
        this.unlockAttempts = unlockAttempts;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @GetMapping("/{slug}")
    @Transactional(readOnly = true)
    public ResponseEntity<String> redirect(@PathVariable String slug, HttpServletRequest request) {
        Optional<Shortlink> link = resolve(slug);
        if (link.isEmpty()) {
            return notFound();
        }
        if (link.get().hasPassword()) {
            // 여기서 "이 슬러그는 존재한다"가 드러난다. 알고 켠 예외다(명세 §10 · D-10).
            return passwordForm(ShortlinkPasswordPage.ASK, HttpStatus.OK);
        }
        return open(link.get(), request);
    }

    /**
     * 비밀번호 확인. 확인 화면의 form이 <b>지금 주소로 그대로</b> POST한 것을
     * 게이트웨이가 여기로 재작성해 보낸다.
     *
     * <p><b>통과해도 쿠키·세션을 만들지 않는다</b>(명세 §10). 다음 방문에 다시 입력한다 —
     * 인증 표면을 늘리지 않기 위해 불편을 택한다.
     *
     * <p>비밀번호가 걸리지 않은 슬러그에 POST가 오면 <b>GET과 같은 판정</b>으로 떨어진다.
     * "이 링크에는 비밀번호가 없습니다"는 그 자체로 정보다.
     */
    // 두 경로를 모두 받는다. 게이트웨이는 POST를 /r/{slug}/unlock으로 재작성하지만(D-3),
    // 확인 화면의 form은 "지금 주소로" 보내므로 재작성 규칙이 빠지면 그대로 /r/{slug}에 닿는다.
    // 그때 405로 죽는 대신 같은 판정을 하게 둔다 — 공개 표면에서 설정 하나로 기능이 사라지는
    // 것보다, 같은 핸들러를 두 이름으로 부르는 편이 낫다.
    @PostMapping({"/{slug}/unlock", "/{slug}"})
    @Transactional(readOnly = true)
    public ResponseEntity<String> unlock(@PathVariable String slug,
                                         @RequestParam(required = false) String password,
                                         HttpServletRequest request) {
        Optional<Shortlink> link = resolve(slug);
        if (link.isEmpty()) {
            return notFound();
        }
        if (!link.get().hasPassword()) {
            return open(link.get(), request);
        }
        if (windowFull(link.get().getSlug())) {
            // 창이 찼으면 맞는 비밀번호도 기다린다 — 그래야 대입이 실제로 막힌다.
            return passwordForm(ShortlinkPasswordPage.TOO_MANY, HttpStatus.TOO_MANY_REQUESTS);
        }
        if (password == null || !passwordEncoder.matches(password, link.get().getPasswordHash())) {
            countFailure(link.get().getSlug());
            return passwordForm(ShortlinkPasswordPage.WRONG, HttpStatus.OK);
        }
        return open(link.get(), request);
    }

    /** 열어 준다 — 방문을 세고 302를 낸다. GET과 unlock이 같은 경로를 쓴다. */
    private ResponseEntity<String> open(Shortlink link, HttpServletRequest request) {
        recordVisit(link, request);
        return found(TargetUrlAssembler.assemble(link.getTargetUrl(), request.getQueryString()));
    }

    /**
     * 슬러그당 분당 10회(명세 §10). <b>세는 것은 실패한 시도뿐이다.</b>
     *
     * <p>성공까지 세면 <b>비밀번호를 아는 사람이 스스로 잠긴다</b> — 세션을 만들지 않기로 했으니
     * (명세 §10) 열 때마다 입력해야 하고, 사진 여러 장을 이어 보는 정도로도 10회를 넘긴다.
     * 막으려는 것은 모르는 사람의 대입이고, 그건 실패 횟수로 충분히 잡힌다.
     *
     * <p><b>세지 못하면 통과시킨다</b> — Redis가 흔들릴 때 열리던 링크가 닫히는 쪽이 더 나쁘다.
     */
    private boolean windowFull(String slug) {
        try {
            return unlockAttempts.count(slug, currentMinute()) >= MAX_ATTEMPTS_PER_MINUTE;
        } catch (RuntimeException e) {
            log.warn("unlock attempts not read (slug={}): {}", slug, e.getMessage());
            return false;
        }
    }

    private void countFailure(String slug) {
        try {
            unlockAttempts.increment(slug, currentMinute());
        } catch (RuntimeException e) {
            log.warn("unlock failure not counted (slug={}): {}", slug, e.getMessage());
        }
    }

    private long currentMinute() {
        return clock.instant().getEpochSecond() / 60;
    }

    private ResponseEntity<String> passwordForm(String message, HttpStatus status) {
        return ResponseEntity.status(status)
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .body(passwordPage.html(message));
    }

    /**
     * 방문을 비동기로 남긴다(명세 §6.5). <b>여는 데 실패한 링크는 세지 않는다</b> —
     * 404로 끝난 요청은 방문이 아니다.
     *
     * <p>제출 자체가 실패할 수도 있다(스레드 풀 포화 등). 그것까지 여기서 삼킨다 —
     * 통계를 잃는 것과 링크가 죽는 것은 비교 대상이 아니다.
     */
    private void recordVisit(Shortlink link, HttpServletRequest request) {
        try {
            // 요청 스코프 값은 지금 뽑는다. 비동기 스레드에서는 이 요청이 이미 끝나 있다.
            VisitContext context = new VisitContext(
                    request.getHeader(HttpHeaders.USER_AGENT),
                    request.getHeader(HttpHeaders.REFERER),
                    clock.instant());
            visitRecorder.record(link.getId(), context);
        } catch (RuntimeException e) {
            log.warn("shortlink visit not submitted (slug={}): {}", link.getSlug(), e.getMessage());
        }
    }

    /**
     * 열어 줄 수 있는 링크만 돌려준다.
     *
     * <p>네 가지 실패를 <b>한곳에서</b> 판정한다 — 없음 · 꺼짐 · 만료 · 삭제. 어느 쪽이든
     * 빈 값이 되어 같은 404로 떨어지고, 그래서 응답이 갈릴 자리가 생기지 않는다.
     *
     * <p><b>비밀번호는 여기서 거르지 않는다.</b> 비밀번호가 걸린 링크는 "없는 링크"가 아니라
     * "한 번 더 물어보는 링크"이고, 그 갈림은 호출하는 쪽에서 한다.
     */
    private Optional<Shortlink> resolve(String slug) {
        Instant now = clock.instant();
        return shortlinkRepository.findBySlug(SlugPolicy.normalize(slug))
                .filter(link -> !link.isDeleted())
                .filter(link -> link.getStatus() == ShortlinkStatus.ACTIVE)
                .filter(link -> !link.isExpiredAt(now));
    }

    private ResponseEntity<String> found(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    private ResponseEntity<String> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                // charset을 명시한다 — 문구가 한글이고, 이 화면은 FE가 아니라 여기서 끝난다.
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .body(failurePage.html());
    }
}
