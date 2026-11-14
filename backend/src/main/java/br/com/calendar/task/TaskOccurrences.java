package br.com.calendar.task;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Single recurrence algorithm, shared by the calendar queries and the
 * reminder scheduler so both always agree on when an occurrence starts.
 *
 * <p>Recurrence model (RFC 5545-style, simplified to the two columns the task
 * table has): repeatInterval is the cadence unit (daily/weekly/monthly/yearly),
 * repeat is the stride ("every N repeatIntervals", defaulting to 1). There's
 * no stored end condition, so a recurring task repeats indefinitely.
 * Occurrences are computed in UTC.
 */
public final class TaskOccurrences {

    public record Occurrence(String id, Instant startsAt, Instant endsAt) {
    }

    // Safety net for how many occurrences a single range can legitimately
    // contain (a month of a daily task tops out at 31) — not a substitute for
    // correctly locating where iteration should start, which
    // fastForwardOnOrBefore handles regardless of how old the task is.
    private static final int MAX_OCCURRENCES_PER_TASK = 1000;

    private TaskOccurrences() {
    }

    /**
     * True when the task repeats with a recognized repeatInterval. Tasks with
     * an unrecognized value behave as a single, non-repeating occurrence.
     */
    public static boolean isRecurring(Task task) {
        return task.getStartsAt() != null
                && task.getRepeatInterval() != null
                && chronoUnit(task.getRepeatInterval()) != null;
    }

    /**
     * Occurrences of a recurring task overlapping [rangeStart, rangeEnd]:
     * starting in it, ending in it, or fully spanning it.
     */
    public static List<Occurrence> between(Task task, Instant rangeStart, Instant rangeEnd) {
        ChronoUnit unit = chronoUnit(task.getRepeatInterval());
        if (unit == null) {
            // Unrecognized repeatInterval: treat as a single, non-repeating
            // occurrence rather than guessing a cadence.
            return overlaps(task.getStartsAt(), task.getEndsAt(), rangeStart, rangeEnd)
                    ? List.of(occurrence(task, task.getStartsAt(), task.getEndsAt()))
                    : List.of();
        }

        Duration eventDuration = task.getEndsAt() != null
                ? Duration.between(task.getStartsAt(), task.getEndsAt())
                : null;
        int stride = task.getRepeat() != null && task.getRepeat() > 0 ? task.getRepeat() : 1;
        ZonedDateTime origin = task.getStartsAt().atZone(ZoneOffset.UTC);

        List<Occurrence> occurrences = new ArrayList<>();
        // Jump straight to the last occurrence at/before the start of the
        // range instead of walking one stride at a time from the task's
        // original startsAt — otherwise a long-lived daily/weekly task hits
        // MAX_OCCURRENCES_PER_TASK before ever reaching a range that's years
        // out, and silently disappears from it.
        long units = fastForwardOnOrBefore(origin, unit, stride, rangeStart);

        for (int i = 0; i < MAX_OCCURRENCES_PER_TASK; i++, units += stride) {
            // Always offset from the original start (not from the previous
            // occurrence), so an end-of-month start (Jan 31) clamps to Feb 28
            // and comes back to Mar 31 instead of drifting to the 28th.
            Instant occursAt = origin.plus(units, unit).toInstant();
            if (occursAt.isAfter(rangeEnd)) {
                break;
            }
            Instant occursUntil = eventDuration != null ? occursAt.plus(eventDuration) : null;

            if (overlaps(occursAt, occursUntil, rangeStart, rangeEnd)) {
                occurrences.add(occurrence(task, occursAt, occursUntil));
            }
        }

        return occurrences;
    }

    /**
     * Whether {@code startsAt} is the start of one of the task's occurrences
     * (for a non-recurring task: whether it is the task's own startsAt).
     */
    public static boolean isOccurrence(Task task, Instant startsAt) {
        if (task.getStartsAt() == null || startsAt == null) {
            return false;
        }
        if (!isRecurring(task)) {
            return startsAt.equals(task.getStartsAt());
        }
        return between(task, startsAt, startsAt).stream()
                .anyMatch(occurrence -> occurrence.startsAt().equals(startsAt));
    }

    private static Occurrence occurrence(Task task, Instant startsAt, Instant endsAt) {
        return new Occurrence(task.getId() + "_" + startsAt, startsAt, endsAt);
    }

    /**
     * Does an event running from `start` (to `end`, or a single instant if
     * `end` is null) overlap the [rangeStart, rangeEnd] range at all? Covers
     * starting in the range, ending in it, and fully spanning it.
     */
    private static boolean overlaps(Instant start, Instant end, Instant rangeStart, Instant rangeEnd) {
        if (start.isAfter(rangeEnd)) {
            return false;
        }
        Instant effectiveEnd = end != null ? end : start;
        return !effectiveEnd.isBefore(rangeStart);
    }

    /**
     * The largest number of units of the form `k*stride` (k >= 0) such that
     * `origin + units` is <= target, computed directly instead of by
     * iterating — `unit.between` gives the exact number of whole units
     * between two instants, so this is O(1) regardless of how far apart
     * `origin` and `target` are.
     */
    private static long fastForwardOnOrBefore(ZonedDateTime origin, ChronoUnit unit, int stride, Instant target) {
        if (!origin.toInstant().isBefore(target)) {
            return 0;
        }

        long unitsBetween = unit.between(origin, target.atZone(ZoneOffset.UTC));
        return (unitsBetween / stride) * stride;
    }

    private static ChronoUnit chronoUnit(String repeatInterval) {
        if (repeatInterval == null) {
            return null;
        }
        return switch (repeatInterval.toLowerCase()) {
            case "daily" -> ChronoUnit.DAYS;
            case "weekly" -> ChronoUnit.WEEKS;
            case "monthly" -> ChronoUnit.MONTHS;
            case "yearly" -> ChronoUnit.YEARS;
            default -> null;
        };
    }
}
