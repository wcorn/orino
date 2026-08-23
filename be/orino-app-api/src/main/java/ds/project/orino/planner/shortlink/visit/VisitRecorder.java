package ds.project.orino.planner.shortlink.visit;

import ds.project.orino.domain.planner.shortlink.entity.ShortlinkVisit;
import ds.project.orino.domain.planner.shortlink.entity.VisitDevice;
import ds.project.orino.domain.planner.shortlink.repository.ShortlinkVisitDailyRepository;
import ds.project.orino.domain.planner.shortlink.repository.ShortlinkVisitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 방문 기록(아키텍처 §3). <b>리다이렉트와 분리된 비동기 경로다.</b>
 *
 * <p>여기서 무슨 일이 나도 302는 이미 나갔거나 나간다(명세 §6.5). 통계를 잃는 것과 링크가
 * 죽는 것은 비교 대상이 아니므로, 예외는 <b>삼켜서 로그로만</b> 남긴다.
 *
 * <p>원시 INSERT와 일별 UPSERT를 <b>한 트랜잭션에서 함께</b> 한다(D-12). 야간 집계 배치가
 * 없으므로, 이 두 줄이 어긋나면 총계와 그래프가 영영 어긋난 채로 남는다.
 *
 * <p>요청 스코프 값(User-Agent · Referer)은 <b>호출하는 쪽에서 이미 뽑아</b>
 * {@link VisitContext}로 넘어온다 — 다른 스레드에서 {@code HttpServletRequest}를 만지면
 * 요청이 끝난 뒤에는 빈 값이 나온다.
 */
@Component
public class VisitRecorder {

    private static final Logger log = LoggerFactory.getLogger(VisitRecorder.class);

    /** 집계 기준 시간대. 화면의 "오늘"이 사용자 하루와 어긋나면 안 된다(데이터 모델 §2.5). */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ShortlinkVisitRepository visitRepository;
    private final ShortlinkVisitDailyRepository dailyRepository;

    public VisitRecorder(ShortlinkVisitRepository visitRepository,
                         ShortlinkVisitDailyRepository dailyRepository) {
        this.visitRepository = visitRepository;
        this.dailyRepository = dailyRepository;
    }

    /**
     * 방문 한 건을 남긴다. 호출자는 결과를 기다리지 않는다.
     *
     * <p>트랜잭션을 새로 연다({@code REQUIRES_NEW}) — 비동기라 어차피 호출자의 트랜잭션은
     * 여기까지 오지 않지만, 그 사실을 코드에 적어 둔다.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long shortlinkId, VisitContext context) {
        try {
            boolean bot = UserAgents.isBot(context.userAgent());
            VisitDevice device = UserAgents.deviceOf(context.userAgent());
            String referrerDomain = Referrers.domainOf(context.referer());
            Instant visitedAt = context.visitedAt();

            visitRepository.save(new ShortlinkVisit(shortlinkId, visitedAt, referrerDomain,
                    device, null, bot));
            LocalDate visitDate = LocalDate.ofInstant(visitedAt, KST);
            dailyRepository.accumulate(shortlinkId, visitDate, bot ? 0 : 1, bot ? 1 : 0);
        } catch (RuntimeException e) {
            // 여기서 예외가 올라가도 사용자는 이미 목적지에 가 있다. 조용히 잃는다.
            log.warn("shortlink visit not recorded (id={}): {}", shortlinkId, e.getMessage());
        }
    }
}
