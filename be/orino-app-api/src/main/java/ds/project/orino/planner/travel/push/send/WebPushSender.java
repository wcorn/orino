package ds.project.orino.planner.travel.push.send;

import ds.project.orino.domain.planner.push.entity.PushSubscription;

/**
 * 구독 하나에 알림을 보낸다.
 *
 * <p>인터페이스로 둔다 — 테스트에서 진짜 푸시 서비스를 부르면 결과를 확인할 수도 없고,
 * 무엇보다 <b>보냈는지 여부</b>를 세어 검증할 수 없다.
 */
public interface WebPushSender {

    Result send(PushSubscription subscription, String payloadJson);

    /**
     * @param delivered   푸시 서비스가 받아들였다(2xx)
     * @param subscriptionGone 410·404 — 죽은 구독이라 지워야 한다
     * @param reason      실패 사유. 성공이면 null
     */
    record Result(boolean delivered, boolean subscriptionGone, String reason) {

        public static Result ok() {
            return new Result(true, false, null);
        }

        public static Result gone(String reason) {
            return new Result(false, true, reason);
        }

        public static Result failed(String reason) {
            return new Result(false, false, reason);
        }
    }
}
