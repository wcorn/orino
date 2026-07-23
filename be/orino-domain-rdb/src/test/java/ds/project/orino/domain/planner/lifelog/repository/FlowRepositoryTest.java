package ds.project.orino.domain.planner.lifelog.repository;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.lifelog.entity.Flow;
import ds.project.orino.domain.planner.lifelog.entity.FlowStatus;
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
 * Flow 매핑·상태 기본값·멤버 스코프 조회를 고정한다.
 */
@RepositoryTest
@Transactional
class FlowRepositoryTest {

    @Autowired
    private FlowRepository flowRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.save(new Member("flowuser", "pw")).getId();
    }

    @Test
    @DisplayName("새 흐름은 ACTIVE 상태로 생성된다")
    void newFlowIsActive() {
        Flow saved = flowRepository.save(new Flow(memberId, "제주 여행 2박3일", "2026 여름"));

        Flow found = flowRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(FlowStatus.ACTIVE);
        assertThat(found.getTitle()).isEqualTo("제주 여행 2박3일");
        assertThat(found.getDescription()).isEqualTo("2026 여름");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("update로 기간·커버·상태를 바꾼다")
    void updatesFields() {
        Flow flow = flowRepository.save(new Flow(memberId, "제주 여행", null));
        flow.update("제주 여행 수정", "설명", "lifelog/cover.jpg",
                Instant.parse("2026-07-20T00:00:00Z"), Instant.parse("2026-07-22T00:00:00Z"),
                FlowStatus.ARCHIVED);
        flowRepository.flush();

        Flow found = flowRepository.findById(flow.getId()).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("제주 여행 수정");
        assertThat(found.getCoverObjectKey()).isEqualTo("lifelog/cover.jpg");
        assertThat(found.getStartedAt()).isEqualTo(Instant.parse("2026-07-20T00:00:00Z"));
        assertThat(found.getStatus()).isEqualTo(FlowStatus.ARCHIVED);
    }

    @Test
    @DisplayName("findByIdAndMemberId는 다른 멤버의 흐름을 넘겨주지 않는다")
    void scopedByMember() {
        Long other = memberRepository.save(new Member("other", "pw")).getId();
        Long id = flowRepository.save(new Flow(memberId, "mine", null)).getId();

        assertThat(flowRepository.findByIdAndMemberId(id, memberId)).isPresent();
        assertThat(flowRepository.findByIdAndMemberId(id, other)).isEmpty();
    }

    @Test
    @DisplayName("상태 필터 목록은 해당 멤버의 그 상태 흐름만, 기간 역순으로")
    void listsByStatusScopedAndOrdered() {
        Flow a = flowRepository.save(new Flow(memberId, "A", null));
        a.update("A", null, null, Instant.parse("2026-05-01T00:00:00Z"), null, FlowStatus.ACTIVE);
        Flow b = flowRepository.save(new Flow(memberId, "B", null));
        b.update("B", null, null, Instant.parse("2026-07-01T00:00:00Z"), null, FlowStatus.ACTIVE);
        Flow c = flowRepository.save(new Flow(memberId, "C", null));
        c.update("C", null, null, Instant.parse("2026-06-01T00:00:00Z"), null, FlowStatus.ARCHIVED);
        flowRepository.flush();

        List<String> active = flowRepository
                .findAllByMemberIdAndStatusOrderByStartedAtDescIdDesc(memberId, FlowStatus.ACTIVE)
                .stream().map(Flow::getTitle).toList();

        assertThat(active).containsExactly("B", "A");
    }
}
