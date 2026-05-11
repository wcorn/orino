package ds.project.orino.domain.planner.material.repository;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.material.entity.MaterialStatus;
import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.support.RepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
@Transactional
class StudyMaterialRepositoryTest {

    @Autowired
    private StudyMaterialRepository studyMaterialRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("멤버별로 자료 목록을 조회한다")
    void findAllByMemberId() {
        Member m1 = memberRepository.save(new Member("user1", "pw"));
        Member m2 = memberRepository.save(new Member("user2", "pw"));
        studyMaterialRepository.save(new StudyMaterial(m1.getId(), "내 자료", MaterialType.BOOK));
        studyMaterialRepository.save(new StudyMaterial(m2.getId(), "타인 자료", MaterialType.LECTURE));

        List<StudyMaterial> result = studyMaterialRepository.findAllByMemberIdOrderByCreatedAtDesc(m1.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("내 자료");
    }

    @Test
    @DisplayName("멤버 + 상태로 자료 목록을 필터링한다")
    void findAllByMemberIdAndStatus() {
        Member member = memberRepository.save(new Member("user1", "pw"));
        studyMaterialRepository.save(new StudyMaterial(member.getId(), "활성", MaterialType.BOOK));
        StudyMaterial completed = new StudyMaterial(member.getId(), "완료", MaterialType.LECTURE);
        completed.updateStatus(MaterialStatus.COMPLETED);
        studyMaterialRepository.save(completed);

        List<StudyMaterial> active = studyMaterialRepository
                .findAllByMemberIdAndStatusOrderByCreatedAtDesc(member.getId(), MaterialStatus.ACTIVE);

        assertThat(active).hasSize(1);
        assertThat(active.get(0).getTitle()).isEqualTo("활성");
    }

    @Test
    @DisplayName("id + memberId로 자료 한 건을 조회한다")
    void findByIdAndMemberId() {
        Member member = memberRepository.save(new Member("user1", "pw"));
        StudyMaterial saved = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "자료", MaterialType.BOOK));

        Optional<StudyMaterial> found = studyMaterialRepository
                .findByIdAndMemberId(saved.getId(), member.getId());

        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("id + 다른 memberId로 조회 시 빈 Optional을 반환한다")
    void findByIdAndMemberId_notOwned() {
        Member m1 = memberRepository.save(new Member("user1", "pw"));
        Member m2 = memberRepository.save(new Member("user2", "pw"));
        StudyMaterial m1Material = studyMaterialRepository.save(
                new StudyMaterial(m1.getId(), "자료", MaterialType.BOOK));

        Optional<StudyMaterial> found = studyMaterialRepository
                .findByIdAndMemberId(m1Material.getId(), m2.getId());

        assertThat(found).isEmpty();
    }
}
