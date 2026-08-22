package br.com.calendar.service;

import br.com.calendar.domain.Task;
import br.com.calendar.domain.TaskRepository;
import br.com.calendar.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository repository;

    public Task createTask(Task task) {
        // Get the currently authenticated user from the security context
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        task.setUser(currentUser);
        return repository.save(task);
    }

    public List<Task> getTasksForDay(LocalDate date) {
        var startOfDay = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        var endOfDay = date.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
        return repository.findActiveTasksForDay(startOfDay, endOfDay);
    }

    public void deleteTask(String id) {
        repository.findByIdAndDeletedAtIsNull(id)
            .ifPresent(task -> repository.deleteById(id));
    }

    public List<Task> getTaskHistory() {
        return repository.findAll();
    }
}