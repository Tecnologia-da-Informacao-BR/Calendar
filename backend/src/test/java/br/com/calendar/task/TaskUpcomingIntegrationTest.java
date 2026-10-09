package br.com.calendar.task;

import br.com.calendar.auth.JwtUtil;
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
    void acceptsLimitWithoutBusinessMaximum() throws Exception {
        insertTask("future", USER_ID, referenceTime.plusSeconds(3600));

        mockMvc.perform(get("/tasks/upcoming").header("Authorization", "Bearer " + token)
                        .param("limit", Integer.toString(Integer.MAX_VALUE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains("future")));
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
    void usesPersistedStartsAtWithoutExpandingRecurrences() throws Exception {
        insertTask("past-recurring", USER_ID, referenceTime.minus(1, ChronoUnit.DAYS));
        insertTask("future-recurring", USER_ID, referenceTime.plusSeconds(3600));
        jdbc.update("UPDATE task SET repeat_interval = 'daily', repeat = 1 WHERE user_id = ?", USER_ID);

        upcoming()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains("future-recurring")));
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
