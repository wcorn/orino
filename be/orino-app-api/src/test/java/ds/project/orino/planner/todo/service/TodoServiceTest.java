package ds.project.orino.planner.todo.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.category.entity.Category;
import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.goal.entity.Goal;
import ds.project.orino.domain.goal.entity.PeriodType;
import ds.project.orino.domain.goal.repository.GoalRepository;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.todo.entity.Priority;
import ds.project.orino.domain.todo.entity.Todo;
import ds.project.orino.domain.todo.entity.TodoStatus;
import ds.project.orino.domain.todo.repository.TodoRepository;
import ds.project.orino.planner.scheduling.dirty.DirtyScheduleMarker;
import ds.project.orino.planner.todo.dto.CreateTodoRequest;
import ds.project.orino.planner.todo.dto.TodoResponse;
import ds.project.orino.planner.todo.dto.UpdateTodoRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    private TodoService todoService;

    @Mock
    private TodoRepository todoRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private DirtyScheduleMarker dirtyScheduleMarker;

    private Member member;

    @BeforeEach
    void setUp() {
        todoService = new TodoService(todoRepository, memberRepository,
                categoryRepository, goalRepository, dirtyScheduleMarker);
        member = new Member("admin", "encoded");
    }

    @Test
    @DisplayName("할 일 목록을 조회한다")
    @SuppressWarnings("unchecked")
    void getTodos() {
        Todo todo = new Todo(member, "할 일1", null, null, null,
                Priority.HIGH, null, null);

        given(todoRepository.findAll(any(Specification.class)))
                .willReturn(List.of(todo));

        List<TodoResponse> result = todoService.getTodos(
                1L, null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("할 일1");
        assertThat(result.get(0).priority()).isEqualTo(Priority.HIGH);
    }

    @Test
    @DisplayName("할 일을 생성한다")
    void create() {
        Todo saved = new Todo(member, "새 할 일", "설명", null, null,
                Priority.HIGH, LocalDate.of(2026, 4, 10), 60);

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(todoRepository.save(any(Todo.class))).willReturn(saved);

        TodoResponse result = todoService.create(1L,
                new CreateTodoRequest("새 할 일", "설명", null, null,
                        Priority.HIGH, LocalDate.of(2026, 4, 10), 60));

        assertThat(result.title()).isEqualTo("새 할 일");
        assertThat(result.priority()).isEqualTo(Priority.HIGH);
        assertThat(result.estimatedMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("카테고리와 목표를 지정하여 할 일을 생성한다")
    void create_withCategoryAndGoal() {
        Category category = new Category(member, "프로그래밍",
                "#FF9800", "code", 0);
        Goal goal = new Goal(member, null, "목표1", null,
                PeriodType.QUARTER,
                LocalDate.of(2026, 4, 1), null);
        Todo saved = new Todo(member, "할 일", null, category, goal,
                Priority.MEDIUM, null, null);

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(categoryRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(category));
        given(goalRepository.findByIdAndMemberId(2L, 1L))
                .willReturn(Optional.of(goal));
        given(todoRepository.save(any(Todo.class))).willReturn(saved);

        todoService.create(1L,
                new CreateTodoRequest("할 일", null, 1L, 2L,
                        Priority.MEDIUM, null, null));

        verify(categoryRepository).findByIdAndMemberId(1L, 1L);
        verify(goalRepository).findByIdAndMemberId(2L, 1L);
    }

    @Test
    @DisplayName("존재하지 않는 회원으로 할 일 생성 시 예외를 던진다")
    void create_memberNotFound() {
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.create(999L,
                new CreateTodoRequest("테스트", null, null, null,
                        null, null, null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(
                        ((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("할 일을 수정한다")
    void update() {
        Todo todo = new Todo(member, "기존 제목", null, null, null,
                Priority.LOW, null, null);

        given(todoRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(todo));

        TodoResponse result = todoService.update(1L, 1L,
                new UpdateTodoRequest("수정된 제목", "새 설명", null, null,
                        Priority.HIGH, LocalDate.of(2026, 5, 1), 90));

        assertThat(result.title()).isEqualTo("수정된 제목");
        assertThat(result.priority()).isEqualTo(Priority.HIGH);
        assertThat(result.estimatedMinutes()).isEqualTo(90);
    }

    @Test
    @DisplayName("존재하지 않는 할 일 수정 시 예외를 던진다")
    void update_notFound() {
        given(todoRepository.findByIdAndMemberId(999L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.update(1L, 999L,
                new UpdateTodoRequest("제목", null, null, null,
                        null, null, null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(
                        ((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("할 일을 삭제한다")
    void delete() {
        Todo todo = new Todo(member, "삭제대상", null, null, null,
                null, null, null);

        given(todoRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(todo));

        todoService.delete(1L, 1L);

        verify(todoRepository).delete(todo);
    }

    @Test
    @DisplayName("존재하지 않는 할 일 삭제 시 예외를 던진다")
    void delete_notFound() {
        given(todoRepository.findByIdAndMemberId(999L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.delete(1L, 999L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(
                        ((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("할 일을 완료 처리한다")
    void complete() {
        Todo todo = new Todo(member, "완료 대상", null, null, null,
                null, null, null);

        given(todoRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(todo));

        TodoResponse result = todoService.complete(1L, 1L);

        assertThat(result.status()).isEqualTo(TodoStatus.COMPLETED);
    }

    @Test
    @DisplayName("존재하지 않는 할 일 완료 시 예외를 던진다")
    void complete_notFound() {
        given(todoRepository.findByIdAndMemberId(999L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.complete(1L, 999L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(
                        ((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
