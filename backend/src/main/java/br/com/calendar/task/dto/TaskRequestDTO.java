package br.com.calendar.task.dto;

import java.time.Instant;

import br.com.calendar.task.TaskPriority;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Getter
@Setter
public class TaskRequestDTO {

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

    private Instant completedAt;

    private TaskPriority priority;

    @Min(value = 0, message = "Reminder offset must not be negative")
    @Max(value = 525600, message = "Reminder offset must not exceed 525600 minutes")
    private Integer reminderMinutesBefore;

    @JsonIgnore
    private boolean reminderMinutesBeforePresent;

    @JsonSetter("reminderMinutesBefore")
    public void setReminderMinutesBefore(Integer reminderMinutesBefore) {
        this.reminderMinutesBefore = reminderMinutesBefore;
        this.reminderMinutesBeforePresent = true;
    }
}
