package br.com.calendar.task;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, String> {


    Optional<Task> findByIdAndDeletedAtIsNull(String id);

    List<Task> findAllByUser_Id(String userId);

    // POSITION treats the keyword literally, including % and _.
    @Query(value = """
            SELECT t.* FROM task t
            WHERE t.user_id = :userId AND t.deleted_at IS NULL
              AND POSITION(LOWER(unaccent(:keyword)) IN LOWER(unaccent(t.title))) > 0
            """, nativeQuery = true)
    List<Task> searchActiveTasksByTitle(@Param("userId") String userId, @Param("keyword") String keyword);


    @Query("SELECT t FROM Task t" +
            " WHERE t.user.id = :userId AND t.deletedAt IS NULL AND t.startsAt BETWEEN :start AND :end" +
            " ORDER BY t.startsAt")
    List<Task> findActiveTasksForDay(@Param("userId") String userId, @Param("start") Instant start, @Param("end") Instant end);

    /**
     * Candidates for a month view: non-recurring tasks overlapping the month
     * at all — starting in it, ending in it, or fully spanning it (starts
     * before, ends after) — plus every recurring task that could still have
     * an occurrence in the month (its own startsAt is before the month ends
     * — recurrence has no stored end, so it's not filterable any more
     * precisely at the query level). Occurrence expansion for the recurring
     * ones happens in the service.
     */
    @Query("SELECT t FROM Task t" +
            " WHERE t.user.id = :userId AND t.deletedAt IS NULL" +
            " AND (" +
            "   (t.repeatInterval IS NULL AND (" +
            "     t.startsAt BETWEEN :monthStart AND :monthEnd" +
            "     OR (t.endsAt IS NOT NULL AND t.startsAt <= :monthEnd AND t.endsAt >= :monthStart)" +
            "   ))" +
            "   OR (t.repeatInterval IS NOT NULL AND t.startsAt <= :monthEnd)" +
            " )" +
            " ORDER BY t.startsAt")
    List<Task> findActiveTasksForMonth(@Param("userId") String userId, @Param("monthStart") Instant monthStart, @Param("monthEnd") Instant monthEnd);

    /**
     * History query: returns tasks that either already started
     * (startsAt < now) or have been completed (completedAt IS NOT NULL).
     * Ordered by COALESCE(completedAt, startsAt) DESC, so a completed
     * task is ranked by when it was completed, and a task that's simply
     * overdue is ranked by when it started.
     * Soft-deleted tasks are NOT filtered out on purpose: the history
     * view is expected to show them too (see #137, confirmed with team).
     * A separate countQuery is provided because Hibernate can't always
     * derive an accurate count from a query with ORDER BY + COALESCE
     */
    @Query(
    value = "SELECT t FROM Task t " +
            "WHERE t.user.id = :userId " +
            "AND (t.startsAt < :now OR t.completedAt IS NOT NULL) " +
            "ORDER BY COALESCE(t.completedAt, t.startsAt) DESC",
    countQuery = "SELECT COUNT(t) FROM Task t " +
            "WHERE t.user.id = :userId " +
            "AND (t.startsAt < :now OR t.completedAt IS NOT NULL)"
)
Page<Task> findTaskHistory(@Param("userId") String userId, @Param("now") Instant now, Pageable pageable);


/**
 * Checks if there are any tasks associated with a given category that have not been soft-deleted.
 * @param categoryId
 * @return boolean: true if there are tasks associated with the category, false otherwise
 */
boolean existsByCategory_IdAndDeletedAtIsNull(String categoryId);}
