package br.com.calendar.reminder;

import java.time.Instant;

/**
 * What a delivery channel receives for a reminder that is eligible for
 * delivery. {@code reminderId} is the stable idempotency key of the scheduled
 * reminder (one row per task occurrence), so channels can use it to avoid
 * sending the same reminder twice.
 */
public record ReadyReminder(
        String reminderId,
        String userId,
        String taskId,
        Instant occurrenceStartsAt,
        Instant scheduledAt
) {

    static ReadyReminder from(Reminder reminder) {
        return new ReadyReminder(
                reminder.getId(),
                reminder.getUser().getId(),
                reminder.getTask().getId(),
                reminder.getOccurrenceStartsAt(),
                reminder.getScheduledAt()
        );
    }
}
