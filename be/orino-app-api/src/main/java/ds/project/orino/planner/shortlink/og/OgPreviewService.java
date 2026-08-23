package ds.project.orino.planner.shortlink.og;

import ds.project.orino.domain.planner.shortlink.repository.ShortlinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * OG 프리뷰(SL-005). <b>발급 흐름과 완전히 분리된 경로다</b>(명세 §4.4).
 *
 * <p>이 서비스가 통째로 죽어도 링크는 발급된다. 그래서 여기서 나는 모든 예외를 삼키고,
 * 화면에는 프리뷰가 안 뜨는 정도로 보인다 — <b>느리거나 실패해도 「만들기」는 항상 눌린다.</b>
 *
 * <p>모든 fetch는 {@link SsrfGuard}를 거친다. 방어 없이 이 기능을 켜지 않는다(D-11).
 */
@Service
public class OgPreviewService {

    private static final Logger log = LoggerFactory.getLogger(OgPreviewService.class);

    private final OgPreviewClient client;
    private final ShortlinkRepository shortlinkRepository;
    private final OgPreviewProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public OgPreviewService(OgPreviewClient client,
                            ShortlinkRepository shortlinkRepository,
                            OgPreviewProperties properties,
                            TransactionTemplate transactionTemplate,
                            Clock clock) {
        this.client = client;
        this.shortlinkRepository = shortlinkRepository;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /**
     * 모달이 부르는 조회. 실패하면 빈 결과다 — <b>이유를 나누지 않는다</b>(아키텍처 §5).
     */
    public OgPreviewResult preview(String url) {
        if (!properties.enabled()) {
            return OgPreviewResult.empty();
        }
        try {
            return client.fetchHtml(url).map(OgHtmlParser::parse).orElseGet(OgPreviewResult::empty);
        } catch (RuntimeException e) {
            log.debug("og preview failed");
            return OgPreviewResult.empty();
        }
    }

    /**
     * 발급 뒤 뒤따라 채운다. <b>발급 트랜잭션에 얹지 않는다</b> — 프리뷰 fetch가 느리면
     * 그만큼 발급이 늦어지고, 그러면 「붙여넣고 Enter」가 3초를 넘긴다(명세 §4.1).
     *
     * <p>{@code ogFetchedAt}은 결과가 비어 있어도 남긴다. "아직 시도 안 함"과 "해 봤는데
     * 없더라"를 구분하지 못하면 같은 URL을 계속 다시 긁는다.
     */
    @Async
    public void fillAsync(Long shortlinkId, String targetUrl) {
        if (!properties.enabled()) {
            return;
        }
        try {
            OgPreviewResult result = preview(targetUrl);
            // 트랜잭션을 메서드가 아니라 여기 안쪽에 둔다 — @Transactional이면 커밋이 메서드
            // 밖에서 일어나고, 그 사이 링크가 지워졌을 때 나는 실패를 이 try가 못 잡는다.
            transactionTemplate.executeWithoutResult(status ->
                    shortlinkRepository.findById(shortlinkId).ifPresent(link -> {
                        link.updateOgPreview(result.title(), result.imageUrl(), clock.instant());
                        shortlinkRepository.save(link);
                    }));
        } catch (RuntimeException e) {
            // 프리뷰를 못 채운 것뿐이다. 링크는 이미 살아 있다(발급 직후 지워졌을 수도 있다).
            log.debug("og preview not stored (id={})", shortlinkId);
        }
    }
}
