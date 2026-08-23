package ds.project.orino.planner.shortlink.redirect;

import ds.project.orino.domain.planner.shortlink.entity.Shortlink;
import ds.project.orino.domain.planner.shortlink.entity.ShortlinkStatus;
import ds.project.orino.domain.planner.shortlink.repository.ShortlinkRepository;
import ds.project.orino.planner.shortlink.service.SlugPolicy;
import ds.project.orino.planner.shortlink.visit.VisitContext;
import ds.project.orino.planner.shortlink.visit.VisitRecorder;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    private final ShortlinkRepository shortlinkRepository;
    private final ShortlinkFailurePage failurePage;
    private final VisitRecorder visitRecorder;
    private final Clock clock;

    public RedirectController(ShortlinkRepository shortlinkRepository,
                              ShortlinkFailurePage failurePage,
                              VisitRecorder visitRecorder,
                              Clock clock) {
        this.shortlinkRepository = shortlinkRepository;
        this.failurePage = failurePage;
        this.visitRecorder = visitRecorder;
        this.clock = clock;
    }

    @GetMapping("/{slug}")
    @Transactional(readOnly = true)
    public ResponseEntity<String> redirect(@PathVariable String slug, HttpServletRequest request) {
        return resolve(slug)
                .map(link -> {
                    recordVisit(link, request);
                    return found(TargetUrlAssembler.assemble(
                            link.getTargetUrl(), request.getQueryString()));
                })
                .orElseGet(this::notFound);
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
     * <p>비밀번호가 걸린 링크도 지금은 열어 주지 않는다. 확인 화면(#1244)이 아직 없으므로
     * <b>닫히는 쪽으로 실패한다</b> — 사용자가 비밀번호를 걸어 둔 링크를 확인 없이 통과시키는
     * 것보다, 잠시 열리지 않는 편이 낫다.
     */
    private Optional<Shortlink> resolve(String slug) {
        Instant now = clock.instant();
        return shortlinkRepository.findBySlug(SlugPolicy.normalize(slug))
                .filter(link -> !link.isDeleted())
                .filter(link -> link.getStatus() == ShortlinkStatus.ACTIVE)
                .filter(link -> !link.isExpiredAt(now))
                .filter(link -> !link.hasPassword());
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
