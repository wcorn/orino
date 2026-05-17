package ds.project.orino.domain.planner.flashcard.repository;

import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {

    List<Flashcard> findAllByMaterialIdOrderByCreatedAtAscIdAsc(Long materialId);

    Optional<Flashcard> findByIdAndMemberId(Long id, Long memberId);

    List<Flashcard> findAllByIdIn(Collection<Long> ids);
}
