package ds.project.orino.domain.member.repository;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Transactional
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("Member 엔티티를 저장하고 조회한다")
    void save_and_find() {
        Member member = new Member("testuser", "encodedPassword");
        Member saved = memberRepository.save(member);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getLoginId()).isEqualTo("testuser");
        assertThat(saved.getPassword()).isEqualTo("encodedPassword");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("loginId로 Member를 조회한다")
    void findByLoginId() {
        memberRepository.save(new Member("finduser", "encodedPassword"));

        Optional<Member> found = memberRepository.findByLoginId("finduser");

        assertThat(found).isPresent();
        assertThat(found.get().getLoginId()).isEqualTo("finduser");
    }

    @Test
    @DisplayName("존재하지 않는 loginId 조회 시 빈 Optional을 반환한다")
    void findByLoginId_notFound() {
        Optional<Member> found = memberRepository.findByLoginId("nonexistent");

        assertThat(found).isEmpty();
    }
}
