package ds.project.orino.domain.planner.routine.repository;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.routine.entity.RoutineCheck;
import ds.project.orino.domain.support.RepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
@Transactional
class RoutineCheckRepositoryTest {

    @Autowired
    private RoutineCheckRepository routineCheckRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.save(new Member("routineuser", "encodedPassword")).getId();
    }

    @Test
    @DisplayName("(member, recurringEventId, instanceDate) 중복 저장은 UNIQUE 위반")
    void uniqueConstraint() {
        routineCheckRepository.saveAndFlush(
                new RoutineCheck(memberId, "r-1", LocalDate.of(2026, 6, 20)));

        assertThatThrownBy(() -> routineCheckRepository.saveAndFlush(
                new RoutineCheck(memberId, "r-1", LocalDate.of(2026, 6, 20))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findByMemberIdAndInstanceDateBetween은 구간 내 행만 batch 로드한다")
    void findBetween() {
        routineCheckRepository.save(new RoutineCheck(memberId, "r-1", LocalDate.of(2026, 6, 1)));
        routineCheckRepository.save(new RoutineCheck(memberId, "r-1", LocalDate.of(2026, 6, 20)));
        routineCheckRepository.save(new RoutineCheck(memberId, "r-2", LocalDate.of(2026, 7, 5)));

        List<RoutineCheck> found = routineCheckRepository.findByMemberIdAndInstanceDateBetween(
                memberId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(found).hasSize(2)
                .allSatisfy(c -> assertThat(c.getInstanceDate()).isBetween(
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));
    }

    @Test
    @DisplayName("delete 후 exists는 false")
    void deleteAndExists() {
        routineCheckRepository.saveAndFlush(
                new RoutineCheck(memberId, "r-1", LocalDate.of(2026, 6, 20)));

        routineCheckRepository.deleteByMemberIdAndRecurringEventIdAndInstanceDate(
                memberId, "r-1", LocalDate.of(2026, 6, 20));

        assertThat(routineCheckRepository.existsByMemberIdAndRecurringEventIdAndInstanceDate(
                memberId, "r-1", LocalDate.of(2026, 6, 20))).isFalse();
    }
}
