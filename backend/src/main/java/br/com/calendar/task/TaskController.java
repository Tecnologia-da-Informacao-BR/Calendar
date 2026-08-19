package br.com.calendar.task;

import br.com.calendar.common.exception.NotAuthenticatedException;
import br.com.calendar.task.dto.request.CreateTaskRequest;
import br.com.calendar.task.dto.response.CreateTaskResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<CreateTaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new NotAuthenticatedException("Not authenticated");
        }
        String userId = authentication.getName();
        CreateTaskResponse response = taskService.create(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
