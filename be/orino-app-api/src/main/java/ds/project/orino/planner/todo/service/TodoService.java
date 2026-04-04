package ds.project.orino.planner.todo.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.category.entity.Category;
import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.goal.entity.Goal;
import ds.project.orino.domain.goal.repository.GoalRepository;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.todo.entity.Priority;
import ds.project.orino.domain.todo.entity.Todo;
import ds.project.orino.domain.todo.entity.TodoStatus;
import ds.project.orino.domain.todo.repository.TodoRepository;
import ds.project.orino.domain.todo.repository.TodoSpecification;
import ds.project.orino.planner.todo.dto.CreateTodoRequest;
import ds.project.orino.planner.todo.dto.TodoResponse;
import ds.project.orino.planner.todo.dto.UpdateTodoRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TodoService {

    private final TodoRepository todoRepository;
    private final MemberRepository memberRepository;
    private final CategoryRepository categoryRepository;
    private final GoalRepository goalRepository;

    public TodoService(TodoRepository todoRepository,
                       MemberRepository memberRepository,
                       CategoryRepository categoryRepository,
                       GoalRepository goalRepository) {
        this.todoRepository = todoRepository;
        this.memberRepository = memberRepository;
        this.categoryRepository = categoryRepository;
        this.goalRepository = goalRepository;
    }

    public List<TodoResponse> getTodos(Long memberId, TodoStatus status,
                                       Priority priority, Long categoryId,
                                       Boolean hasDeadline) {
        Specification<Todo> spec = TodoSpecification.memberIdEquals(memberId);

        if (status != null) {
            spec = spec.and(TodoSpecification.statusEquals(status));
        }
        if (priority != null) {
            spec = spec.and(TodoSpecification.priorityEquals(priority));
        }
        if (categoryId != null) {
            spec = spec.and(TodoSpecification.categoryIdEquals(categoryId));
        }
        if (hasDeadline != null) {
            spec = spec.and(TodoSpecification.hasDeadline(hasDeadline));
        }

        return todoRepository.findAll(spec).stream()
                .map(TodoResponse::from)
                .toList();
    }

    @Transactional
    public TodoResponse create(Long memberId, CreateTodoRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        Category category = resolveCategory(request.categoryId(), memberId);
        Goal goal = resolveGoal(request.goalId(), memberId);

        Todo todo = new Todo(member, request.title(), request.description(),
                category, goal, request.priority(),
                request.deadline(), request.estimatedMinutes());

        return TodoResponse.from(todoRepository.save(todo));
    }

    @Transactional
    public TodoResponse update(Long memberId, Long todoId,
                               UpdateTodoRequest request) {
        Todo todo = todoRepository.findByIdAndMemberId(todoId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        Category category = resolveCategory(request.categoryId(), memberId);
        Goal goal = resolveGoal(request.goalId(), memberId);

        todo.update(request.title(), request.description(),
                category, goal, request.priority(),
                request.deadline(), request.estimatedMinutes());

        return TodoResponse.from(todo);
    }

    @Transactional
    public void delete(Long memberId, Long todoId) {
        Todo todo = todoRepository.findByIdAndMemberId(todoId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        todoRepository.delete(todo);
    }

    @Transactional
    public TodoResponse complete(Long memberId, Long todoId) {
        Todo todo = todoRepository.findByIdAndMemberId(todoId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        todo.complete();
        return TodoResponse.from(todo);
    }

    private Category resolveCategory(Long categoryId, Long memberId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findByIdAndMemberId(categoryId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Goal resolveGoal(Long goalId, Long memberId) {
        if (goalId == null) {
            return null;
        }
        return goalRepository.findByIdAndMemberId(goalId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
