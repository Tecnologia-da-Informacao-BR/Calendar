package br.com.calendar.reminder;

import br.com.calendar.task.Task;
import br.com.calendar.user.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

public class ReminderScheduleServiceTest {

    @Test
    void scheduleReminderForTaskWithOffset() {
        ReminderRepository repository = mock(ReminderRepository.class);
        ReminderScheduleCalculator calculator = new ReminderScheduleCalculator();
        ReminderScheduleService service = new ReminderScheduleService(
                repository,
                calculator,
                Duration.ofDays(7)
        );
        User user = new User();
        Task task = new Task();

        task.setUser(user);
        task.setStartsAt(Instant.parse("2026-11-10T18:00:00Z"));
        task.setReminderMinutesBefore(45);
        service.scheduleForNewTask(task);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(repository).save(captor.capture());
        Reminder savedReminder = captor.getValue();

        assertSame(task, savedReminder.getTask());
        assertSame(user, savedReminder.getUser());
        assertEquals(task.getStartsAt(), savedReminder.getOccurrenceStartsAt());
        assertEquals(
                Instant.parse("2026-11-10T17:15:00Z"),
                savedReminder.getScheduledAt()
        );
        assertEquals(ReminderStatus.PENDING, savedReminder.getStatus());
    }

    @Test
    void doesNotScheduleReminderWithoutOffset() {
        ReminderRepository repository = mock(ReminderRepository.class);
        ReminderScheduleCalculator calculator = new ReminderScheduleCalculator();
        ReminderScheduleService service = new ReminderScheduleService(
                repository,
                calculator,
                Duration.ofDays(7)
        );
        User user = new User();
        Task task = new Task();

        task.setUser(user);
        task.setStartsAt(Instant.parse("2026-11-10T18:00:00Z"));
        service.scheduleForNewTask(task);
        verifyNoInteractions(repository);
    }

    @Test
    void updatesExistingPendingReminder() {
        ReminderRepository repository = mock(ReminderRepository.class);
        ReminderScheduleCalculator calculator = new ReminderScheduleCalculator();
        ReminderScheduleService service = new ReminderScheduleService(
                repository,
                calculator,
                Duration.ofDays(7)
        );
        User user = new User();
        Task task = new Task();

        task.setUser(user);
        task.setStartsAt(Instant.parse("2026-11-10T18:00:00Z"));
        task.setReminderMinutesBefore(30);
        task.setId("task1");

        Reminder existingReminder = new Reminder();
        existingReminder.setScheduledAt(Instant.parse("2026-11-10T17:15:00Z"));
        Mockito.when(repository.findByTask_IdAndOccurrenceStartsAt(
                task.getId(),
                task.getStartsAt()
        )).thenReturn(Optional.of(existingReminder));
        service.scheduleForNewTask(task);
        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(repository).save(captor.capture());
        Reminder savedReminder = captor.getValue();

        assertSame(existingReminder, savedReminder);
        assertEquals(Instant.parse("2026-11-10T17:30:00Z"), savedReminder.getScheduledAt());
    }

    @Test
    void cancelsReminderOfOccurrenceWithSingleConditionalUpdate() {
        ReminderRepository repository = mock(ReminderRepository.class);
        ReminderScheduleService service = new ReminderScheduleService(
                repository,
                new ReminderScheduleCalculator(),
                Duration.ofDays(7)
        );
        Instant startsAt = Instant.parse("2026-11-10T18:00:00Z");

        service.cancelReminder("task1", startsAt);

        verify(repository).cancelActiveByTaskIdAndOccurrence(eq("task1"), eq(startsAt), any(Instant.class));
        verify(repository, never()).save(any());
    }

    @Test
    void ignoresCancellationWithoutOccurrence() {
        ReminderRepository repository = mock(ReminderRepository.class);
        ReminderScheduleService service = new ReminderScheduleService(
                repository,
                new ReminderScheduleCalculator(),
                Duration.ofDays(7)
        );

        service.cancelReminder("task1", null);

        verifyNoInteractions(repository);
    }

    @Test
    void reschedulesReminderWhenTaskStartChanges() {
        ReminderRepository repository = mock(ReminderRepository.class);
        ReminderScheduleService service = new ReminderScheduleService(
                repository,
                new ReminderScheduleCalculator(),
                Duration.ofDays(7)
        );

        Instant previousStartsAt = Instant.parse("2026-11-10T18:00:00Z");
        Instant currentStartsAt = Instant.parse("2026-11-10T19:00:00Z");

        Task task = new Task();
        task.setId("task1");
        task.setStartsAt(currentStartsAt);
        task.setReminderMinutesBefore(45);

        Mockito.when(repository.findByTask_IdAndOccurrenceStartsAt(
                task.getId(),
                currentStartsAt
        )).thenReturn(Optional.empty());

        service.synchronizeAfterTaskUpdate(task, previousStartsAt);

        verify(repository).cancelActiveByTaskIdAndOccurrence(eq("task1"), eq(previousStartsAt), any(Instant.class));

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(repository).save(captor.capture());
        Reminder newReminder = captor.getValue();

        assertSame(task, newReminder.getTask());
        assertEquals(ReminderStatus.PENDING, newReminder.getStatus());
        assertEquals(currentStartsAt, newReminder.getOccurrenceStartsAt());
        assertEquals(
                Instant.parse("2026-11-10T18:15:00Z"),
                newReminder.getScheduledAt()
        );
    }

    @Test
    void cancelsReminderWhenReminderIsDisabled() {
        ReminderRepository repository = mock(ReminderRepository.class);
        ReminderScheduleService service = new ReminderScheduleService(
                repository,
                new ReminderScheduleCalculator(),
                Duration.ofDays(7)
        );
        Instant startsAt = Instant.parse("2026-11-10T18:00:00Z");

        Task task = new Task();
        task.setId("task1");
        task.setStartsAt(startsAt);
        task.setReminderMinutesBefore(null);

        service.synchronizeAfterTaskUpdate(task, startsAt);

        verify(repository).cancelActiveByTaskIdAndOccurrence(eq("task1"), eq(startsAt), any(Instant.class));
        verify(repository, never()).save(any());
    }

    @Test
    void reactivatesCanceledReminderWhenReminderIsEnabledAgain() {
        ReminderRepository repository = mock(ReminderRepository.class);
        ReminderScheduleService service = new ReminderScheduleService(
                repository,
                new ReminderScheduleCalculator(),
                Duration.ofDays(7)
        );

        Task task = new Task();
        task.setId("task1");
        task.setStartsAt(Instant.parse("2026-11-10T18:00:00Z"));
        task.setReminderMinutesBefore(30);

        Reminder canceledReminder = new Reminder();
        canceledReminder.setStatus(ReminderStatus.CANCELED);
        canceledReminder.setScheduledAt(Instant.parse("2026-11-10T17:15:00Z"));
        when(repository.findByTask_IdAndOccurrenceStartsAt(task.getId(), task.getStartsAt()))
                .thenReturn(Optional.of(canceledReminder));

        service.scheduleForNewTask(task);

        assertEquals(ReminderStatus.PENDING, canceledReminder.getStatus());
        assertEquals(Instant.parse("2026-11-10T17:30:00Z"), canceledReminder.getScheduledAt());
        verify(repository).save(canceledReminder);
    }

    @Test
    void keepsProcessedReminderHistoryUntouched() {
        ReminderRepository repository = mock(ReminderRepository.class);
        ReminderScheduleService service = new ReminderScheduleService(
                repository,
                new ReminderScheduleCalculator(),
                Duration.ofDays(7)
        );

        Task task = new Task();
        task.setId("task1");
        task.setStartsAt(Instant.parse("2026-11-10T18:00:00Z"));
        task.setReminderMinutesBefore(30);

        Reminder skippedReminder = new Reminder();
        skippedReminder.setStatus(ReminderStatus.SKIPPED);
        skippedReminder.setScheduledAt(Instant.parse("2026-11-10T17:15:00Z"));
        when(repository.findByTask_IdAndOccurrenceStartsAt(task.getId(), task.getStartsAt()))
                .thenReturn(Optional.of(skippedReminder));

        service.scheduleForNewTask(task);

        assertEquals(ReminderStatus.SKIPPED, skippedReminder.getStatus());
        assertEquals(Instant.parse("2026-11-10T17:15:00Z"), skippedReminder.getScheduledAt());
        verify(repository, never()).save(any());
    }

    @Test
    void doesNotScheduleReminderForCompletedTask() {
        ReminderRepository repository = mock(ReminderRepository.class);
        ReminderScheduleService service = new ReminderScheduleService(
                repository,
                new ReminderScheduleCalculator(),
                Duration.ofDays(7)
        );

        Task task = new Task();
        task.setStartsAt(Instant.parse("2026-11-10T18:00:00Z"));
        task.setReminderMinutesBefore(45);
        task.setCompletedAt(Instant.parse("2026-11-10T12:00:00Z"));

        service.scheduleForNewTask(task);

        verifyNoInteractions(repository);
    }

    @Test
    void cancelsReminderWhenTaskIsCompleted() {
        ReminderRepository repository = mock(ReminderRepository.class);
        ReminderScheduleService service = new ReminderScheduleService(
                repository,
                new ReminderScheduleCalculator(),
                Duration.ofDays(7)
        );

        Instant startsAt = Instant.parse("2026-11-10T18:00:00Z");
        Task task = new Task();
        task.setId("task1");
        task.setStartsAt(startsAt);
        task.setReminderMinutesBefore(45);
        task.setCompletedAt(Instant.parse("2026-11-10T12:00:00Z"));

        service.synchronizeAfterTaskUpdate(task, startsAt);

        verify(repository, times(2)).cancelActiveByTaskIdAndOccurrence(eq("task1"), eq(startsAt), any(Instant.class));
        verify(repository, never()).save(any());
    }

    @Test
    void cancelsUndeliveredRemindersOfDeletedTask() {
        ReminderRepository repository = mock(ReminderRepository.class);
        ReminderScheduleService service = new ReminderScheduleService(
                repository,
                new ReminderScheduleCalculator(),
                Duration.ofDays(7)
        );

        service.cancelRemindersForTask("task1");

        verify(repository).cancelActiveByTaskId(eq("task1"), any(Instant.class));
    }
}
