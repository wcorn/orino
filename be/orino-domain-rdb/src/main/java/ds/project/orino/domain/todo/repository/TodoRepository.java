package ds.project.orino.domain.todo.repository;

import ds.project.orino.domain.todo.entity.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TodoRepository extends JpaRepository<Todo, Long>,
        JpaSpecificationExecutor<Todo> {

    Optional<Todo> findByIdAndMemberId(Long id, Long memberId);
}
