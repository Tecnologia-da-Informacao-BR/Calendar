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
     * Cancels every reminder of the task that is still waiting for its
     * scheduled time. Reminders that were already processed (READY, SKIPPED)
     * keep their status so their history is preserved.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE reminder
            SET status = 'CANCELED',
                version = version + 1,
                updated_at = :now
            WHERE task_id = :taskId
              AND status = 'PENDING'
            """, nativeQuery = true)
    int cancelPendingByTaskId(@Param("taskId") String taskId, @Param("now") Instant now);

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

    @Query("""
            SELECT r
            FROM Reminder r
            JOIN FETCH r.task t
            WHERE r.status = br.com.calendar.reminder.ReminderStatus.READY
              AND r.occurrenceStartsAt > :now
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
