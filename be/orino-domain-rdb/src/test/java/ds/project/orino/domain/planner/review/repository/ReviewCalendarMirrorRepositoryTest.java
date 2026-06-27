package ds.project.orino.domain.planner.review.repository;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.review.entity.ReviewCalendarMirror;
import ds.project.orino.domain.support.RepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
@Transactional
class ReviewCalendarMirrorRepositoryTest {

    private static final LocalDate DUE_DATE = LocalDate.of(2026, 6, 20);
    private static final Instant SYNCED_AT = Instant.parse("2026-06-19T01:00:00Z");

    @Autowired
    private ReviewCalendarMirrorRepository mirrorRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.save(new Member("mirroruser", "encodedPassword")).getId();
    }

    @Test
    @DisplayName("미러를 저장하고 member+dueDate로 조회한다")
    void save_and_findByMemberIdAndDueDate() {
        mirrorRepository.save(new ReviewCalendarMirror(memberId, DUE_DATE, "evt-1", 3, SYNCED_AT));

        Optional<ReviewCalendarMirror> found = mirrorRepository.findByMemberIdAndDueDate(memberId, DUE_DATE);

        assertThat(found).isPresent();
        ReviewCalendarMirror saved = found.get();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getMemberId()).isEqualTo(memberId);
        assertThat(saved.getDueDate()).isEqualTo(DUE_DATE);
        assertThat(saved.getGoogleEventId()).isEqualTo("evt-1");
        assertThat(saved.getPendingCount()).isEqualTo(3);
        assertThat(saved.getSyncedAt()).isEqualTo(SYNCED_AT);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("(member_id, due_date)는 UNIQUE — 같은 키로 두 row를 저장하면 실패한다")
    void uniqueMemberDueDate() {
        mirrorRepository.saveAndFlush(new ReviewCalendarMirror(memberId, DUE_DATE, "evt-1", 1, SYNCED_AT));

        assertThatThrownBy(() ->
                mirrorRepository.saveAndFlush(new ReviewCalendarMirror(memberId, DUE_DATE, "evt-2", 2, SYNCED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("sync는 googleEventId·pendingCount·syncedAt를 갱신한다(self-heal 포함)")
    void sync_updatesFields() {
        ReviewCalendarMirror mirror =
                mirrorRepository.save(new ReviewCalendarMirror(memberId, DUE_DATE, "evt-1", 3, SYNCED_AT));

        Instant resyncedAt = Instant.parse("2026-06-19T02:00:00Z");
        mirror.sync("evt-2", 5, resyncedAt);
        mirrorRepository.saveAndFlush(mirror);

        ReviewCalendarMirror reloaded =
                mirrorRepository.findByMemberIdAndDueDate(memberId, DUE_DATE).orElseThrow();
        assertThat(reloaded.getGoogleEventId()).isEqualTo("evt-2");
        assertThat(reloaded.getPendingCount()).isEqualTo(5);
        assertThat(reloaded.getSyncedAt()).isEqualTo(resyncedAt);
    }

    @Test
    @DisplayName("member의 모든 미러를 조회한다")
    void findAllByMemberId() {
        mirrorRepository.save(new ReviewCalendarMirror(memberId, DUE_DATE, "evt-1", 1, SYNCED_AT));
        mirrorRepository.save(new ReviewCalendarMirror(memberId, DUE_DATE.plusDays(1), "evt-2", 2, SYNCED_AT));

        assertThat(mirrorRepository.findAllByMemberId(memberId)).hasSize(2);
    }

    @Test
    @DisplayName("member+dueDate로 미러를 삭제한다(N=0 정리)")
    void deleteByMemberIdAndDueDate() {
        mirrorRepository.save(new ReviewCalendarMirror(memberId, DUE_DATE, "evt-1", 1, SYNCED_AT));

        mirrorRepository.deleteByMemberIdAndDueDate(memberId, DUE_DATE);

        assertThat(mirrorRepository.findByMemberIdAndDueDate(memberId, DUE_DATE)).isEmpty();
    }

    @Test
    @DisplayName("member의 모든 미러를 삭제한다(disable 정리)")
    void deleteByMemberId() {
        mirrorRepository.save(new ReviewCalendarMirror(memberId, DUE_DATE, "evt-1", 1, SYNCED_AT));
        mirrorRepository.save(new ReviewCalendarMirror(memberId, DUE_DATE.plusDays(1), "evt-2", 2, SYNCED_AT));

        mirrorRepository.deleteByMemberId(memberId);

        assertThat(mirrorRepository.findAllByMemberId(memberId)).isEmpty();
    }
}
