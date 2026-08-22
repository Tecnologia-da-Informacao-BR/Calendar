package br.com.calendar.task.dto.response;

import br.com.calendar.task.entity.TaskPriority;

import java.time.Instant;

public record CreateTaskResponse(String id,
                                 String title,
                                 String description,
                                 String timezone,
                                 String location,
                                 String status,
                                 Instant startsAt,
                                 Instant endsAt,
                                 Integer repeat,
                                 String repeatInterval,
                                 boolean allDay,
                                 TaskPriority priority,
                                 String categoryId,
                                 String userId,
                                 Instant createdAt,
                                 Instant updatedAt) {
}
