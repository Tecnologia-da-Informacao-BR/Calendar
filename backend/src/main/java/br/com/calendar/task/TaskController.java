package br.com.calendar.task;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import br.com.calendar.task.dto.TaskMonthResponseDTO;
import br.com.calendar.task.dto.TaskRequestDTO;
import br.com.calendar.task.dto.TaskResponseDTO;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/tasks")
@AllArgsConstructor
public class TaskController {

    private final TaskService service;

    @PostMapping
    public ResponseEntity<TaskResponseDTO> createTask(@RequestBody TaskRequestDTO task) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTask(task));
    }

    @GetMapping(params = "date")
    public ResponseEntity<List<TaskResponseDTO>> getTasksByDay(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.getTasksForDay(date));
    }

    @GetMapping("/search")
    public ResponseEntity<List<TaskResponseDTO>> searchTasks(@RequestParam("keyword") String keyword) {
        return ResponseEntity.ok(service.searchTasks(keyword));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<TaskResponseDTO>> getUpcomingTasks(
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(service.getUpcomingTasks(limit));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable String id) {
        service.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/history")
    public ResponseEntity<Page<TaskResponseDTO>> getTaskHistory(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.getTaskHistory(pageable));
    }

    @GetMapping(params = {"month", "year"})
    public ResponseEntity<List<TaskMonthResponseDTO>> getTasksByMonth(
            @RequestParam("month") int month, @RequestParam("year") int year) {
        return ResponseEntity.ok(service.getTasksForMonth(month, year));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponseDTO> replaceTask(@PathVariable String id, @RequestBody TaskRequestDTO task) {
        return ResponseEntity.ok(service.replaceTask(task, id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TaskResponseDTO> patchTask(@PathVariable String id, @RequestBody TaskRequestDTO task) {
        return ResponseEntity.ok(service.updateTask(task, id));
    }
}
