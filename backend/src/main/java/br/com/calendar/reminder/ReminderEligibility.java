package br.com.calendar.reminder;

import br.com.calendar.task.Task;

import java.time.Instant;

/**
 * Single place for the rule that decides what happens to a due reminder.
 * Used both by the scheduler and by the delivery contract, so a reminder is
 * revalidated with exactly the same rule right before it is delivered.
 */
final class ReminderEligibility {

    private ReminderEligibility() {
    }

    /**
     * @return CANCELED when the task was deleted or completed, SKIPPED when the
     * event already started (the event start is the cutoff, not the original
     * reminder time) and READY otherwise.
     */
    static ReminderStatus resolve(Reminder reminder, Task task, Instant now) {
        if (task.getDeletedAt() != null || task.getCompletedAt() != null) {
            return ReminderStatus.CANCELED;
        }
        if (!now.isBefore(reminder.getOccurrenceStartsAt())) {
            return ReminderStatus.SKIPPED;
        }
        return ReminderStatus.READY;
    }
}
