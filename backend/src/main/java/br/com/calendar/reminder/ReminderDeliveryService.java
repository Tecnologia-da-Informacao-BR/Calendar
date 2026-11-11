package br.com.calendar.reminder;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Integration point for the delivery sub-issues (in-app, email, Web Push).
 * The scheduler never calls a channel; channels pull work from here.
 *
 * <p>Contract for a channel:
 * <ol>
 *     <li>list candidates with {@link #findReadyReminders(Instant, int)};</li>
 *     <li>right before sending, call {@link #revalidate(String, Instant)}. An
 *     empty result means the reminder is no longer eligible (task deleted or
 *     completed, or the event already started) and must not be sent; the
 *     reminder has already been moved to CANCELED or SKIPPED;</li>
 *     <li>acknowledge or reject the work in the channel's own delivery record,
 *     keyed by {@link ReadyReminder#reminderId()}. Handing a reminder to a
 *     channel never marks it as delivered: it stays READY, because each
 *     channel tracks its own delivery status and retries.</li>
 * </ol>
 */
@Service
public class ReminderDeliveryService {

    private final ReminderRepository reminderRepository;

    public ReminderDeliveryService(ReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    @Transactional(readOnly = true)
    public List<ReadyReminder> findReadyReminders(Instant now, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return reminderRepository.findDeliverable(now, PageRequest.of(0, limit))
                .stream()
                .map(ReadyReminder::from)
                .toList();
    }

    @Transactional
    public Optional<ReadyReminder> revalidate(String reminderId, Instant now) {
        Optional<Reminder> found = reminderRepository.findWithTaskById(reminderId);
        if (found.isEmpty() || found.get().getStatus() != ReminderStatus.READY) {
            return Optional.empty();
        }

        Reminder reminder = found.get();
        ReminderStatus result = ReminderEligibility.resolve(reminder, reminder.getTask(), now);
        if (result != ReminderStatus.READY) {
            reminder.setStatus(result);
            reminderRepository.saveAndFlush(reminder);
            return Optional.empty();
        }
        return Optional.of(ReadyReminder.from(reminder));
    }
}
