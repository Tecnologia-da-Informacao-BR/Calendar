package br.com.calendar.reminder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReminderProcessorIntegrationTest {

    private static final String USER_ID = "processor-owner";
    private static final String TASK_ID = "processor-task";
    private static final Instant EVENT_START = Instant.parse("2026-11-10T15:00:00Z");
    private static final Instant REMINDER_DUE = Instant.parse("2026-11-10T14:45:00Z");

    @Autowired
    private ReminderProcessor processor;

    @Autowired
    private JdbcTemplate jdbc;

    private ReminderTestData data;

    @BeforeEach
    void setUp() {
        data = new ReminderTestData(jdbc);
        data.insertUser(USER_ID);
        data.insertTask(TASK_ID, USER_ID, EVENT_START);
    }

    /**
     * The table from issue #222: the event start is the cutoff, not the
     * original reminder time.
     */
    @ParameterizedTest
    @CsvSource({
            "2026-11-10T14:45:00Z, READY",
            "2026-11-10T14:50:00Z, READY",
            "2026-11-10T15:00:00Z, SKIPPED",
            "2026-11-10T16:00:00Z, SKIPPED"
    })
    void processesDueReminderUsingEventStartAsCutoff(String now, ReminderStatus expected) {
        data.insertReminder("r-due", TASK_ID, USER_ID, EVENT_START, REMINDER_DUE, ReminderStatus.PENDING);

        processor.processDueReminders(Instant.parse(now));

        assertEquals(expected.name(), data.statusOf("r-due"));
        assertNull(data.claimTokenOf("r-due"));
    }

    @Test
    void recoversReminderAfterDowntimeWhileEventHasNotStarted() {
        // Reminder due 14:30, backend offline until 14:45, event at 15:00.
        data.insertReminder("r-downtime", TASK_ID, USER_ID, EVENT_START,
                Instant.parse("2026-11-10T14:30:00Z"), ReminderStatus.PENDING);

        ReminderProcessingResult result = processor.processDueReminders(Instant.parse("2026-11-10T14:45:00Z"));

        assertEquals("READY", data.statusOf("r-downtime"));
        assertTrue(result.ready() >= 1);
    }

    @Test
    void leavesReminderPendingBeforeItsScheduledTime() {
        data.insertReminder("r-future", TASK_ID, USER_ID, EVENT_START, REMINDER_DUE, ReminderStatus.PENDING);

        processor.processDueReminders(Instant.parse("2026-11-10T14:44:59Z"));

        assertEquals("PENDING", data.statusOf("r-future"));
    }

    @Test
    void neverReprocessesTerminalOrAlreadyReadyReminders() {
        String[] taskIds = {"t-skipped", "t-canceled", "t-ready"};
        ReminderStatus[] statuses = {ReminderStatus.SKIPPED, ReminderStatus.CANCELED, ReminderStatus.READY};
        for (int i = 0; i < taskIds.length; i++) {
            data.insertTask(taskIds[i], USER_ID, EVENT_START);
            data.insertReminder("r-" + taskIds[i], taskIds[i], USER_ID, EVENT_START, REMINDER_DUE, statuses[i]);
        }

        processor.processDueReminders(Instant.parse("2026-11-10T14:50:00Z"));

        assertEquals("SKIPPED", data.statusOf("r-t-skipped"));
        assertEquals("CANCELED", data.statusOf("r-t-canceled"));
        assertEquals("READY", data.statusOf("r-t-ready"));
    }

    @Test
    void cancelsDueReminderOfDeletedOrCompletedTask() {
        Instant earlier = Instant.parse("2026-11-10T12:00:00Z");
        data.insertTask("t-deleted", USER_ID, EVENT_START, earlier, null);
        data.insertTask("t-completed", USER_ID, EVENT_START, null, earlier);
        data.insertReminder("r-deleted", "t-deleted", USER_ID, EVENT_START, REMINDER_DUE, ReminderStatus.PENDING);
        data.insertReminder("r-completed", "t-completed", USER_ID, EVENT_START, REMINDER_DUE, ReminderStatus.PENDING);

        processor.processDueReminders(Instant.parse("2026-11-10T14:50:00Z"));

        assertEquals("CANCELED", data.statusOf("r-deleted"));
        assertEquals("CANCELED", data.statusOf("r-completed"));
    }

    @Test
    void recoversAbandonedClaimAfterLeaseExpires() {
        // An instance claimed the reminder and crashed; its lease ended at 14:46.
        data.insertReminder("r-abandoned", TASK_ID, USER_ID, EVENT_START, REMINDER_DUE,
                ReminderStatus.PROCESSING, "crashed-instance-token", Instant.parse("2026-11-10T14:46:00Z"));

        ReminderProcessingResult result = processor.processDueReminders(Instant.parse("2026-11-10T14:50:00Z"));

        assertEquals("READY", data.statusOf("r-abandoned"));
        assertNull(data.claimTokenOf("r-abandoned"));
        assertTrue(result.recovered() >= 1);
    }

    @Test
    void doesNotStealClaimWhoseLeaseIsStillValid() {
        data.insertReminder("r-claimed", TASK_ID, USER_ID, EVENT_START, REMINDER_DUE,
                ReminderStatus.PROCESSING, "live-instance-token", Instant.parse("2026-11-10T14:55:00Z"));

        processor.processDueReminders(Instant.parse("2026-11-10T14:50:00Z"));

        assertEquals("PROCESSING", data.statusOf("r-claimed"));
        assertEquals("live-instance-token", data.claimTokenOf("r-claimed"));
    }
}
