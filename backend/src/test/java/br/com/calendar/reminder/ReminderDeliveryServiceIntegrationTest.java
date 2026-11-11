package br.com.calendar.reminder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReminderDeliveryServiceIntegrationTest {

    private static final String USER_ID = "delivery-owner";
    private static final String OTHER_USER_ID = "delivery-other";
    private static final Instant EVENT_START = Instant.parse("2026-11-10T15:00:00Z");
    private static final Instant REMINDER_DUE = Instant.parse("2026-11-10T14:45:00Z");
    private static final Instant NOW = Instant.parse("2026-11-10T14:50:00Z");

    @Autowired
    private ReminderDeliveryService deliveryService;

    @Autowired
    private JdbcTemplate jdbc;

    private ReminderTestData data;

    @BeforeEach
    void setUp() {
        data = new ReminderTestData(jdbc);
        data.insertUser(USER_ID);
        data.insertUser(OTHER_USER_ID);
    }

    @Test
    void exposesReadyReminderWithEverythingDeliveryNeeds() {
        data.insertTask("d-task", USER_ID, EVENT_START);
        data.insertReminder("d-ready", "d-task", USER_ID, EVENT_START, REMINDER_DUE, ReminderStatus.READY);

        List<ReadyReminder> ready = deliveryService.findReadyReminders(NOW, 500);

        ReadyReminder reminder = ready.stream()
                .filter(candidate -> candidate.reminderId().equals("d-ready"))
                .findFirst()
                .orElseThrow();
        assertEquals(USER_ID, reminder.userId());
        assertEquals("d-task", reminder.taskId());
        assertEquals(EVENT_START, reminder.occurrenceStartsAt());
        assertEquals(REMINDER_DUE, reminder.scheduledAt());
    }

    @Test
    void doesNotExposePendingStartedOrFinishedReminders() {
        Instant earlier = Instant.parse("2026-11-10T12:00:00Z");
        data.insertTask("d-pending", USER_ID, EVENT_START);
        data.insertTask("d-started", USER_ID, Instant.parse("2026-11-10T14:00:00Z"));
        data.insertTask("d-deleted", USER_ID, EVENT_START, earlier, null);
        data.insertReminder("r-pending", "d-pending", USER_ID, EVENT_START, REMINDER_DUE, ReminderStatus.PENDING);
        data.insertReminder("r-started", "d-started", USER_ID, Instant.parse("2026-11-10T14:00:00Z"),
                Instant.parse("2026-11-10T13:45:00Z"), ReminderStatus.READY);
        data.insertReminder("r-deleted", "d-deleted", USER_ID, EVENT_START, REMINDER_DUE, ReminderStatus.READY);

        List<String> ids = deliveryService.findReadyReminders(NOW, 500).stream()
                .map(ReadyReminder::reminderId)
                .toList();

        assertTrue(!ids.contains("r-pending") && !ids.contains("r-started") && !ids.contains("r-deleted"));
    }

    @Test
    void revalidateConfirmsEligibleReminderAndKeepsItReady() {
        data.insertTask("d-task", USER_ID, EVENT_START);
        data.insertReminder("d-ready", "d-task", USER_ID, EVENT_START, REMINDER_DUE, ReminderStatus.READY);

        Optional<ReadyReminder> result = deliveryService.revalidate("d-ready", NOW);

        assertTrue(result.isPresent());
        // Handing the reminder to a channel does not mark it as delivered.
        assertEquals("READY", data.statusOf("d-ready"));
    }

    @Test
    void revalidateSkipsReminderWhenEventAlreadyStarted() {
        data.insertTask("d-task", USER_ID, EVENT_START);
        data.insertReminder("d-ready", "d-task", USER_ID, EVENT_START, REMINDER_DUE, ReminderStatus.READY);

        Optional<ReadyReminder> result = deliveryService.revalidate("d-ready", EVENT_START);

        assertTrue(result.isEmpty());
        assertEquals("SKIPPED", data.statusOf("d-ready"));
    }

    @Test
    void revalidateCancelsReminderWhenTaskWasCompleted() {
        data.insertTask("d-task", USER_ID, EVENT_START, null, Instant.parse("2026-11-10T14:00:00Z"));
        data.insertReminder("d-ready", "d-task", USER_ID, EVENT_START, REMINDER_DUE, ReminderStatus.READY);

        Optional<ReadyReminder> result = deliveryService.revalidate("d-ready", NOW);

        assertTrue(result.isEmpty());
        assertEquals("CANCELED", data.statusOf("d-ready"));
    }

    @Test
    void revalidateIgnoresRemindersThatAreNotReady() {
        data.insertTask("d-task", USER_ID, EVENT_START);
        data.insertReminder("d-pending", "d-task", USER_ID, EVENT_START, REMINDER_DUE, ReminderStatus.PENDING);

        assertTrue(deliveryService.revalidate("d-pending", NOW).isEmpty());
        assertTrue(deliveryService.revalidate("missing-reminder", NOW).isEmpty());
        assertEquals("PENDING", data.statusOf("d-pending"));
    }

    @Test
    void readyReminderBelongsToTheTaskOwnerOnly() {
        data.insertTask("d-task", USER_ID, EVENT_START);
        data.insertTask("d-other-task", OTHER_USER_ID, EVENT_START);
        data.insertReminder("d-mine", "d-task", USER_ID, EVENT_START, REMINDER_DUE, ReminderStatus.READY);
        data.insertReminder("d-theirs", "d-other-task", OTHER_USER_ID, EVENT_START, REMINDER_DUE, ReminderStatus.READY);

        List<ReadyReminder> ready = deliveryService.findReadyReminders(NOW, 500);

        ready.stream()
                .filter(reminder -> reminder.reminderId().equals("d-mine") || reminder.reminderId().equals("d-theirs"))
                .forEach(reminder -> assertEquals(
                        reminder.reminderId().equals("d-mine") ? USER_ID : OTHER_USER_ID,
                        reminder.userId()
                ));
        assertEquals(2, ready.stream()
                .filter(reminder -> reminder.reminderId().startsWith("d-"))
                .count());
    }
}
