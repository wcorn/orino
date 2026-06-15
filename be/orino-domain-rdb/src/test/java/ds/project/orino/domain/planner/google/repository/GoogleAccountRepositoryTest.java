package ds.project.orino.domain.planner.google.repository;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.google.entity.GoogleAccount;
import ds.project.orino.domain.support.RepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
@Transactional
class GoogleAccountRepositoryTest {

    @Autowired
    private GoogleAccountRepository googleAccountRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.save(new Member("googleuser", "encodedPassword")).getId();
    }

    @Test
    @DisplayName("GoogleAccount를 저장하고 memberId로 조회한다")
    void save_and_findByMemberId() {
        GoogleAccount account = new GoogleAccount(
                memberId, "refresh-token", "scope-a scope-b",
                "me@gmail.com", "primary", "tasklist-1");

        googleAccountRepository.save(account);
        Optional<GoogleAccount> found = googleAccountRepository.findByMemberId(memberId);

        assertThat(found).isPresent();
        GoogleAccount saved = found.get();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getMemberId()).isEqualTo(memberId);
        assertThat(saved.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(saved.getScopes()).isEqualTo("scope-a scope-b");
        assertThat(saved.getGoogleEmail()).isEqualTo("me@gmail.com");
        assertThat(saved.getPrimaryCalendarId()).isEqualTo("primary");
        assertThat(saved.getTaskListId()).isEqualTo("tasklist-1");
        assertThat(saved.getConnectedAt()).isNotNull();
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("memberId 존재 여부를 확인한다")
    void existsByMemberId() {
        googleAccountRepository.save(new GoogleAccount(memberId, "t", null, null, null, null));

        assertThat(googleAccountRepository.existsByMemberId(memberId)).isTrue();
        assertThat(googleAccountRepository.existsByMemberId(memberId + 999)).isFalse();
    }

    @Test
    @DisplayName("memberId로 GoogleAccount를 삭제한다")
    void deleteByMemberId() {
        googleAccountRepository.save(new GoogleAccount(memberId, "t", null, null, null, null));

        googleAccountRepository.deleteByMemberId(memberId);

        assertThat(googleAccountRepository.findByMemberId(memberId)).isEmpty();
    }

    @Test
    @DisplayName("member_id는 UNIQUE — 같은 member로 두 row를 저장하면 실패한다")
    void memberId_isUnique() {
        googleAccountRepository.saveAndFlush(new GoogleAccount(memberId, "t1", null, null, null, null));

        assertThatThrownBy(() ->
                googleAccountRepository.saveAndFlush(new GoogleAccount(memberId, "t2", null, null, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("markRevoked는 revoked를 true로 마킹한다")
    void markRevoked() {
        GoogleAccount account = googleAccountRepository.save(
                new GoogleAccount(memberId, "t", null, null, null, null));

        account.markRevoked();
        googleAccountRepository.save(account);

        assertThat(googleAccountRepository.findByMemberId(memberId))
                .get().extracting(GoogleAccount::isRevoked).isEqualTo(true);
    }

    @Test
    @DisplayName("reconnect는 refresh token·메타데이터를 갱신하고 revoked를 해제한다")
    void reconnect() {
        GoogleAccount account = new GoogleAccount(memberId, "old-token", "old-scope", null, null, null);
        account.markRevoked();
        googleAccountRepository.save(account);

        account.reconnect("new-token", "new-scope", "new@gmail.com", "primary", "tasklist-2");
        googleAccountRepository.save(account);

        GoogleAccount reloaded = googleAccountRepository.findByMemberId(memberId).orElseThrow();
        assertThat(reloaded.getRefreshToken()).isEqualTo("new-token");
        assertThat(reloaded.getScopes()).isEqualTo("new-scope");
        assertThat(reloaded.getGoogleEmail()).isEqualTo("new@gmail.com");
        assertThat(reloaded.getPrimaryCalendarId()).isEqualTo("primary");
        assertThat(reloaded.getTaskListId()).isEqualTo("tasklist-2");
        assertThat(reloaded.isRevoked()).isFalse();
    }
}
