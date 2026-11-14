package br.com.calendar.reminder;

import br.com.calendar.task.Task;
import br.com.calendar.task.TaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Not @Transactional on purpose: each "instance" runs in its own committed
 * transactions, like backend replicas sharing the database.
 */
@SpringBootTest
@ActiveProfiles("test")
class RecurringReminderConcurrencyIntegrationTest {

    private static final String USER_ID = "rconc-owner";
    private static final int TASKS = 30;
    private static final Instant SERIES_START = Instant.parse("2026-11-01T15:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");
    // Daily at 15:00, window P7D from NOW: Nov 14 to Nov 20.
    private static final int OCCURRENCES_PER_TASK = 7;

    @Autowired
    private RecurringReminderGenerator generator;

    @Autowired
    private ReminderScheduleService scheduleService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        cleanUp();
        ReminderTestData data = new ReminderTestData(jdbc);
        data.insertUser(USER_ID);
        for (int i = 0; i < TASKS; i++) {
            data.insertRecurringTask("rconc-task-" + i, USER_ID, SERIES_START, "daily", 1, 30);
        }
    }

    @AfterEach
    void cleanUp() {
        // Deleting the user cascades to its tasks and reminders.
        jdbc.update("DELETE FROM users WHERE id = ?", USER_ID);
    }

    @Test
    void concurrentGeneratorRunsCreateEachReminderExactlyOnce() throws Exception {
        runConcurrently(4, () -> generator.generate(NOW));

        assertExactlyOneReminderPerOccurrence();
    }

    /**
     * Without the task lock (the path a task update takes), concurrent writes
     * of the same occurrences still end in one row each: the unique key plus
     * the upsert, not a check in memory, prevents the duplicates.
     */
    @Test
    void concurrentSchedulingOfTheSameTaskDoesNotDuplicate() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        runConcurrently(4, () -> transaction.execute(status -> {
            int changed = 0;
            for (int i = 0; i < TASKS; i++) {
                Task task = taskRepository.findById("rconc-task-" + i).orElseThrow();
                changed += scheduleService.scheduleRecurring(task, NOW);
            }
            return changed;
        }));

        assertExactlyOneReminderPerOccurrence();
    }

    private void runConcurrently(int instances, Callable<Integer> run) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(instances);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < instances; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return run.call();
                }));
            }
            start.countDown();
            for (Future<Integer> future : futures) {
                future.get(60, TimeUnit.SECONDS); // rethrows any failure
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertExactlyOneReminderPerOccurrence() {
        assertEquals(TASKS * OCCURRENCES_PER_TASK, jdbc.queryForObject(
                "SELECT COUNT(*) FROM reminder WHERE user_id = ?", Integer.class, USER_ID));
        assertEquals(TASKS * OCCURRENCES_PER_TASK, jdbc.queryForObject(
                "SELECT COUNT(DISTINCT (task_id, occurrence_starts_at)) FROM reminder WHERE user_id = ? AND status = 'PENDING'",
                Integer.class, USER_ID));
    }
}
