package ds.project.orino.planner.travel.push;

import ds.project.orino.domain.planner.push.entity.PushSubscription;
import ds.project.orino.planner.travel.push.send.WebPushSender;

import java.util.ArrayList;
import java.util.List;

/**
 * 테스트용 발송 스텁.
 *
 * <p>진짜 푸시 서비스를 부르면 결과를 확인할 수도 없고, 무엇보다 <b>무엇을 보냈는지</b>를
 * 들여다볼 수 없다. 페이로드를 그대로 모아 둔다 — 제목을 발송 시점에 조립하는지가
 * 이 기능의 핵심이라 그걸 봐야 한다.
 */
public class StubWebPushSender implements WebPushSender {

    public record Sent(String endpoint, String payload) {
    }

    public final List<Sent> sent = new ArrayList<>();

    /** 다음 발송의 결과. 실패·죽은 구독 경로를 태울 때 바꾼다. */
    public Result nextResult = Result.ok();

    @Override
    public Result send(PushSubscription subscription, String payloadJson) {
        sent.add(new Sent(subscription.getEndpoint(), payloadJson));
        return nextResult;
    }

    public void reset() {
        sent.clear();
        nextResult = Result.ok();
    }
}
