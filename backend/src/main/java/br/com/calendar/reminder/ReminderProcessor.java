package br.com.calendar.reminder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Moves due reminders out of PENDING. It only decides whether a reminder is
 * eligible for delivery; it never sends anything (delivery lives in the
 * channel sub-issues and uses {@link ReminderDeliveryService}).
 *
 * <p>A run happens in three short transactions:
 * <ol>
 *     <li>abandoned claims whose lease expired go back to PENDING;</li>
 *     <li>a batch of due reminders is claimed (PROCESSING + claim token + lease);</li>
 *     <li>each claimed reminder becomes READY, SKIPPED or CANCELED.</li>
 * </ol>
 * If the instance dies between steps 2 and 3, the lease expires and another
 * instance recovers the reminders in its next run.
 */
@Service
public class ReminderProcessor {

    private final ReminderRepository reminderRepository;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final Duration claimLease;

    public ReminderProcessor(
            ReminderRepository reminderRepository,
            PlatformTransactionManager transactionManager,
            @Value("${calendar.reminders.scheduler.batch-size:100}") int batchSize,
            @Value("${calendar.reminders.scheduler.claim-lease:PT2M}") Duration claimLease) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("calendar.reminders.scheduler.batch-size must be positive");
        }
        if (claimLease.isNegative() || claimLease.isZero()) {
            throw new IllegalArgumentException("calendar.reminders.scheduler.claim-lease must be positive");
        }
        this.reminderRepository = reminderRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
        this.claimLease = claimLease;
    }

    public ReminderProcessingResult processDueReminders(Instant now) {
        Integer recovered = transactionTemplate.execute(status -> reminderRepository.releaseExpiredClaims(now));

        String claimToken = UUID.randomUUID().toString();
        Integer claimed = transactionTemplate.execute(status ->
                reminderRepository.claimDuePending(claimToken, now, now.plus(claimLease), batchSize));

        if (claimed == null || claimed == 0) {
            return recovered == null || recovered == 0
                    ? ReminderProcessingResult.EMPTY
                    : new ReminderProcessingResult(recovered, 0, 0, 0);
        }

        ReminderProcessingResult finished = finishClaim(claimToken, now);
        return new ReminderProcessingResult(
                recovered == null ? 0 : recovered,
                finished.ready(),
                finished.skipped(),
                finished.canceled()
        );
    }

    /**
     * Step 3 of a run: decides the final status of the reminders claimed with
     * {@code claimToken}. The task is read again here, so changes made after
     * the claim (task completed or deleted, reminder disabled, start time
     * changed) are taken into account. Rows canceled meanwhile lost their claim
     * token and are left as they are.
     */
    public ReminderProcessingResult finishClaim(String claimToken, Instant now) {
        Map<ReminderStatus, Integer> totals = transactionTemplate.execute(status -> resolveClaimed(claimToken, now));
        return new ReminderProcessingResult(
                0,
                totals.getOrDefault(ReminderStatus.READY, 0),
                totals.getOrDefault(ReminderStatus.SKIPPED, 0),
                totals.getOrDefault(ReminderStatus.CANCELED, 0)
        );
    }

    private Map<ReminderStatus, Integer> resolveClaimed(String claimToken, Instant now) {
        List<Reminder> claimedReminders = reminderRepository.findClaimedWithTask(claimToken);
        Map<ReminderStatus, Integer> totals = new EnumMap<>(ReminderStatus.class);

        for (Reminder reminder : claimedReminders) {
            ReminderStatus result = ReminderEligibility.resolve(reminder, reminder.getTask(), now);
            int updated = reminderRepository.completeClaim(reminder.getId(), claimToken, result.name(), now);
            if (updated == 1) {
                totals.merge(result, 1, Integer::sum);
            }
        }
        return totals;
    }
}
