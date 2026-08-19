package br.com.calendar.task.dto.request;

import br.com.calendar.task.entity.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.NonNull;

import java.time.Instant;

public record CreateTaskRequest(@NonNull @NotBlank String title,
                                String description,
                                String timezone,
                                String location,
                                String status,
                                @NonNull Instant startsAt,
                                @NonNull Instant endsAt,
                                String categoryId,
                                Integer repeat,
                                String repeatInterval,
                                boolean allDay,
                                TaskPriority priority) {
}
