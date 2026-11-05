package br.com.calendar.task.dto;

import java.time.Instant;

import br.com.calendar.task.TaskPriority;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TaskResponseDTO {
    private String id;

    private String title;

    private String description;

    private String timezone;

    private String location;

    private String status;

    private Instant startsAt;

    private Instant endsAt;

    private Integer repeat;

    private String repeatInterval;

    private Boolean allDay;

    private String categoryId;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant completedAt;

    private Instant deletedAt;

    private TaskPriority priority;

    private Integer reminderMinutesBefore;
}
