package ds.project.orino.planner.todo.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.domain.todo.entity.Priority;
import ds.project.orino.domain.todo.entity.TodoStatus;
import ds.project.orino.planner.todo.dto.CreateTodoRequest;
import ds.project.orino.planner.todo.dto.TodoResponse;
import ds.project.orino.planner.todo.dto.UpdateTodoRequest;
import ds.project.orino.planner.todo.service.TodoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/todos")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TodoResponse>>> getTodos(
            Authentication authentication,
            @RequestParam(required = false) TodoStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean hasDeadline) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                todoService.getTodos(memberId, status, priority,
                        categoryId, hasDeadline)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TodoResponse>> create(
            Authentication authentication,
            @Valid @RequestBody CreateTodoRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(todoService.create(memberId, request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TodoResponse>> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateTodoRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                todoService.update(memberId, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            Authentication authentication, @PathVariable Long id) {
        Long memberId = (Long) authentication.getPrincipal();
        todoService.delete(memberId, id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<TodoResponse>> complete(
            Authentication authentication, @PathVariable Long id) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                todoService.complete(memberId, id)));
    }
}
