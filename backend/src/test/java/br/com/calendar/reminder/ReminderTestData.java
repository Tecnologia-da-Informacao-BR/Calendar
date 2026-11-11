package br.com.calendar.reminder;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Inserts users, tasks and reminders straight into the database so the
 * scheduler tests can set up any state (including PROCESSING rows left behind
 * by a crashed instance).
 */
final class ReminderTestData {

    private final JdbcTemplate jdbc;

    ReminderTestData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void insertUser(String userId) {
        jdbc.update(
                "INSERT INTO users (id, name, email_confirmed) VALUES (?, ?, false)",
                userId,
                "Reminder user " + userId
        );
    }

    void insertTask(String taskId, String userId, Instant startsAt) {
        insertTask(taskId, userId, startsAt, null, null);
    }

    void insertTask(String taskId, String userId, Instant startsAt, Instant deletedAt, Instant completedAt) {
        jdbc.update(
                """
                INSERT INTO task (id, user_id, title, starts_at, reminder_minutes_before, deleted_at, completed_at)
                VALUES (?, ?, ?, ?, 15, ?, ?)
                """,
                taskId,
                userId,
                "Task " + taskId,
                timestamp(startsAt),
                timestamp(deletedAt),
                timestamp(completedAt)
        );
    }

    void insertReminder(String reminderId, String taskId, String userId,
                        Instant occurrenceStartsAt, Instant scheduledAt, ReminderStatus status) {
        insertReminder(reminderId, taskId, userId, occurrenceStartsAt, scheduledAt, status, null, null);
    }

    void insertReminder(String reminderId, String taskId, String userId,
                        Instant occurrenceStartsAt, Instant scheduledAt, ReminderStatus status,
                        String claimToken, Instant leaseExpiresAt) {
        jdbc.update(
                """
                INSERT INTO reminder
                    (id, task_id, user_id, occurrence_starts_at, scheduled_at, status,
                     claim_token, lease_expires_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                reminderId,
                taskId,
                userId,
                timestamp(occurrenceStartsAt),
                timestamp(scheduledAt),
                status.name(),
                claimToken,
                timestamp(leaseExpiresAt)
        );
    }

    String statusOf(String reminderId) {
        return jdbc.queryForObject("SELECT status FROM reminder WHERE id = ?", String.class, reminderId);
    }

    String claimTokenOf(String reminderId) {
        return jdbc.queryForObject("SELECT claim_token FROM reminder WHERE id = ?", String.class, reminderId);
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
