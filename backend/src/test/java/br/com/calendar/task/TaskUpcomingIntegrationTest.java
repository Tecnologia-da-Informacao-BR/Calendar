package br.com.calendar.task;

import br.com.calendar.auth.JwtUtil;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TaskUpcomingIntegrationTest {

    private static final String USER_ID = "upcoming-owner";
    private static final String OTHER_USER_ID = "upcoming-other";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TaskRepository repository;

    private String token;
    private Instant referenceTime;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jdbc.update("INSERT INTO users (id, name, email_confirmed) VALUES (?, ?, false), (?, ?, false)",
                USER_ID, "Upcoming owner", OTHER_USER_ID, "Other user");
        token = jwtUtil.generateToken(USER_ID);
        referenceTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    @Test
    void returnsFutureTaskUsingExistingResponseFormat() throws Exception {
        Instant startsAt = referenceTime.plusSeconds(3600);
        jdbc.update("INSERT INTO category (id, user_id, title) VALUES (?, ?, ?)",
                "upcoming-category", USER_ID, "Trabalho");
        insertTask("future", USER_ID, startsAt);
        jdbc.update("""
                UPDATE task SET description = ?, category_id = ?, priority = ?, all_day = false,
                    timezone = ?, status = ? WHERE id = ?
                """, "Planejamento semanal", "upcoming-category", "HIGH", "America/Sao_Paulo", "custom", "future");

        upcoming()
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("future"))
                .andExpect(jsonPath("$[0].title").value("Task future"))
                .andExpect(jsonPath("$[0].description").value("Planejamento semanal"))
                .andExpect(jsonPath("$[0].categoryId").value("upcoming-category"))
                .andExpect(jsonPath("$[0].priority").value("HIGH"))
                .andExpect(jsonPath("$[0].allDay").value(false))
                .andExpect(jsonPath("$[0].timezone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$[0].status").value("custom"))
                .andExpect(jsonPath("$[0].startsAt").value(startsAt.toString()))
                .andExpect(jsonPath("$[0].completedAt").value(nullValue()))
                .andExpect(jsonPath("$[0].deletedAt").value(nullValue()))
                .andExpect(jsonPath("$[0].user").doesNotExist());
    }

    @Test
    void excludesPastTasks() throws Exception {
        insertTask("past", USER_ID, referenceTime.minusSeconds(3600));
        insertTask("future", USER_ID, referenceTime.plusSeconds(3600));

        upcoming()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains("future")));
    }

    @Test
    void includesStartsAtEqualToNowAndLimitsResultsInRepository() {
        // Use an explicit cutoff to test >= without racing the HTTP request's clock.
        Instant now = Instant.parse("2026-10-09T12:00:00Z");
        insertTask("after", USER_ID, now.plus(1, ChronoUnit.MICROS));
        insertTask("before", USER_ID, now.minus(1, ChronoUnit.MICROS));
        insertTask("at-now", USER_ID, now);

        assertEquals(List.of("at-now", "after"),
                repository.findUpcomingTasks(USER_ID, now, PageRequest.of(0, 20)).stream()
                        .map(Task::getId).toList());
        assertEquals(List.of("at-now"),
                repository.findUpcomingTasks(USER_ID, now, PageRequest.of(0, 1)).stream()
                        .map(Task::getId).toList());
    }

    @Test
    void excludesCompletedTasks() throws Exception {
        insertTask("completed", USER_ID, referenceTime.plusSeconds(3600));
        insertTask("pending", USER_ID, referenceTime.plusSeconds(7200));
        jdbc.update("UPDATE task SET completed_at = CURRENT_TIMESTAMP WHERE id = ?", "completed");

        upcoming()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains("pending")));
    }

    @Test
    void excludesSoftDeletedTasks() throws Exception {
        insertTask("deleted", USER_ID, referenceTime.plusSeconds(3600));
        insertTask("active", USER_ID, referenceTime.plusSeconds(7200));
        jdbc.update("UPDATE task SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", "deleted");

        upcoming()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains("active")));
    }

    @Test
    void onlyReturnsTasksOwnedByAuthenticatedUser() throws Exception {
        insertTask("owned", USER_ID, referenceTime.plusSeconds(7200));
        insertTask("other", OTHER_USER_ID, referenceTime.plusSeconds(3600));

        upcoming()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains("owned")));

        token = jwtUtil.generateToken(OTHER_USER_ID);
        upcoming()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains("other")));
    }

    @Test
    void ordersByStartsAtThenIdAndLimitsEligibleResults() throws Exception {
        insertTask("last", USER_ID, referenceTime.plusSeconds(10800));
        insertTask("same-b", USER_ID, referenceTime.plusSeconds(7200));
        insertTask("first", USER_ID, referenceTime.plusSeconds(3600));
        insertTask("same-a", USER_ID, referenceTime.plusSeconds(7200));
        insertTask("past", USER_ID, referenceTime.minusSeconds(3600));
        insertTask("other", OTHER_USER_ID, referenceTime.plusSeconds(600));
        insertTask("completed", USER_ID, referenceTime.plusSeconds(1200));
        insertTask("deleted", USER_ID, referenceTime.plusSeconds(1800));
        jdbc.update("UPDATE task SET completed_at = CURRENT_TIMESTAMP WHERE id = ?", "completed");
        jdbc.update("UPDATE task SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", "deleted");

        mockMvc.perform(get("/tasks/upcoming").header("Authorization", "Bearer " + token).param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains("first", "same-a", "same-b")));
    }

    @Test
    void defaultsToTwentyResultsWhenLimitIsOmitted() throws Exception {
        for (int i = 20; i >= 0; i--) {
            insertTask("task-" + i, USER_ID, referenceTime.plusSeconds(3600 + i));
        }

        upcoming()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20))
                .andExpect(jsonPath("$[0].id").value("task-0"))
                .andExpect(jsonPath("$[19].id").value("task-19"));
    }

    @Test
    void acceptsMaximumLimit() throws Exception {
        insertTask("future", USER_ID, referenceTime.plusSeconds(3600));

        mockMvc.perform(get("/tasks/upcoming").header("Authorization", "Bearer " + token)
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains("future")));
    }

    @ParameterizedTest
    @ValueSource(ints = {101, Integer.MAX_VALUE})
    void rejectsLimitAboveMaximum(int limit) throws Exception {
        mockMvc.perform(get("/tasks/upcoming").header("Authorization", "Bearer " + token)
                        .param("limit", Integer.toString(limit)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Limit must not exceed 100."));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsNonPositiveLimit(int limit) throws Exception {
        mockMvc.perform(get("/tasks/upcoming").header("Authorization", "Bearer " + token)
                        .param("limit", Integer.toString(limit)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Limit must be greater than zero."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "2147483648"})
    void rejectsInvalidIntegerLimit(String limit) throws Exception {
        mockMvc.perform(get("/tasks/upcoming").header("Authorization", "Bearer " + token).param("limit", limit))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void returnsEmptyArrayWhenNoTasksMatch() throws Exception {
        insertTask("past", USER_ID, referenceTime.minusSeconds(3600));

        upcoming()
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void returns401WithoutAuthentication() throws Exception {
        mockMvc.perform(get("/tasks/upcoming"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void taskTimezoneDoesNotChangeInstantComparison() throws Exception {
        String[] timezones = {"Etc/GMT+12", "Pacific/Kiritimati", null};
        for (int i = 0; i < timezones.length; i++) {
            insertTask("future-" + i, USER_ID, referenceTime.plusSeconds(3600));
            insertTask("past-" + i, USER_ID, referenceTime.minusSeconds(3600));
            jdbc.update("UPDATE task SET timezone = ? WHERE id IN (?, ?)",
                    timezones[i], "future-" + i, "past-" + i);
        }

        upcoming()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains("future-0", "future-1", "future-2")))
                .andExpect(jsonPath("$[0].timezone").value("Etc/GMT+12"))
                .andExpect(jsonPath("$[1].timezone").value("Pacific/Kiritimati"))
                .andExpect(jsonPath("$[2].timezone").value(nullValue()));
    }

    @Test
    void includesUpcomingOccurrencesOfRecurringTasks() throws Exception {
        // First occurrence was yesterday: the next ones are today and tomorrow.
        Instant firstStart = referenceTime.minus(1, ChronoUnit.DAYS).plusSeconds(3600);
        insertTask("daily-series", USER_ID, firstStart);
        makeRecurring("daily-series", "daily", 1);

        mockMvc.perform(get("/tasks/upcoming").header("Authorization", "Bearer " + token).param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].taskId", contains("daily-series", "daily-series")))
                .andExpect(jsonPath("$[*].startsAt", contains(
                        referenceTime.plusSeconds(3600).toString(),
                        referenceTime.plus(1, ChronoUnit.DAYS).plusSeconds(3600).toString())))
                .andExpect(jsonPath("$[0].id").value("daily-series_" + referenceTime.plusSeconds(3600)));
    }

    @Test
    void recurringOccurrenceKeepsTheCompleteTaskData() throws Exception {
        // The queue only loads schedule columns; the response must still carry
        // everything else, loaded afterwards for the selected occurrences.
        jdbc.update("INSERT INTO category (id, user_id, title) VALUES (?, ?, ?)",
                "recurring-category", USER_ID, "Estudos");
        insertTask("study-series", USER_ID, referenceTime.minus(1, ChronoUnit.DAYS).plusSeconds(3600));
        makeRecurring("study-series", "daily", 1);
        jdbc.update("""
                UPDATE task SET description = ?, location = ?, category_id = ?, priority = ?
                WHERE id = ?
                """, "Revisar heap binario", "Biblioteca", "recurring-category", "HIGH", "study-series");

        mockMvc.perform(get("/tasks/upcoming").header("Authorization", "Bearer " + token).param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("study-series_" + referenceTime.plusSeconds(3600)))
                .andExpect(jsonPath("$[0].taskId").value("study-series"))
                .andExpect(jsonPath("$[0].title").value("Task study-series"))
                .andExpect(jsonPath("$[0].description").value("Revisar heap binario"))
                .andExpect(jsonPath("$[0].location").value("Biblioteca"))
                .andExpect(jsonPath("$[0].priority").value("HIGH"))
                .andExpect(jsonPath("$[0].categoryId").value("recurring-category"));
    }

    @Test
    void mergesRecurringOccurrencesWithTasksChronologicallyAndRespectsLimit() throws Exception {
        insertTask("single-in-2h", USER_ID, referenceTime.plusSeconds(7200));
        insertTask("single-in-2d", USER_ID, referenceTime.plus(2, ChronoUnit.DAYS));
        insertTask("daily-at-1h", USER_ID, referenceTime.minus(3, ChronoUnit.DAYS).plusSeconds(3600));
        makeRecurring("daily-at-1h", "daily", 1);

        mockMvc.perform(get("/tasks/upcoming").header("Authorization", "Bearer " + token).param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].taskId", contains("daily-at-1h", "single-in-2h", "daily-at-1h")));
    }

    @Test
    void recurringOccurrencesRespectStrideAndKeepDuration() throws Exception {
        Instant firstStart = referenceTime.minus(7, ChronoUnit.DAYS).plusSeconds(3600);
        insertTask("biweekly", USER_ID, firstStart);
        makeRecurring("biweekly", "weekly", 2);
        jdbc.update("UPDATE task SET ends_at = starts_at + INTERVAL '30 minutes' WHERE id = ?", "biweekly");

        Instant next = firstStart.plus(14, ChronoUnit.DAYS);
        mockMvc.perform(get("/tasks/upcoming").header("Authorization", "Bearer " + token).param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startsAt").value(next.toString()))
                .andExpect(jsonPath("$[0].endsAt").value(next.plus(30, ChronoUnit.MINUTES).toString()));
    }

    @Test
    void excludesCompletedAndSoftDeletedRecurringTasks() throws Exception {
        for (String id : List.of("completed-series", "deleted-series", "active-series")) {
            insertTask(id, USER_ID, referenceTime.minus(1, ChronoUnit.DAYS).plusSeconds(3600));
            makeRecurring(id, "daily", 1);
        }
        jdbc.update("UPDATE task SET completed_at = CURRENT_TIMESTAMP WHERE id = ?", "completed-series");
        jdbc.update("UPDATE task SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", "deleted-series");

        mockMvc.perform(get("/tasks/upcoming").header("Authorization", "Bearer " + token).param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].taskId", contains(
                        "active-series", "active-series", "active-series", "active-series", "active-series")));
    }

    @Test
    void onlyReturnsRecurringOccurrencesOfAuthenticatedUser() throws Exception {
        insertTask("other-series", OTHER_USER_ID, referenceTime.minus(1, ChronoUnit.DAYS));
        makeRecurring("other-series", "daily", 1);
        insertTask("owned", USER_ID, referenceTime.plusSeconds(3600));

        upcoming()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].taskId", contains("owned")));
    }

    @Test
    void maximumLimitWithRecurringTaskReturnsOneHundredOccurrences() throws Exception {
        insertTask("daily-series", USER_ID, referenceTime.plusSeconds(3600));
        makeRecurring("daily-series", "daily", 1);

        mockMvc.perform(get("/tasks/upcoming").header("Authorization", "Bearer " + token)
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(100))
                .andExpect(jsonPath("$[99].startsAt")
                        .value(referenceTime.plusSeconds(3600).plus(99, ChronoUnit.DAYS).toString()));
    }

    /**
     * Many recurring and non-recurring tasks: the merged page must be exactly
     * the earliest occurrences overall, in chronological order. The expected
     * page is computed here with plain date arithmetic, independently of
     * TaskOccurrences.
     */
    @Test
    void largeMixedDatasetReturnsEarliestOccurrencesInChronologicalOrder() throws Exception {
        List<String[]> expected = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();

        for (int i = 0; i < 300; i++) {
            String id = "rec-" + i;
            boolean daily = i % 2 == 0;
            int stride = daily ? 1 + i % 3 : 1 + i % 2;
            long periodDays = (daily ? 1L : 7L) * stride;
            // Whole minutes that never fall exactly on referenceTime.
            Instant firstStart = referenceTime
                    .minus(i % 10, ChronoUnit.DAYS)
                    .plus((i * 37L) % 1439 + 1, ChronoUnit.MINUTES);
            rows.add(new Object[]{id, USER_ID, "Task " + id, firstStart.toString(),
                    daily ? "daily" : "weekly", stride});

            int added = 0;
            for (long n = 0; added < 100; n++) {
                Instant start = firstStart.plus(n * periodDays, ChronoUnit.DAYS);
                if (!start.isBefore(referenceTime)) {
                    expected.add(new String[]{start.toString(), id + "_" + start});
                    added++;
                }
            }
        }
        for (int j = 0; j < 200; j++) {
            String id = "single-" + j;
            Instant start = referenceTime.plus((j * 53L) % 20000 + 1, ChronoUnit.MINUTES);
            rows.add(new Object[]{id, USER_ID, "Task " + id, start.toString(), null, null});
            expected.add(new String[]{start.toString(), id});
        }
        jdbc.batchUpdate("""
                INSERT INTO task (id, user_id, title, starts_at, repeat_interval, repeat)
                VALUES (?, ?, ?, CAST(? AS TIMESTAMPTZ), ?, ?)
                """, rows);

        List<String> expectedIds = expected.stream()
                .sorted(Comparator.<String[], Instant>comparing(entry -> Instant.parse(entry[0]))
                        .thenComparing(entry -> entry[1]))
                .limit(100)
                .map(entry -> entry[1])
                .toList();

        String json = mockMvc.perform(get("/tasks/upcoming").header("Authorization", "Bearer " + token)
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> actualIds = JsonPath.read(json, "$[*].id");
        assertEquals(expectedIds, actualIds);
    }

    private void makeRecurring(String id, String repeatInterval, int repeat) {
        jdbc.update("UPDATE task SET repeat_interval = ?, repeat = ? WHERE id = ?", repeatInterval, repeat, id);
    }

    private void insertTask(String id, String userId, Instant startsAt) {
        jdbc.update("""
                INSERT INTO task (id, user_id, title, starts_at)
                VALUES (?, ?, ?, CAST(? AS TIMESTAMPTZ))
                """, id, userId, "Task " + id, startsAt.toString());
    }

    private ResultActions upcoming() throws Exception {
        return mockMvc.perform(get("/tasks/upcoming").header("Authorization", "Bearer " + token));
    }
}
