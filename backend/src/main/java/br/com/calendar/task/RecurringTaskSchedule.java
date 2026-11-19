package br.com.calendar.task;

import java.time.Instant;

/**
 * The only columns needed to compute a recurring task's occurrences. The
 * upcoming tasks query loads this for every active recurring task instead of
 * the whole entity (title, description, location, relations...), and loads
 * the complete Task only for the occurrences that make it into the result.
 */
public record RecurringTaskSchedule(
        String id,
        Instant startsAt,
        Instant endsAt,
        String repeatInterval,
        Integer repeat
) {

    /**
     * A detached Task holding only the schedule, so {@link TaskOccurrences}
     * can be reused unchanged. It is never persisted nor mapped to a response.
     */
    Task toScheduleOnlyTask() {
        Task task = new Task();
        task.setId(id);
        task.setStartsAt(startsAt);
        task.setEndsAt(endsAt);
        task.setRepeatInterval(repeatInterval);
        task.setRepeat(repeat);
        return task;
    }
}
