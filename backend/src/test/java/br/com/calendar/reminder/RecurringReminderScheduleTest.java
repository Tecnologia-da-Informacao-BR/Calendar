package br.com.calendar.reminder;

import br.com.calendar.task.Task;
import br.com.calendar.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Which occurrences of a recurring task get a reminder, and when. The
 * persistence side (idempotency, concurrency) is covered by
 * {@link RecurringReminderIntegrationTest}.
 */
class RecurringReminderScheduleTest {

    // A Saturday.
    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    private ReminderRepository repository;

    @BeforeEach
    void setUp() {
        repository = mock(ReminderRepository.class);
        when(repository.upsertPending(anyString(), any(), any(), any(), any(), any())).thenReturn(1);
    }

    private ReminderScheduleService service(Duration window) {
        return new ReminderScheduleService(repository, new ReminderScheduleCalculator(), window);
    }

    private static Task task(String startsAt, String repeatInterval, Integer repeat, Integer offset) {
        User user = new User();
        user.setId("user-1");
        Task task = new Task();
        task.setId("task-1");
        task.setUser(user);
        task.setStartsAt(Instant.parse(startsAt));
        task.setRepeatInterval(repeatInterval);
        task.setRepeat(repeat);
        task.setReminderMinutesBefore(offset);
        return task;
    }

    /** Occurrence starts passed to upsertPending at NOW, in call order. */
    private List<Instant> occurrences() {
        ArgumentCaptor<Instant> occurrence = ArgumentCaptor.forClass(Instant.class);
        verify(repository, atLeast(0)).upsertPending(anyString(), eq("task-1"), eq("user-1"),
                occurrence.capture(), any(), eq(NOW));
        return occurrence.getAllValues();
    }

    /** Reminder times passed to upsertPending at NOW, in call order. */
    private List<Instant> scheduledAts() {
        ArgumentCaptor<Instant> scheduledAt = ArgumentCaptor.forClass(Instant.class);
        verify(repository, atLeast(0)).upsertPending(anyString(), eq("task-1"), eq("user-1"),
                any(), scheduledAt.capture(), eq(NOW));
        return scheduledAt.getAllValues();
    }

    private static List<Instant> instants(String... values) {
        return Arrays.stream(values).map(Instant::parse).toList();
    }

    @Test
    void weeklyMondayAt15WithThirtyMinutesGetsAReminderAt1430OnEachMonday() {
        service(Duration.ofDays(14)).scheduleRecurring(task("2026-11-02T15:00:00Z", "weekly", null, 30), NOW);

        assertEquals(instants("2026-11-16T15:00:00Z", "2026-11-23T15:00:00Z"), occurrences());
        assertEquals(instants("2026-11-16T14:30:00Z", "2026-11-23T14:30:00Z"), scheduledAts());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "daily   | 1 | P3D   | 2026-11-14T15:00:00Z,2026-11-15T15:00:00Z,2026-11-16T15:00:00Z",
            "daily   | 2 | P5D   | 2026-11-14T15:00:00Z,2026-11-16T15:00:00Z,2026-11-18T15:00:00Z",
            "weekly  | 1 | P14D  | 2026-11-16T15:00:00Z,2026-11-23T15:00:00Z",
            "weekly  | 3 | P31D  | 2026-11-23T15:00:00Z,2026-12-14T15:00:00Z",
            "monthly | 1 | P70D  | 2026-12-02T15:00:00Z,2027-01-02T15:00:00Z",
            "monthly | 4 | P400D | 2027-03-02T15:00:00Z,2027-07-02T15:00:00Z,2027-11-02T15:00:00Z",
            "yearly  | 1 | P800D | 2027-11-02T15:00:00Z,2028-11-02T15:00:00Z",
            "yearly  | 2 | P800D | 2028-11-02T15:00:00Z"
    })
    void generatesEachFutureOccurrenceWithinTheWindow(String interval, int repeat, String window, String expected) {
        // Series started Monday 2026-11-02 15:00, before NOW.
        service(Duration.parse(window)).scheduleRecurring(task("2026-11-02T15:00:00Z", interval, repeat, 0), NOW);

        assertEquals(instants(expected.split(",")), occurrences());
    }

    @Test
    void offsetPullsInOccurrencesThatStartAfterTheWindowButAreRemindedWithinIt() {
        // Window ends 2026-11-15T10:00. A two-day offset means the occurrence
        // of the 17th at 09:00 is reminded on the 15th at 09:00 — in the window.
        service(Duration.ofDays(1)).scheduleRecurring(task("2026-11-01T09:00:00Z", "daily", null, 2880), NOW);

        assertEquals(instants("2026-11-15T09:00:00Z", "2026-11-16T09:00:00Z", "2026-11-17T09:00:00Z"),
                occurrences());
        // The first two reminders are already late (their occurrence has not
        // started): they are still created and the processor makes them READY.
        assertEquals(instants("2026-11-13T09:00:00Z", "2026-11-14T09:00:00Z", "2026-11-15T09:00:00Z"),
                scheduledAts());
    }

    @Test
    void windowEdgeIsInclusiveOnReminderTime() {
        // Window ends exactly at 2026-11-15T10:00 = reminder of the 10:30 occurrence.
        service(Duration.ofDays(1)).scheduleRecurring(task("2026-11-01T10:30:00Z", "daily", null, 30), NOW);

        assertEquals(instants("2026-11-14T10:30:00Z", "2026-11-15T10:30:00Z"), occurrences());
    }

    @Test
    void neverGeneratesOccurrencesThatAlreadyStarted() {
        // Running daily event: today's 09:00–11:00 occurrence is in progress at NOW.
        Task task = task("2026-11-01T09:00:00Z", "daily", null, 15);
        task.setEndsAt(Instant.parse("2026-11-01T11:00:00Z"));

        service(Duration.ofDays(1)).scheduleRecurring(task, NOW);

        assertEquals(instants("2026-11-15T09:00:00Z"), occurrences());
    }

    @Test
    void windowMovesForwardWithTime() {
        ReminderScheduleService service = service(Duration.ofDays(2));
        Task task = task("2026-11-01T15:00:00Z", "daily", null, 0);

        service.scheduleRecurring(task, NOW);
        assertEquals(instants("2026-11-14T15:00:00Z", "2026-11-15T15:00:00Z"), occurrences());

        // A later run reaches occurrences the first one did not; the repeated
        // ones are no-ops in the database (upsert on the unique key).
        Instant later = NOW.plus(Duration.ofDays(3));
        service.scheduleRecurring(task, later);
        verify(repository).upsertPending(anyString(), eq("task-1"), eq("user-1"),
                eq(Instant.parse("2026-11-18T15:00:00Z")), any(), eq(later));
        verify(repository, never()).upsertPending(anyString(), any(), any(),
                eq(Instant.parse("2026-11-15T15:00:00Z")), any(), eq(later));
    }

    @Test
    void longRunningSeriesOnlyProducesTheWindow() {
        service(Duration.ofDays(7)).scheduleRecurring(task("1995-03-01T15:00:00Z", "daily", null, 30), NOW);

        List<Instant> result = occurrences();
        assertEquals(7, result.size());
        assertEquals(Instant.parse("2026-11-14T15:00:00Z"), result.getFirst());
        assertTrue(result.stream().allMatch(NOW::isBefore));
    }

    @Test
    void noReminderOffsetMeansNoReminders() {
        service(Duration.ofDays(7)).scheduleRecurring(task("2026-11-02T15:00:00Z", "daily", null, null), NOW);

        verify(repository, never()).upsertPending(any(), any(), any(), any(), any(), any());
    }

    @Test
    void finishedTaskGetsNoReminders() {
        Task completed = task("2026-11-02T15:00:00Z", "daily", null, 30);
        completed.setCompletedAt(NOW);
        Task deleted = task("2026-11-02T15:00:00Z", "daily", null, 30);
        deleted.setDeletedAt(NOW);

        service(Duration.ofDays(7)).scheduleRecurring(completed, NOW);
        service(Duration.ofDays(7)).scheduleRecurring(deleted, NOW);

        verify(repository, never()).upsertPending(any(), any(), any(), any(), any(), any());
    }

    @Test
    void newRecurringTaskIsScheduledThroughTheRecurringPath() {
        Task task = task("2026-11-02T15:00:00Z", "daily", null, 30);

        service(Duration.ofDays(7)).scheduleForNewTask(task);

        verify(repository, atLeast(1)).upsertPending(anyString(), eq("task-1"), eq("user-1"), any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsNonPositiveWindow() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service(Duration.ZERO));
    }
}
