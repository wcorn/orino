package ds.project.orino.domain.preference.repository;

import ds.project.orino.domain.preference.entity.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPreferenceRepository
        extends JpaRepository<UserPreference, Long> {

    Optional<UserPreference> findByMemberId(Long memberId);
}
