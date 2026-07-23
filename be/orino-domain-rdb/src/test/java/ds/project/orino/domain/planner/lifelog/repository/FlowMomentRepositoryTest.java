package ds.project.orino.domain.planner.lifelog.repository;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.lifelog.entity.Flow;
import ds.project.orino.domain.planner.lifelog.entity.FlowMoment;
import ds.project.orino.domain.planner.lifelog.entity.Moment;
import ds.project.orino.domain.support.RepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 흐름↔기록 N:M 조인의 담기·순서·빼기·역조회를 고정한다. 한 기록이 여러 흐름에 담기는 것도 확인.
 */
@RepositoryTest
@Transactional
class FlowMomentRepositoryTest {

    @Autowired
    private FlowMomentRepository flowMomentRepository;
    @Autowired
    private FlowRepository flowRepository;
    @Autowired
    private MomentRepository momentRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Long flowId;
    private Long m1;
    private Long m2;

    @BeforeEach
    void setUp() {
        Long memberId = memberRepository.save(new Member("fmuser", "pw")).getId();
        flowId = flowRepository.save(new Flow(memberId, "제주 여행", null)).getId();
        m1 = momentRepository.save(moment(memberId, "2026-07-20T10:00:00Z")).getId();
        m2 = momentRepository.save(moment(memberId, "2026-07-20T14:00:00Z")).getId();
    }

    @Test
    @DisplayName("담을 때 다음 sort_order는 max+1, 빈 흐름의 첫 항목은 0")
    void appendsWithIncrementingSortOrder() {
        assertThat(flowMomentRepository.findMaxSortOrder(flowId)).isEqualTo(-1);
        flowMomentRepository.save(new FlowMoment(flowId, m1, flowMomentRepository.findMaxSortOrder(flowId) + 1));
        assertThat(flowMomentRepository.findMaxSortOrder(flowId)).isEqualTo(0);
        flowMomentRepository.save(new FlowMoment(flowId, m2, flowMomentRepository.findMaxSortOrder(flowId) + 1));

        assertThat(flowMomentRepository.findAllByFlowIdOrderBySortOrderAscIdAsc(flowId))
                .extracting(FlowMoment::getMomentId)
                .containsExactly(m1, m2);
    }

    @Test
    @DisplayName("이미 담긴 기록인지 exists로 확인하고, 빼면 소속만 사라진다")
    void existsAndRemove() {
        flowMomentRepository.save(new FlowMoment(flowId, m1, 0));

        assertThat(flowMomentRepository.existsByFlowIdAndMomentId(flowId, m1)).isTrue();
        assertThat(flowMomentRepository.existsByFlowIdAndMomentId(flowId, m2)).isFalse();

        flowMomentRepository.deleteByFlowIdAndMomentId(flowId, m1);
        flowMomentRepository.flush();

        assertThat(flowMomentRepository.existsByFlowIdAndMomentId(flowId, m1)).isFalse();
        // 기록 자체는 남는다.
        assertThat(momentRepository.findById(m1)).isPresent();
    }

    @Test
    @DisplayName("순서를 조정하면 sort_order가 반영된다")
    void reorder() {
        FlowMoment fm1 = flowMomentRepository.save(new FlowMoment(flowId, m1, 0));
        FlowMoment fm2 = flowMomentRepository.save(new FlowMoment(flowId, m2, 1));

        fm1.updateSortOrder(1);
        fm2.updateSortOrder(0);
        flowMomentRepository.flush();

        assertThat(flowMomentRepository.findAllByFlowIdOrderBySortOrderAscIdAsc(flowId))
                .extracting(FlowMoment::getMomentId)
                .containsExactly(m2, m1);
    }

    @Test
    @DisplayName("한 기록이 여러 흐름에 담기고, 역조회로 소속 흐름을 찾는다")
    void oneMomentInManyFlows() {
        Long memberId = flowRepository.findById(flowId).orElseThrow().getMemberId();
        Long flow2 = flowRepository.save(new Flow(memberId, "2026 하이라이트", null)).getId();
        flowMomentRepository.save(new FlowMoment(flowId, m1, 0));
        flowMomentRepository.save(new FlowMoment(flow2, m1, 0));

        List<Long> flows = flowMomentRepository.findAllByMomentId(m1)
                .stream().map(FlowMoment::getFlowId).toList();
        assertThat(flows).containsExactlyInAnyOrder(flowId, flow2);
    }

    private Moment moment(Long memberId, String occurredAt) {
        return new Moment(memberId, Instant.parse(occurredAt), null, null, null, null, null);
    }
}
