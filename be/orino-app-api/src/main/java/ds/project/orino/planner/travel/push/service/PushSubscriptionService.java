package ds.project.orino.planner.travel.push.service;

import ds.project.orino.domain.planner.push.entity.PushSubscription;
import ds.project.orino.domain.planner.push.repository.PushSubscriptionRepository;
import ds.project.orino.planner.travel.push.dto.PushSubscriptionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 구독 등록·해지. 기기 하나가 "나에게 보내도 좋다"고 등록한 주소를 관리한다. */
@Service
@Transactional(readOnly = true)
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;

    public PushSubscriptionService(PushSubscriptionRepository repository) {
        this.repository = repository;
    }

    /**
     * 등록. 같은 기기가 다시 구독하면 <b>새 행이 아니라 갱신</b>이다.
     *
     * <p>브라우저는 재구독 때 키를 새로 만들어 주기도 한다. 옛 키를 그대로 두면 그 키로
     * 암호화한 알림을 기기가 못 풀어 조용히 사라진다.
     */
    @Transactional
    public void subscribe(Long memberId, PushSubscriptionRequest request) {
        String hash = PushSubscription.hash(request.endpoint());
        repository.findByEndpointHash(hash)
                .ifPresentOrElse(
                        existing -> existing.refresh(request.keys().p256dh(),
                                request.keys().auth(), request.userAgent()),
                        () -> repository.save(new PushSubscription(memberId, request.endpoint(),
                                request.keys().p256dh(), request.keys().auth(),
                                request.userAgent())));
    }

    /** 해지. 없는 구독을 지우려 해도 조용히 넘어간다 — 결과가 같다. */
    @Transactional
    public void unsubscribe(Long memberId, String endpoint) {
        repository.deleteByMemberIdAndEndpointHash(memberId, PushSubscription.hash(endpoint));
    }

    public List<PushSubscription> subscriptionsOf(Long memberId) {
        return repository.findAllByMemberId(memberId);
    }
}
