package br.com.calendar.reminder;

import br.com.calendar.task.Task;
import br.com.calendar.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReminderRepositoryIntegrationTest {

    private static final String USER_ID = "scheduler-owner";
    private static final String TASK_ID = "scheduler-task";

    @Autowired
    private ReminderRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update(
                "INSERT INTO users (id, name, email_confirmed) VALUES (?, ?, false)",
                USER_ID,
                "Scheduler owner"
        );

        jdbc.update(
                """
                        INSERT INTO task (id, user_id, title, starts_at)
                        VALUES (?, ?, ?, TIMESTAMPTZ '2026-11-10T18:00:00Z')
                        """,
                TASK_ID,
                USER_ID,
                "Meeting"
        );
    }

    @Test
    void savesAndFindsReminderByTaskAndOccurrence() {
        Instant occurrenceStartsAt = Instant.parse("2026-11-10T18:00:00Z");
        Instant scheduledAt = occurrenceStartsAt.minus(45, ChronoUnit.MINUTES);

        Reminder reminder = new Reminder();
        reminder.setTask(entityManager.getReference(Task.class, TASK_ID));
        reminder.setUser(entityManager.getReference(User.class, USER_ID));
        reminder.setOccurrenceStartsAt(occurrenceStartsAt);
        reminder.setScheduledAt(scheduledAt);

        Reminder saved = repository.saveAndFlush(reminder);
        String reminderId = saved.getId();

        entityManager.clear();

        Reminder found = repository
                .findByTask_IdAndOccurrenceStartsAt(TASK_ID, occurrenceStartsAt)
                .orElseThrow();

        assertNotNull(reminderId);
        assertEquals(reminderId, found.getId());
        assertEquals(TASK_ID, found.getTask().getId());
        assertEquals(USER_ID, found.getUser().getId());
        assertEquals(occurrenceStartsAt, found.getOccurrenceStartsAt());
        assertEquals(scheduledAt, found.getScheduledAt());
        assertEquals(ReminderStatus.PENDING, found.getStatus());
        assertNull(found.getClaimToken());
        assertNull(found.getLeaseExpiresAt());
        assertNotNull(found.getCreatedAt());
        assertNotNull(found.getUpdatedAt());
    }

    @Test
    void reminderForAnExistingTaskTest() {
        Instant occurrenceStartsAt = Instant.parse("2026-11-10T18:00:00Z");
        Instant scheduledAt = occurrenceStartsAt.minus(45, ChronoUnit.MINUTES);

        Reminder reminder = new Reminder();
        reminder.setTask(entityManager.getReference(Task.class, TASK_ID));
        reminder.setUser(entityManager.getReference(User.class, USER_ID));
        reminder.setOccurrenceStartsAt(occurrenceStartsAt);
        reminder.setScheduledAt(scheduledAt);

        Reminder saved = repository.saveAndFlush(reminder);


        Reminder duplicateReminder = new Reminder();
        duplicateReminder.setTask(entityManager.getReference(Task.class, TASK_ID));
        duplicateReminder.setUser(entityManager.getReference(User.class, USER_ID));
        duplicateReminder.setOccurrenceStartsAt(occurrenceStartsAt);
        duplicateReminder.setScheduledAt(scheduledAt);

        assertThrows(DataIntegrityViolationException.class, () -> {
            repository.saveAndFlush(duplicateReminder);
        });
    }
}