package br.com.calendar.reminder;

import br.com.calendar.task.Task;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
public class ReminderScheduleService {

    private final ReminderRepository reminderRepository;
    private final ReminderScheduleCalculator reminderScheduleCalculator;

    public ReminderScheduleService(ReminderRepository reminderRepository, ReminderScheduleCalculator reminderScheduleCalculator) {
        this.reminderRepository = reminderRepository;
        this.reminderScheduleCalculator = reminderScheduleCalculator;
    }

    public void scheduleForNewTask(Task task) {
        Instant occurrenceStarts = task.getStartsAt();
        Integer reminderMinutesBefore = task.getReminderMinutesBefore();
        if (occurrenceStarts == null || reminderMinutesBefore == null || isFinished(task)) {
            return;
        }
        Instant scheduleAt = reminderScheduleCalculator.calculateScheduledAt(occurrenceStarts, reminderMinutesBefore);
        Optional<Reminder> existingReminder = reminderRepository.findByTask_IdAndOccurrenceStartsAt(task.getId(), occurrenceStarts);


        if (existingReminder.isPresent()) {
            Reminder foundReminder = existingReminder.get();

            if (foundReminder.getStatus() == ReminderStatus.PENDING) {
                foundReminder.setScheduledAt(scheduleAt);
                reminderRepository.save(foundReminder);
            } else if (foundReminder.getStatus() == ReminderStatus.CANCELED) {
                // The unique key (task, occurrence) allows one row per occurrence,
                // so a reminder the user turns back on reuses its canceled row.
                // READY rows are already due and SKIPPED rows are history.
                foundReminder.setStatus(ReminderStatus.PENDING);
                foundReminder.setScheduledAt(scheduleAt);
                reminderRepository.save(foundReminder);
            }
            return;
        }
        Reminder reminder = new Reminder();
        reminder.setTask(task);
        reminder.setUser(task.getUser());
        reminder.setOccurrenceStartsAt(occurrenceStarts);
        reminder.setScheduledAt(scheduleAt);
        reminderRepository.save(reminder);
    }

    /**
     * Cancels the reminder of one occurrence while it has not been delivered
     * yet (PENDING, PROCESSING or READY). A single conditional UPDATE is used
     * so a scheduler instance working on the same row at the same time can
     * never turn it back into READY: its claim token is cleared here, and the
     * scheduler only finishes claims whose token still matches.
     */
    public void cancelReminder(String taskId, Instant occurrenceStartsAt) {
        if (occurrenceStartsAt == null) {
            return;
        }
        reminderRepository.cancelActiveByTaskIdAndOccurrence(taskId, occurrenceStartsAt, Instant.now());
    }

    /**
     * Cancels every reminder of the task that has not been delivered yet.
     * Called when the task is soft-deleted, in the same transaction as the
     * deletion.
     */
    public void cancelRemindersForTask(String taskId) {
        reminderRepository.cancelActiveByTaskId(taskId, Instant.now());
    }

    public void synchronizeAfterTaskUpdate(Task task, Instant previousStartsAt) {

        if (isFinished(task)) {
            cancelReminder(task.getId(), previousStartsAt);
            cancelReminder(task.getId(), task.getStartsAt());
            return;
        }

        boolean sameStart = Objects.equals(previousStartsAt, task.getStartsAt());
        boolean startChanged = !sameStart;
        boolean reminderDisabled = task.getReminderMinutesBefore() == null;

        if (startChanged || reminderDisabled) {
            cancelReminder(task.getId(), previousStartsAt);
        }

        scheduleForNewTask(task);
    }

    /**
     * A completed (completedAt set) or soft-deleted task must not get reminders.
     */
    private boolean isFinished(Task task) {
        return task.getCompletedAt() != null || task.getDeletedAt() != null;
    }
}
