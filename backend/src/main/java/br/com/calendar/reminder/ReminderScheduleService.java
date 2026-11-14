package br.com.calendar.reminder;

import br.com.calendar.task.Task;
import br.com.calendar.task.TaskOccurrences;
import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

@Service
public class ReminderScheduleService {

    private final ReminderRepository reminderRepository;
    private final ReminderScheduleCalculator reminderScheduleCalculator;
    private final Duration recurringWindow;

    public ReminderScheduleService(
            ReminderRepository reminderRepository,
            ReminderScheduleCalculator reminderScheduleCalculator,
            @Value("${calendar.reminders.recurring.window:P7D}") Duration recurringWindow) {
        if (recurringWindow.isNegative() || recurringWindow.isZero()) {
            throw new IllegalArgumentException("calendar.reminders.recurring.window must be positive");
        }
        this.reminderRepository = reminderRepository;
        this.reminderScheduleCalculator = reminderScheduleCalculator;
        this.recurringWindow = recurringWindow;
    }

    public void scheduleForNewTask(Task task) {
        if (TaskOccurrences.isRecurring(task)) {
            scheduleRecurring(task, Instant.now());
            return;
        }
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

    /**
     * Creates the reminders of a recurring task whose reminder time falls
     * within the scheduling window ({@code now + window}). Only occurrences
     * that have not started yet are generated, so a restart after downtime
     * never creates reminders for past occurrences; an occurrence whose
     * reminder time already passed but that has not started is still created
     * and is picked up right away by the processor. Called again by every
     * scheduler run, which moves the window forward; the lower bound is
     * {@code now}, so the series history is never walked again.
     *
     * @return how many reminders were created or changed
     */
    public int scheduleRecurring(Task task, Instant now) {
        Integer reminderMinutesBefore = task.getReminderMinutesBefore();
        if (!TaskOccurrences.isRecurring(task) || reminderMinutesBefore == null || isFinished(task)) {
            return 0;
        }

        // Offset added to the window so an occurrence whose reminder is due
        // within the window is generated even when the occurrence itself
        // starts after it.
        Instant horizon = now.plus(recurringWindow).plus(reminderMinutesBefore, ChronoUnit.MINUTES);
        int changed = 0;
        for (TaskOccurrences.Occurrence occurrence : TaskOccurrences.between(task, now, horizon)) {
            if (!occurrence.startsAt().isAfter(now)) {
                continue; // started already (returned only because it is still running)
            }
            changed += reminderRepository.upsertPending(
                    NanoIdUtils.randomNanoId(),
                    task.getId(),
                    task.getUser().getId(),
                    occurrence.startsAt(),
                    reminderScheduleCalculator.calculateScheduledAt(occurrence.startsAt(), reminderMinutesBefore),
                    now
            );
        }
        return changed;
    }

    public void synchronizeAfterTaskUpdate(Task task, Instant previousStartsAt) {
        // Writes the task update first, so its row lock makes a concurrent
        // recurring generation run either finish before the reads below or
        // skip this task.
        reminderRepository.flush();
        Instant now = Instant.now();
        cancelFutureRemindersOutsideSeries(task, now);

        if (TaskOccurrences.isRecurring(task)) {
            if (isFinished(task)) {
                cancelRemindersForTask(task.getId());
            } else {
                scheduleRecurring(task, now);
            }
            return;
        }

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
     * Cancels the undelivered reminders of future occurrences that are no
     * longer part of the task: the recurrence, start time or reminder was
     * changed or turned off, or the task was completed. Reminders of
     * occurrences that already started are history and are left to the
     * processor, which skips or cancels them.
     */
    private void cancelFutureRemindersOutsideSeries(Task task, Instant now) {
        boolean keepAny = !isFinished(task) && task.getReminderMinutesBefore() != null;
        for (Instant occurrence : reminderRepository.findActiveOccurrencesAfter(task.getId(), now)) {
            if (!keepAny || !TaskOccurrences.isOccurrence(task, occurrence)) {
                reminderRepository.cancelActiveByTaskIdAndOccurrence(task.getId(), occurrence, now);
            }
        }
    }

    /**
     * A completed (completedAt set) or soft-deleted task must not get reminders.
     */
    private boolean isFinished(Task task) {
        return task.getCompletedAt() != null || task.getDeletedAt() != null;
    }
}
