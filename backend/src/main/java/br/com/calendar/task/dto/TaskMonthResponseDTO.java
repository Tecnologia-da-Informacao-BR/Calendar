package br.com.calendar.task.dto;

import java.time.Instant;

import br.com.calendar.category.dto.CategoryResponseDTO;
import br.com.calendar.task.TaskPriority;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * One occurrence of a task within a requested month. For a non-recurring
 * task, id == taskId and there's exactly one of these. For a recurring
 * task, taskId identifies the underlying task and id is unique per
 * occurrence (the same task can appear multiple times in a month).
 */
@Getter
@Setter
@Builder
public class TaskMonthResponseDTO {

    private String id;

    private String taskId;

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

    private TaskPriority priority;

    private Integer reminderMinutesBefore;

    private CategoryResponseDTO category;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant completedAt;
}
