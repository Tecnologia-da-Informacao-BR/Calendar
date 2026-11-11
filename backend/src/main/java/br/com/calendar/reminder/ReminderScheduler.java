package br.com.calendar.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Polls the database for due reminders. One periodic job handles every
 * reminder, so no timer or thread is created per reminder, and all state lives
 * in the database, so pending reminders survive restarts.
 *
 * <p>Disable with {@code calendar.reminders.scheduler.enabled=false} (the test
 * profile does this so tests drive {@link ReminderProcessor} directly).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(
        name = "calendar.reminders.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderProcessor reminderProcessor;
    private final Clock clock = Clock.systemUTC();

    public ReminderScheduler(ReminderProcessor reminderProcessor) {
        this.reminderProcessor = reminderProcessor;
    }

    @Scheduled(
            initialDelayString = "${calendar.reminders.scheduler.initial-delay:PT10S}",
            fixedDelayString = "${calendar.reminders.scheduler.poll-interval:PT30S}"
    )
    public void processDueReminders() {
        try {
            ReminderProcessingResult result = reminderProcessor.processDueReminders(Instant.now(clock));
            if (!result.isEmpty()) {
                log.info("Reminder run finished: {}", result);
            }
        } catch (RuntimeException exception) {
            // Keep the job alive: the next run retries and expired leases are recovered.
            log.error("Reminder run failed", exception);
        }
    }
}
