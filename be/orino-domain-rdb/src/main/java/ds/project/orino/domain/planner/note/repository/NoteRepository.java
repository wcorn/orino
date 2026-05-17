package ds.project.orino.domain.planner.note.repository;

import ds.project.orino.domain.planner.note.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {

    Optional<Note> findByMaterialId(Long materialId);
}
