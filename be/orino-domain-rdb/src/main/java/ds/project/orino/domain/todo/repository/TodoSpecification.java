package ds.project.orino.domain.todo.repository;

import ds.project.orino.domain.todo.entity.Priority;
import ds.project.orino.domain.todo.entity.Todo;
import ds.project.orino.domain.todo.entity.TodoStatus;
import org.springframework.data.jpa.domain.Specification;

public final class TodoSpecification {

    private TodoSpecification() {
    }

    public static Specification<Todo> memberIdEquals(Long memberId) {
        return (root, query, cb) -> cb.equal(root.get("member").get("id"), memberId);
    }

    public static Specification<Todo> statusEquals(TodoStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Todo> priorityEquals(Priority priority) {
        return (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<Todo> categoryIdEquals(Long categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Todo> hasDeadline(boolean hasDeadline) {
        return (root, query, cb) -> hasDeadline
                ? cb.isNotNull(root.get("deadline"))
                : cb.isNull(root.get("deadline"));
    }
}
