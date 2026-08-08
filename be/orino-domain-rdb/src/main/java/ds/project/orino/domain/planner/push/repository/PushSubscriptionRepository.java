package ds.project.orino.domain.planner.push.repository;

import ds.project.orino.domain.planner.push.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    /** 같은 기기의 재구독인지 확인한다. endpoint 원본이 아니라 해시로 찾는다(길이 때문). */
    Optional<PushSubscription> findByEndpointHash(String endpointHash);

    /** 발송은 "이 멤버의 구독 전부"로 조회한다. */
    List<PushSubscription> findAllByMemberId(Long memberId);

    /** 해지. 남의 구독을 지우지 않도록 멤버까지 함께 본다. */
    void deleteByMemberIdAndEndpointHash(Long memberId, String endpointHash);
}
