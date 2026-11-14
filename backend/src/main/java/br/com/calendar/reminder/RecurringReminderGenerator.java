package br.com.calendar.reminder;

import br.com.calendar.task.Task;
import br.com.calendar.task.TaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Moves the scheduling window of recurring tasks forward: on each run, every
 * active recurring task with a reminder gets the reminders of its occurrences
 * that entered the window since the last run. It only creates reminder rows;
 * {@link ReminderProcessor} decides later whether they are delivered.
 *
 * <p>Each page of tasks is handled in its own short transaction with the task
 * rows locked, so a task update never interleaves with the generation of the
 * same task. Duplicates are impossible because rows are written with
 * {@link ReminderRepository#upsertPending}, backed by the unique key
 * (task, occurrence).
 */
@Service
public class RecurringReminderGenerator {

    private final TaskRepository taskRepository;
    private final ReminderScheduleService reminderScheduleService;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;

    public RecurringReminderGenerator(
            TaskRepository taskRepository,
            ReminderScheduleService reminderScheduleService,
            PlatformTransactionManager transactionManager,
            @Value("${calendar.reminders.scheduler.batch-size:100}") int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("calendar.reminders.scheduler.batch-size must be positive");
        }
        this.taskRepository = taskRepository;
        this.reminderScheduleService = reminderScheduleService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
    }

    /**
     * @return how many reminders were created or changed
     */
    public int generate(Instant now) {
        int changed = 0;
        String afterId = "";
        while (true) {
            String cursor = afterId;
            Page page = transactionTemplate.execute(status -> {
                List<Task> tasks = taskRepository.lockRecurringTasksWithReminder(cursor, batchSize);
                int pageChanged = 0;
                for (Task task : tasks) {
                    pageChanged += reminderScheduleService.scheduleRecurring(task, now);
                }
                return new Page(tasks.isEmpty() ? null : tasks.getLast().getId(), pageChanged);
            });
            if (page == null || page.lastId() == null) {
                return changed;
            }
            changed += page.changed();
            afterId = page.lastId();
        }
    }

    private record Page(String lastId, int changed) {
    }
}
