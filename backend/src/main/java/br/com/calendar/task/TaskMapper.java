package br.com.calendar.task;

import br.com.calendar.category.Category;
import br.com.calendar.task.dto.request.CreateTaskRequest;
import br.com.calendar.task.dto.response.CreateTaskResponse;
import br.com.calendar.task.entity.Task;
import br.com.calendar.user.User;

public class TaskMapper {
    static Task toEntity(CreateTaskRequest dto, User user, Category category) {
        Task task = new Task();
        task.setUser(user);
        task.setCategory(category);
        task.setTitle(dto.title());
        task.setDescription(dto.description());
        task.setTimezone(dto.timezone());
        task.setLocation(dto.location());
        task.setStatus(dto.status());
        task.setStartsAt(dto.startsAt());
        task.setEndsAt(dto.endsAt());
        task.setRepeat(dto.repeat());
        task.setRepeatInterval(dto.repeatInterval());
        task.setAllDay(dto.allDay());
        task.setPriority(dto.priority());
        return task;
    }

    static CreateTaskResponse toResponse(Task task) {
        return new CreateTaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getTimezone(),
                task.getLocation(),
                task.getStatus(),
                task.getStartsAt(),
                task.getEndsAt(),
                task.getRepeat(),
                task.getRepeatInterval(),
                Boolean.TRUE.equals(task.getAllDay()),
                task.getPriority(),
                task.getCategory() != null ? task.getCategory().getId() : null,
                task.getUser().getId(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
