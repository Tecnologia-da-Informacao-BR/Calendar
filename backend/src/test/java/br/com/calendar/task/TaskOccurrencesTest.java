package br.com.calendar.task;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskOccurrencesTest {

    private static Task task(String startsAt, String repeatInterval, Integer repeat) {
        Task task = new Task();
        task.setId("t1");
        task.setStartsAt(Instant.parse(startsAt));
        task.setRepeatInterval(repeatInterval);
        task.setRepeat(repeat);
        return task;
    }

    private static List<Instant> starts(Task task, String from, String to) {
        return TaskOccurrences.between(task, Instant.parse(from), Instant.parse(to)).stream()
                .map(TaskOccurrences.Occurrence::startsAt)
                .toList();
    }

    @Test
    void monthlyFromEndOfMonthClampsWithoutDrifting() {
        Task task = task("2026-01-31T10:00:00Z", "monthly", null);

        // Same result whatever range is asked, so calendar queries and
        // reminders (which use different ranges) agree on every occurrence.
        List<Instant> expected = List.of(
                Instant.parse("2026-02-28T10:00:00Z"),
                Instant.parse("2026-03-31T10:00:00Z"),
                Instant.parse("2026-04-30T10:00:00Z"));
        assertEquals(expected, starts(task, "2026-02-01T00:00:00Z", "2026-04-30T23:59:59Z"));
        assertEquals(List.of(Instant.parse("2026-03-31T10:00:00Z")),
                starts(task, "2026-03-01T00:00:00Z", "2026-03-31T23:59:59Z"));
    }

    @Test
    void yearlyFromLeapDayFallsBackToFebruary28() {
        Task task = task("2024-02-29T10:00:00Z", "yearly", null);

        assertEquals(List.of(Instant.parse("2027-02-28T10:00:00Z"), Instant.parse("2028-02-29T10:00:00Z")),
                starts(task, "2027-01-01T00:00:00Z", "2028-12-31T00:00:00Z"));
    }

    @Test
    void monthlyStrideFromEndOfMonthKeepsClampingFromTheOriginalStart() {
        Task task = task("2026-01-31T10:00:00Z", "monthly", 2);

        assertEquals(List.of(
                        Instant.parse("2026-03-31T10:00:00Z"),
                        Instant.parse("2026-05-31T10:00:00Z"),
                        Instant.parse("2026-07-31T10:00:00Z")),
                starts(task, "2026-02-01T00:00:00Z", "2026-08-31T23:59:59Z"));
    }

    @Test
    void rangeBoundariesOnClampedDatesIncludeTheOccurrence() {
        Task monthly = task("2026-01-31T10:00:00Z", "monthly", null);
        Task yearly = task("2024-02-29T10:00:00Z", "yearly", null);

        // Range starting or ending exactly on the clamped occurrence.
        assertEquals(List.of(Instant.parse("2026-02-28T10:00:00Z")),
                starts(monthly, "2026-02-28T10:00:00Z", "2026-03-30T23:59:59Z"));
        assertEquals(List.of(Instant.parse("2026-02-28T10:00:00Z")),
                starts(monthly, "2026-02-01T00:00:00Z", "2026-02-28T10:00:00Z"));
        assertEquals(List.of(Instant.parse("2028-02-29T10:00:00Z")),
                starts(yearly, "2028-02-29T10:00:00Z", "2028-02-29T10:00:00Z"));
    }

    @Test
    void isOccurrenceAcceptsClampedAndLeapDatesAndRejectsTheirNeighbours() {
        Task monthly = task("2026-01-31T10:00:00Z", "monthly", null);
        Task yearly = task("2024-02-29T10:00:00Z", "yearly", null);

        // A false negative here would cancel a valid reminder (ReminderEligibility).
        assertTrue(TaskOccurrences.isOccurrence(monthly, Instant.parse("2026-03-31T10:00:00Z")));
        assertTrue(TaskOccurrences.isOccurrence(monthly, Instant.parse("2027-02-28T10:00:00Z")));
        assertTrue(TaskOccurrences.isOccurrence(monthly, Instant.parse("2028-02-29T10:00:00Z")));
        assertFalse(TaskOccurrences.isOccurrence(monthly, Instant.parse("2026-03-28T10:00:00Z")));
        assertFalse(TaskOccurrences.isOccurrence(monthly, Instant.parse("2026-03-01T10:00:00Z")));

        assertTrue(TaskOccurrences.isOccurrence(yearly, Instant.parse("2025-02-28T10:00:00Z")));
        assertTrue(TaskOccurrences.isOccurrence(yearly, Instant.parse("2028-02-29T10:00:00Z")));
        assertTrue(TaskOccurrences.isOccurrence(yearly, Instant.parse("2100-02-28T10:00:00Z")));
        assertFalse(TaskOccurrences.isOccurrence(yearly, Instant.parse("2025-03-01T10:00:00Z")));
        assertFalse(TaskOccurrences.isOccurrence(yearly, Instant.parse("2028-02-28T10:00:00Z")));
    }

    @Test
    void longRunningSeriesIsReachedWithoutWalkingItsHistory() {
        Task task = task("1990-01-01T15:00:00Z", "daily", 3);

        List<Instant> result = starts(task, "2026-11-14T00:00:00Z", "2026-11-20T00:00:00Z");

        assertEquals(2, result.size());
        result.forEach(start -> assertTrue(TaskOccurrences.isOccurrence(task, start)));
    }

    @Test
    void isOccurrenceFollowsTheSeries() {
        Task weekly = task("2026-11-02T15:00:00Z", "weekly", 2);

        assertTrue(TaskOccurrences.isOccurrence(weekly, Instant.parse("2026-11-02T15:00:00Z")));
        assertTrue(TaskOccurrences.isOccurrence(weekly, Instant.parse("2026-11-16T15:00:00Z")));
        assertFalse(TaskOccurrences.isOccurrence(weekly, Instant.parse("2026-11-09T15:00:00Z")));
        assertFalse(TaskOccurrences.isOccurrence(weekly, Instant.parse("2026-11-16T15:30:00Z")));
        assertFalse(TaskOccurrences.isOccurrence(weekly, Instant.parse("2026-10-19T15:00:00Z")));
        assertTrue(TaskOccurrences.isOccurrence(task("2026-01-31T10:00:00Z", "monthly", null),
                Instant.parse("2026-02-28T10:00:00Z")));
    }

    @Test
    void nonRecurringOrUnrecognizedIntervalOnlyMatchesItsOwnStart() {
        Task single = task("2026-11-02T15:00:00Z", null, null);
        Task unknown = task("2026-11-02T15:00:00Z", "fortnightly", null);

        assertFalse(TaskOccurrences.isRecurring(single));
        assertFalse(TaskOccurrences.isRecurring(unknown));
        assertTrue(TaskOccurrences.isOccurrence(unknown, Instant.parse("2026-11-02T15:00:00Z")));
        assertFalse(TaskOccurrences.isOccurrence(unknown, Instant.parse("2026-11-16T15:00:00Z")));
    }

    private static List<Instant> upcomingStarts(Task task, String from, int limit) {
        return TaskOccurrences.upcoming(task, Instant.parse(from), limit).stream()
                .map(TaskOccurrences.Occurrence::startsAt)
                .toList();
    }

    @Test
    void upcomingReturnsNextOccurrencesOfOldRecurringTask() {
        // Started years ago: only the occurrences from `from` on matter.
        Task daily = task("2020-03-10T09:00:00Z", "daily", null);

        assertEquals(List.of(
                        Instant.parse("2026-11-17T09:00:00Z"),
                        Instant.parse("2026-11-18T09:00:00Z"),
                        Instant.parse("2026-11-19T09:00:00Z")),
                upcomingStarts(daily, "2026-11-17T08:00:00Z", 3));
    }

    @Test
    void upcomingIncludesOccurrenceStartingExactlyAtFromAndSkipsOngoingOne() {
        Task daily = task("2026-11-01T09:00:00Z", "daily", null);
        daily.setEndsAt(Instant.parse("2026-11-01T10:00:00Z"));

        assertEquals(List.of(Instant.parse("2026-11-17T09:00:00Z"), Instant.parse("2026-11-18T09:00:00Z")),
                upcomingStarts(daily, "2026-11-17T09:00:00Z", 2));
        // At 09:30 the 17th is already running (it started before `from`).
        assertEquals(List.of(Instant.parse("2026-11-18T09:00:00Z")),
                upcomingStarts(daily, "2026-11-17T09:30:00Z", 1));
    }

    @Test
    void upcomingRespectsStrideAndKeepsDuration() {
        Task everyTwoWeeks = task("2026-11-02T15:00:00Z", "weekly", 2);
        everyTwoWeeks.setEndsAt(Instant.parse("2026-11-02T15:30:00Z"));

        List<TaskOccurrences.Occurrence> next =
                TaskOccurrences.upcoming(everyTwoWeeks, Instant.parse("2026-11-03T00:00:00Z"), 2);

        assertEquals(List.of(Instant.parse("2026-11-16T15:00:00Z"), Instant.parse("2026-11-30T15:00:00Z")),
                next.stream().map(TaskOccurrences.Occurrence::startsAt).toList());
        assertEquals(Instant.parse("2026-11-16T15:30:00Z"), next.get(0).endsAt());
        assertEquals("t1_2026-11-16T15:00:00Z", next.get(0).id());
    }

    @Test
    void upcomingMonthlyAndYearlyAgreeWithBetween() {
        Task monthly = task("2026-01-31T10:00:00Z", "monthly", null);
        Task yearly = task("2024-02-29T10:00:00Z", "yearly", null);

        assertEquals(starts(monthly, "2026-02-01T00:00:00Z", "2026-04-30T23:59:59Z"),
                upcomingStarts(monthly, "2026-02-01T00:00:00Z", 3));
        assertEquals(List.of(Instant.parse("2027-02-28T10:00:00Z"), Instant.parse("2028-02-29T10:00:00Z")),
                upcomingStarts(yearly, "2026-03-01T00:00:00Z", 2));
    }

    @Test
    void upcomingOfNonRecurringTaskIsItsOwnStartWhenNotPast() {
        Task single = task("2026-11-20T15:00:00Z", null, null);
        Task unknown = task("2026-11-20T15:00:00Z", "fortnightly", null);

        assertEquals(List.of(Instant.parse("2026-11-20T15:00:00Z")),
                upcomingStarts(single, "2026-11-17T00:00:00Z", 5));
        assertEquals(List.of(Instant.parse("2026-11-20T15:00:00Z")),
                upcomingStarts(unknown, "2026-11-17T00:00:00Z", 5));
        assertEquals(List.of(), upcomingStarts(single, "2026-11-21T00:00:00Z", 5));
    }

    @Test
    void upcomingWithHugeLimitStaysBounded() {
        Task daily = task("2026-01-01T09:00:00Z", "daily", null);

        List<Instant> next = upcomingStarts(daily, "2026-11-17T00:00:00Z", Integer.MAX_VALUE);

        assertEquals(998, next.size());
        assertEquals(Instant.parse("2026-11-17T09:00:00Z"), next.get(0));
    }
}
