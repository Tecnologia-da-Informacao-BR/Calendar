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
                // Processed rows (READY, SKIPPED) are history and stay untouched.
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

    public void cancelPendingReminder(String taskId, Instant occurrenceStartAt) {

        if (occurrenceStartAt == null) {
            return;
        }
        Optional<Reminder> existingReminder =
                reminderRepository.findByTask_IdAndOccurrenceStartsAt(
                        taskId,
                        occurrenceStartAt
                );
        if (existingReminder.isEmpty()) {
            return;
        }

        Reminder foundReminder = existingReminder.get();

        if (foundReminder.getStatus() == ReminderStatus.PENDING) {
            foundReminder.setStatus(ReminderStatus.CANCELED);
            reminderRepository.save(foundReminder);
        }
    }

    /**
     * Cancels every pending reminder of the task. Called when the task is
     * soft-deleted, in the same transaction as the deletion.
     */
    public void cancelPendingRemindersForTask(String taskId) {
        reminderRepository.cancelPendingByTaskId(taskId, Instant.now());
    }

    public void synchronizeAfterTaskUpdate(Task task, Instant previousStartsAt) {

        if (isFinished(task)) {
            cancelPendingReminder(task.getId(), previousStartsAt);
            cancelPendingReminder(task.getId(), task.getStartsAt());
            return;
        }

        boolean sameStart = Objects.equals(previousStartsAt, task.getStartsAt());
        boolean startChanged = !sameStart;
        boolean reminderDisabled = task.getReminderMinutesBefore() == null;

        if (startChanged || reminderDisabled) {
            cancelPendingReminder(task.getId(), previousStartsAt);
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
