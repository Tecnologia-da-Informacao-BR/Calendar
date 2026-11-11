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
     * @return CANCELED when the task was deleted or completed, its reminder was
     * disabled, or its start time changed (the reminder belongs to an outdated
     * occurrence); SKIPPED when the event already started (the event start is
     * the cutoff, not the original reminder time); READY otherwise.
     */
    static ReminderStatus resolve(Reminder reminder, Task task, Instant now) {
        boolean taskFinished = task.getDeletedAt() != null || task.getCompletedAt() != null;
        boolean reminderDisabled = task.getReminderMinutesBefore() == null;
        boolean outdatedOccurrence = !reminder.getOccurrenceStartsAt().equals(task.getStartsAt());

        if (taskFinished || reminderDisabled || outdatedOccurrence) {
            return ReminderStatus.CANCELED;
        }
        if (!now.isBefore(reminder.getOccurrenceStartsAt())) {
            return ReminderStatus.SKIPPED;
        }
        return ReminderStatus.READY;
    }
}
