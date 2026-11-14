package br.com.calendar.reminder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recurring reminder generation against the real database, with fixed
 * instants. The test profile keeps the default window (P7D).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecurringReminderIntegrationTest {

    private static final String USER_ID = "recurring-owner";
    private static final String TASK_ID = "recurring-task";
    // A Saturday, 10:00 UTC.
    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");
    private static final Instant SERIES_START = Instant.parse("2026-11-01T15:00:00Z");

    @Autowired
    private RecurringReminderGenerator generator;

    @Autowired
    private ReminderProcessor processor;

    @Autowired
    private ReminderDeliveryService deliveryService;

    @Autowired
    private JdbcTemplate jdbc;

    private ReminderTestData data;

    @BeforeEach
    void setUp() {
        data = new ReminderTestData(jdbc);
        data.insertUser(USER_ID);
    }

    private static Instant day(int dayOfNovember) {
        return SERIES_START.plus(Duration.ofDays(dayOfNovember - 1L));
    }

    private static List<Instant> days(int fromDay, int toDay) {
        return IntStream.rangeClosed(fromDay, toDay).mapToObj(RecurringReminderIntegrationTest::day).toList();
    }

    @Test
    void createsOneReminderPerOccurrenceInTheWindow() {
        data.insertRecurringTask(TASK_ID, USER_ID, SERIES_START, "daily", 1, 30);

        generator.generate(NOW);

        // Window: NOW + 7 days (+30 min of offset) -> Nov 14 to Nov 20 at 15:00.
        assertEquals(days(14, 20), data.occurrencesOf(TASK_ID));
        for (Instant occurrence : days(14, 20)) {
            assertEquals("PENDING", data.statusOf(TASK_ID, occurrence));
            assertEquals(occurrence.minus(Duration.ofMinutes(30)), data.scheduledAtOf(TASK_ID, occurrence));
        }
    }

    @Test
    void repeatedRunsAndRestartsDoNotDuplicate() {
        data.insertRecurringTask(TASK_ID, USER_ID, SERIES_START, "daily", 1, 30);
        generator.generate(NOW);
        List<String> idsBefore = reminderIds();

        // Same run again, and a run after a restart one hour later: all
        // state lives in the database, so nothing new appears.
        generator.generate(NOW);
        generator.generate(NOW.plus(Duration.ofHours(1)));

        assertEquals(idsBefore, reminderIds());
    }

    @Test
    void windowAdvancesWithTimeWithoutRewritingExistingReminders() {
        data.insertRecurringTask(TASK_ID, USER_ID, SERIES_START, "daily", 1, 30);
        generator.generate(NOW);
        List<String> idsBefore = reminderIds();

        generator.generate(NOW.plus(Duration.ofDays(2)));

        assertEquals(days(14, 22), data.occurrencesOf(TASK_ID));
        assertTrue(reminderIds().containsAll(idsBefore));
    }

    @Test
    void recoversAfterDowntimeWithoutDeliveringPastOccurrences() {
        // Reminders created before the backend went down for three days.
        data.insertRecurringTask(TASK_ID, USER_ID, SERIES_START, "daily", 1, 30);
        for (int dayOfMonth = 11; dayOfMonth <= 14; dayOfMonth++) {
            data.insertReminder("r-" + dayOfMonth, TASK_ID, USER_ID, day(dayOfMonth),
                    day(dayOfMonth).minus(Duration.ofMinutes(30)), ReminderStatus.PENDING);
        }

        // Back at 10:00 on the 14th.
        generator.generate(NOW);
        processor.processDueReminders(NOW);

        assertEquals("SKIPPED", data.statusOf("r-11"));
        assertEquals("SKIPPED", data.statusOf("r-12"));
        assertEquals("SKIPPED", data.statusOf("r-13"));
        assertEquals("PENDING", data.statusOf("r-14")); // today's 15:00, reminder at 14:30
        // Nothing generated for the occurrences missed during downtime.
        assertEquals(days(11, 20), data.occurrencesOf(TASK_ID));

        processor.processDueReminders(Instant.parse("2026-11-14T14:35:00Z"));

        assertEquals("READY", data.statusOf("r-14"));
        assertEquals("PENDING", data.statusOf(TASK_ID, day(15)));
    }

    @Test
    void lateReminderOfAnOccurrenceNotStartedYetIsStillDelivered() {
        // 10-hour offset: today's reminder time (05:00) passed while the
        // occurrence (15:00) has not started.
        data.insertRecurringTask(TASK_ID, USER_ID, SERIES_START, "daily", 1, 600);

        generator.generate(NOW);
        processor.processDueReminders(NOW);

        assertEquals("READY", data.statusOf(TASK_ID, day(14)));
        assertEquals("PENDING", data.statusOf(TASK_ID, day(15)));
        assertTrue(deliveryService.findReadyReminders(NOW, 100).stream()
                .anyMatch(ready -> ready.taskId().equals(TASK_ID) && ready.occurrenceStartsAt().equals(day(14))));
    }

    @Test
    void processorKeepsSeriesOccurrencesAndCancelsOnesOutsideTheSeries() {
        // Weekly from Sunday Nov 1: Nov 22 is in the series, Nov 16 16:00 is not.
        data.insertRecurringTask(TASK_ID, USER_ID, SERIES_START, "weekly", 1, 30);
        Instant inSeries = day(22);
        Instant notInSeries = Instant.parse("2026-11-16T16:00:00Z");
        data.insertReminder("r-in-series", TASK_ID, USER_ID, inSeries,
                inSeries.minus(Duration.ofMinutes(30)), ReminderStatus.PENDING);
        data.insertReminder("r-outside", TASK_ID, USER_ID, notInSeries,
                notInSeries.minus(Duration.ofMinutes(30)), ReminderStatus.PENDING);

        processor.processDueReminders(Instant.parse("2026-11-22T14:40:00Z"));

        assertEquals("READY", data.statusOf("r-in-series"));
        assertEquals("CANCELED", data.statusOf("r-outside"));
    }

    @Test
    void keepsReminderHistoryAndReusesCanceledRowsOfTheSeries() {
        data.insertRecurringTask(TASK_ID, USER_ID, SERIES_START, "daily", 1, 30);
        Instant oldScheduledAt = Instant.parse("2026-11-01T00:00:00Z");
        data.insertReminder("r-skipped", TASK_ID, USER_ID, day(15), oldScheduledAt, ReminderStatus.SKIPPED);
        data.insertReminder("r-ready", TASK_ID, USER_ID, day(16), oldScheduledAt, ReminderStatus.READY);
        data.insertReminder("r-canceled", TASK_ID, USER_ID, day(17), oldScheduledAt, ReminderStatus.CANCELED);
        data.insertReminder("r-pending", TASK_ID, USER_ID, day(18), oldScheduledAt, ReminderStatus.PENDING);

        generator.generate(NOW);

        assertEquals("SKIPPED", data.statusOf("r-skipped"));
        assertEquals(oldScheduledAt, data.scheduledAtOf(TASK_ID, day(15)));
        assertEquals("READY", data.statusOf("r-ready"));
        assertEquals(oldScheduledAt, data.scheduledAtOf(TASK_ID, day(16)));
        // Same row (same id), back to PENDING with the current offset.
        assertEquals("PENDING", data.statusOf("r-canceled"));
        assertEquals(day(17).minus(Duration.ofMinutes(30)), data.scheduledAtOf(TASK_ID, day(17)));
        assertEquals(day(18).minus(Duration.ofMinutes(30)), data.scheduledAtOf(TASK_ID, day(18)));
        assertEquals(days(14, 20), data.occurrencesOf(TASK_ID));
    }

    @Test
    void ignoresTasksWithoutReminderOrFinished() {
        data.insertRecurringTask("no-offset", USER_ID, SERIES_START, "daily", 1, null);
        data.insertRecurringTask("completed", USER_ID, SERIES_START, "daily", 1, 30);
        data.insertRecurringTask("deleted", USER_ID, SERIES_START, "daily", 1, 30);
        jdbc.update("UPDATE task SET completed_at = now() WHERE id = 'completed'");
        jdbc.update("UPDATE task SET deleted_at = now() WHERE id = 'deleted'");

        generator.generate(NOW);

        assertEquals(List.of(), data.occurrencesOf("no-offset"));
        assertEquals(List.of(), data.occurrencesOf("completed"));
        assertEquals(List.of(), data.occurrencesOf("deleted"));
    }

    @Test
    void remindersBelongToTheOwnerOfTheirTask() {
        String otherUser = "recurring-other";
        data.insertUser(otherUser);
        data.insertRecurringTask(TASK_ID, USER_ID, SERIES_START, "daily", 1, 30);
        data.insertRecurringTask("other-task", otherUser, SERIES_START, "weekly", 1, 30);

        generator.generate(NOW);

        assertEquals(7, countFor(USER_ID, TASK_ID));
        assertEquals(1, countFor(otherUser, "other-task")); // Sunday Nov 15
        assertEquals(0, countFor(USER_ID, "other-task"));
        assertEquals(0, countFor(otherUser, TASK_ID));
    }

    private int countFor(String userId, String taskId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM reminder WHERE user_id = ? AND task_id = ?",
                Integer.class, userId, taskId);
    }

    private List<String> reminderIds() {
        return jdbc.queryForList("SELECT id FROM reminder WHERE task_id = ? ORDER BY occurrence_starts_at",
                String.class, TASK_ID);
    }
}
