package br.com.calendar.reminder;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReminderRepository extends JpaRepository<Reminder, String> {

    Optional<Reminder> findByTask_IdAndOccurrenceStartsAt(
            String taskId,
            Instant occurrenceStartsAt
    );

    /**
     * Cancels every reminder of the task that was not delivered yet: PENDING,
     * PROCESSING (claimed by a scheduler right now) and READY. SKIPPED and
     * CANCELED rows keep their status so their history is preserved. Clearing
     * the claim makes a scheduler that is processing the row lose its claim.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE reminder
            SET status = 'CANCELED',
                claim_token = NULL,
                lease_expires_at = NULL,
                version = version + 1,
                updated_at = :now
            WHERE task_id = :taskId
              AND status IN ('PENDING', 'PROCESSING', 'READY')
            """, nativeQuery = true)
    int cancelActiveByTaskId(@Param("taskId") String taskId, @Param("now") Instant now);

    /**
     * Same as {@link #cancelActiveByTaskId} for a single occurrence, used when
     * the start time changes or the reminder is disabled.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE reminder
            SET status = 'CANCELED',
                claim_token = NULL,
                lease_expires_at = NULL,
                version = version + 1,
                updated_at = :now
            WHERE task_id = :taskId
              AND occurrence_starts_at = :occurrenceStartsAt
              AND status IN ('PENDING', 'PROCESSING', 'READY')
            """, nativeQuery = true)
    int cancelActiveByTaskIdAndOccurrence(
            @Param("taskId") String taskId,
            @Param("occurrenceStartsAt") Instant occurrenceStartsAt,
            @Param("now") Instant now
    );

    /**
     * Creates the PENDING reminder of one occurrence, idempotently: the unique
     * key (task, occurrence) turns a second insert of the same occurrence,
     * from a repeated run or a concurrent instance, into an update. A PENDING
     * row gets the new reminder time (offset changed) and a CANCELED row is
     * reused when its occurrence is part of the series again. PROCESSING,
     * READY and SKIPPED rows are left untouched.
     *
     * @return 1 when a row was created or changed, 0 otherwise
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO reminder
                (id, task_id, user_id, occurrence_starts_at, scheduled_at, status, version, created_at, updated_at)
            VALUES (:id, :taskId, :userId, :occurrenceStartsAt, :scheduledAt, 'PENDING', 0, :now, :now)
            ON CONFLICT ON CONSTRAINT uk_reminder_task_occurrence DO UPDATE
            SET status = 'PENDING',
                scheduled_at = EXCLUDED.scheduled_at,
                version = reminder.version + 1,
                updated_at = EXCLUDED.updated_at
            WHERE reminder.status = 'CANCELED'
               OR (reminder.status = 'PENDING' AND reminder.scheduled_at <> EXCLUDED.scheduled_at)
            """, nativeQuery = true)
    int upsertPending(
            @Param("id") String id,
            @Param("taskId") String taskId,
            @Param("userId") String userId,
            @Param("occurrenceStartsAt") Instant occurrenceStartsAt,
            @Param("scheduledAt") Instant scheduledAt,
            @Param("now") Instant now
    );

    /**
     * Occurrences after {@code after} whose reminder was not delivered yet.
     * Used to cancel the ones that left the series when the task changes.
     */
    @Query("""
            SELECT r.occurrenceStartsAt
            FROM Reminder r
            WHERE r.task.id = :taskId
              AND r.occurrenceStartsAt > :after
              AND r.status IN (
                  br.com.calendar.reminder.ReminderStatus.PENDING,
                  br.com.calendar.reminder.ReminderStatus.PROCESSING,
                  br.com.calendar.reminder.ReminderStatus.READY)
            """)
    List<Instant> findActiveOccurrencesAfter(@Param("taskId") String taskId, @Param("after") Instant after);

    /**
     * Atomically claims up to {@code limit} due PENDING reminders for one
     * scheduler run. {@code FOR UPDATE SKIP LOCKED} makes concurrent instances
     * skip rows another instance is claiming, so a reminder is never claimed
     * twice. The claim token identifies the rows taken by this run and the
     * lease lets another instance recover them if this one crashes.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE reminder
            SET status = 'PROCESSING',
                claim_token = :claimToken,
                lease_expires_at = :leaseExpiresAt,
                version = version + 1,
                updated_at = :now
            WHERE id IN (
                SELECT id
                FROM reminder
                WHERE status = 'PENDING'
                  AND scheduled_at <= :now
                ORDER BY scheduled_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            """, nativeQuery = true)
    int claimDuePending(
            @Param("claimToken") String claimToken,
            @Param("now") Instant now,
            @Param("leaseExpiresAt") Instant leaseExpiresAt,
            @Param("limit") int limit
    );

    @Query("""
            SELECT r
            FROM Reminder r
            JOIN FETCH r.task
            WHERE r.claimToken = :claimToken
              AND r.status = br.com.calendar.reminder.ReminderStatus.PROCESSING
            ORDER BY r.scheduledAt
            """)
    List<Reminder> findClaimedWithTask(@Param("claimToken") String claimToken);

    /**
     * Finishes a claim. The claim token must still match: if this instance
     * lost its lease and another instance took the reminder over, nothing is
     * changed and 0 is returned.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE reminder
            SET status = :status,
                claim_token = NULL,
                lease_expires_at = NULL,
                version = version + 1,
                updated_at = :now
            WHERE id = :id
              AND claim_token = :claimToken
              AND status = 'PROCESSING'
            """, nativeQuery = true)
    int completeClaim(
            @Param("id") String id,
            @Param("claimToken") String claimToken,
            @Param("status") String status,
            @Param("now") Instant now
    );

    /**
     * Returns abandoned claims (instance crashed or stopped before finishing)
     * to PENDING so the next run evaluates them again.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE reminder
            SET status = 'PENDING',
                claim_token = NULL,
                lease_expires_at = NULL,
                version = version + 1,
                updated_at = :now
            WHERE status = 'PROCESSING'
              AND lease_expires_at <= :now
            """, nativeQuery = true)
    int releaseExpiredClaims(@Param("now") Instant now);

    /**
     * Recurring occurrences can't be matched against the series in JPQL; a
     * READY reminder of an occurrence that left the series is canceled when
     * the task is updated, and {@link ReminderDeliveryService#revalidate}
     * checks the series again right before delivery.
     */
    @Query("""
            SELECT r
            FROM Reminder r
            JOIN FETCH r.task t
            WHERE r.status = br.com.calendar.reminder.ReminderStatus.READY
              AND r.occurrenceStartsAt > :now
              AND (r.occurrenceStartsAt = t.startsAt OR t.repeatInterval IS NOT NULL)
              AND t.reminderMinutesBefore IS NOT NULL
              AND t.deletedAt IS NULL
              AND t.completedAt IS NULL
            ORDER BY r.scheduledAt
            """)
    List<Reminder> findDeliverable(@Param("now") Instant now, Pageable pageable);

    @Query("""
            SELECT r
            FROM Reminder r
            JOIN FETCH r.task
            WHERE r.id = :id
            """)
    Optional<Reminder> findWithTaskById(@Param("id") String id);
}
