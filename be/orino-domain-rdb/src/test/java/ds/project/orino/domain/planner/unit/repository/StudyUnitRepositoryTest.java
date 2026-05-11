package ds.project.orino.domain.planner.unit.repository;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.unit.entity.StudyUnit;
import ds.project.orino.domain.planner.unit.entity.UnitStatus;
import ds.project.orino.domain.support.RepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
@Transactional
class StudyUnitRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudyMaterialRepository studyMaterialRepository;

    @Autowired
    private StudyUnitRepository studyUnitRepository;

    @Test
    @DisplayName("자료별 단위 목록을 sort_order ASC로 조회한다")
    void findAllByMaterialId_orderedBySortOrder() {
        Member member = memberRepository.save(new Member("user1", "pw"));
        StudyMaterial material = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "자료", MaterialType.BOOK));
        studyUnitRepository.save(new StudyUnit(member.getId(), material.getId(), "두번째", 2));
        studyUnitRepository.save(new StudyUnit(member.getId(), material.getId(), "첫번째", 1));
        studyUnitRepository.save(new StudyUnit(member.getId(), material.getId(), "세번째", 3));

        List<StudyUnit> result = studyUnitRepository.findAllByMaterialIdOrderBySortOrderAsc(material.getId());

        assertThat(result).extracting(StudyUnit::getSortOrder).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("materialId 목록으로 진행률을 집계한다")
    void countByMaterialIds_aggregates() throws Exception {
        Member member = memberRepository.save(new Member("user1", "pw"));
        StudyMaterial m1 = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "자료1", MaterialType.BOOK));
        StudyMaterial m2 = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "자료2", MaterialType.LECTURE));

        StudyUnit u1 = new StudyUnit(member.getId(), m1.getId(), "u1", 1);
        StudyUnit u2 = new StudyUnit(member.getId(), m1.getId(), "u2", 2);
        StudyUnit u3 = new StudyUnit(member.getId(), m2.getId(), "u3", 1);
        markCompleted(u1);
        studyUnitRepository.save(u1);
        studyUnitRepository.save(u2);
        studyUnitRepository.save(u3);

        Map<Long, StudyUnitRepository.UnitCountProjection> map = studyUnitRepository
                .countByMaterialIds(List.of(m1.getId(), m2.getId())).stream()
                .collect(Collectors.toMap(
                        StudyUnitRepository.UnitCountProjection::getMaterialId,
                        Function.identity()));

        assertThat(map.get(m1.getId()).getTotalUnits()).isEqualTo(2L);
        assertThat(map.get(m1.getId()).getCompletedUnits()).isEqualTo(1L);
        assertThat(map.get(m2.getId()).getTotalUnits()).isEqualTo(1L);
        assertThat(map.get(m2.getId()).getCompletedUnits()).isZero();
    }

    @Test
    @DisplayName("findMaxSortOrderByMaterialId - 단위가 없으면 0을 반환한다")
    void findMaxSortOrder_empty_returnsZero() {
        Member member = memberRepository.save(new Member("user1", "pw"));
        StudyMaterial material = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "자료", MaterialType.BOOK));

        int max = studyUnitRepository.findMaxSortOrderByMaterialId(material.getId());

        assertThat(max).isZero();
    }

    @Test
    @DisplayName("findMaxSortOrderByMaterialId - 가장 큰 sort_order를 반환한다")
    void findMaxSortOrder_returnsMax() {
        Member member = memberRepository.save(new Member("user1", "pw"));
        StudyMaterial material = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "자료", MaterialType.BOOK));
        studyUnitRepository.save(new StudyUnit(member.getId(), material.getId(), "u1", 2));
        studyUnitRepository.save(new StudyUnit(member.getId(), material.getId(), "u2", 7));
        studyUnitRepository.save(new StudyUnit(member.getId(), material.getId(), "u3", 5));

        int max = studyUnitRepository.findMaxSortOrderByMaterialId(material.getId());

        assertThat(max).isEqualTo(7);
    }

    @Test
    @DisplayName("findByIdAndMemberId - 본인 단위면 Optional에 담아 반환한다")
    void findByIdAndMemberId_owned() {
        Member member = memberRepository.save(new Member("user1", "pw"));
        StudyMaterial material = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "자료", MaterialType.BOOK));
        StudyUnit unit = studyUnitRepository.save(
                new StudyUnit(member.getId(), material.getId(), "u1", 1));

        assertThat(studyUnitRepository.findByIdAndMemberId(unit.getId(), member.getId()))
                .isPresent();
    }

    @Test
    @DisplayName("findByIdAndMemberId - 다른 멤버 단위면 빈 Optional")
    void findByIdAndMemberId_notOwned_empty() {
        Member m1 = memberRepository.save(new Member("user1", "pw"));
        Member m2 = memberRepository.save(new Member("user2", "pw"));
        StudyMaterial material = studyMaterialRepository.save(
                new StudyMaterial(m1.getId(), "자료", MaterialType.BOOK));
        StudyUnit unit = studyUnitRepository.save(
                new StudyUnit(m1.getId(), material.getId(), "u1", 1));

        assertThat(studyUnitRepository.findByIdAndMemberId(unit.getId(), m2.getId()))
                .isEmpty();
    }

    private void markCompleted(StudyUnit unit) throws Exception {
        Field status = StudyUnit.class.getDeclaredField("status");
        status.setAccessible(true);
        status.set(unit, UnitStatus.COMPLETED);
    }
}
