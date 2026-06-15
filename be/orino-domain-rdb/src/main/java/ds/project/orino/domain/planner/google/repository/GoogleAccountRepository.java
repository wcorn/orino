package ds.project.orino.domain.planner.google.repository;

import ds.project.orino.domain.planner.google.entity.GoogleAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoogleAccountRepository extends JpaRepository<GoogleAccount, Long> {

    Optional<GoogleAccount> findByMemberId(Long memberId);

    boolean existsByMemberId(Long memberId);

    void deleteByMemberId(Long memberId);
}
