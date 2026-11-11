package br.com.calendar.reminder;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Not @Transactional on purpose: each "instance" must run in its own committed
 * transaction, like two backend replicas sharing the database. The data is
 * removed in {@link #cleanUp()}.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReminderClaimConcurrencyIntegrationTest {

    private static final String USER_ID = "concurrency-owner";
    private static final int REMINDERS = 40;
    private static final Instant EVENT_START = Instant.parse("2026-11-10T15:00:00Z");
    private static final Instant REMINDER_DUE = Instant.parse("2026-11-10T14:45:00Z");
    private static final Instant NOW = Instant.parse("2026-11-10T14:50:00Z");

    @Autowired
    private ReminderRepository repository;

    @Autowired
    private ReminderProcessor processor;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ReminderTestData data;

    @BeforeEach
    void setUp() {
        cleanUp();
        data = new ReminderTestData(jdbc);
        data.insertUser(USER_ID);
        for (int i = 0; i < REMINDERS; i++) {
            String taskId = "conc-task-" + i;
            data.insertTask(taskId, USER_ID, EVENT_START);
            data.insertReminder("conc-rem-" + i, taskId, USER_ID, EVENT_START, REMINDER_DUE, ReminderStatus.PENDING);
        }
    }

    @AfterEach
    void cleanUp() {
        // Deleting the user cascades to its tasks and reminders.
        jdbc.update("DELETE FROM users WHERE id = ?", USER_ID);
    }

    @Test
    void concurrentInstancesNeverClaimTheSameReminder() throws Exception {
        int instances = 4;
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(instances);

        try {
            List<Future<Set<String>>> futures = new ArrayList<>();
            for (int i = 0; i < instances; i++) {
                Callable<Set<String>> instance = () -> {
                    String token = UUID.randomUUID().toString();
                    start.await();
                    transaction.executeWithoutResult(status ->
                            repository.claimDuePending(token, NOW, NOW.plusSeconds(120), REMINDERS));
                    return new HashSet<>(jdbc.queryForList(
                            "SELECT id FROM reminder WHERE claim_token = ? AND user_id = ?",
                            String.class,
                            token,
                            USER_ID
                    ));
                };
                futures.add(executor.submit(instance));
            }
            start.countDown();

            Set<String> allClaimed = new HashSet<>();
            int totalClaims = 0;
            for (Future<Set<String>> future : futures) {
                Set<String> claimedByInstance = future.get(30, TimeUnit.SECONDS);
                totalClaims += claimedByInstance.size();
                allClaimed.addAll(claimedByInstance);
            }

            // No reminder appears in two instances' claims.
            assertEquals(totalClaims, allClaimed.size());
            assertTrue(allClaimed.size() <= REMINDERS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSchedulerRunsMarkEveryReminderExactlyOnce() throws Exception {
        int instances = 3;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(instances);

        try {
            List<Future<ReminderProcessingResult>> futures = new ArrayList<>();
            for (int i = 0; i < instances; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return processor.processDueReminders(NOW);
                }));
            }
            start.countDown();

            for (Future<ReminderProcessingResult> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }

            // Any reminder left PROCESSING by a lost race is finished by a later run.
            processor.processDueReminders(NOW);

            assertEquals(REMINDERS, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM reminder WHERE user_id = ? AND status = 'READY' AND claim_token IS NULL",
                    Integer.class,
                    USER_ID
            ));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void instanceThatLostItsLeaseCannotOverwriteTheNewOwner() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        String reminderId = "conc-rem-0";
        jdbc.update(
                """
                UPDATE reminder
                SET status = 'PROCESSING', claim_token = 'new-owner', lease_expires_at = ?
                WHERE id = ?
                """,
                java.sql.Timestamp.from(NOW.plusSeconds(120)),
                reminderId
        );

        Integer updated = transaction.execute(status ->
                repository.completeClaim(reminderId, "old-owner", ReminderStatus.SKIPPED.name(), NOW));

        assertEquals(0, updated);
        assertEquals("PROCESSING", data.statusOf(reminderId));
        assertEquals("new-owner", data.claimTokenOf(reminderId));
    }
}
