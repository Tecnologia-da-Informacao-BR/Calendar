package br.com.calendar.reminder;

import br.com.calendar.task.Task;
import br.com.calendar.user.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

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
                calculator
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
                calculator
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
                calculator
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

}
