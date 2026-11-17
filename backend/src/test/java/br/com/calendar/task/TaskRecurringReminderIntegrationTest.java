package br.com.calendar.task;

import br.com.calendar.auth.JwtUtil;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recurring series lifecycle through the API (issue #223). The API uses the
 * real clock, so the series start in the past and assertions are made
 * relative to the current time.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TaskRecurringReminderIntegrationTest {

    private static final String USER_ID = "recurring-api-owner";
    // Monday 15:00 UTC, weekly, reminder 30 minutes before.
    private static final String WEEKLY_MONDAY = """
            {
                "title": "Weekly meeting",
                "startsAt": "2026-11-02T15:00:00Z",
                "repeatInterval": "weekly",
                "repeat": 1,
                "reminderMinutesBefore": 30
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EntityManager entityManager;

    private String token;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jdbc.update("INSERT INTO users (id, name, email_confirmed) VALUES (?, ?, false)", USER_ID, "Recurring owner");
        token = jwtUtil.generateToken(USER_ID);
    }

    @Test
    void createRecurringTask_SchedulesEveryMondayAt1430() throws Exception {
        Instant before = Instant.now();
        String taskId = createTask(WEEKLY_MONDAY);

        List<Row> rows = rows(taskId);
        assertFalse(rows.isEmpty());
        assertTrue(rows.size() <= 2, "only the 7-day window is scheduled");
        for (Row row : rows) {
            ZonedDateTime occurrence = row.occurrence().atZone(ZoneOffset.UTC);
            assertEquals(DayOfWeek.MONDAY, occurrence.getDayOfWeek());
            assertEquals(LocalTime.of(15, 0), occurrence.toLocalTime());
            assertEquals(row.occurrence().minus(Duration.ofMinutes(30)), row.scheduledAt());
            assertEquals("PENDING", row.status());
            assertTrue(row.occurrence().isAfter(before));
        }
    }

    @Test
    void reminderOccurrencesMatchTheMonthlyCalendarQuery() throws Exception {
        String taskId = createTask("""
                {
                    "title": "Daily standup",
                    "startsAt": "2025-01-31T09:00:00Z",
                    "endsAt": "2025-01-31T09:15:00Z",
                    "repeatInterval": "daily",
                    "reminderMinutesBefore": 10
                }
                """);

        for (Row row : rows(taskId)) {
            ZonedDateTime occurrence = row.occurrence().atZone(ZoneOffset.UTC);
            MvcResult month = mockMvc.perform(get("/tasks")
                            .param("month", String.valueOf(occurrence.getMonthValue()))
                            .param("year", String.valueOf(occurrence.getYear()))
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            List<String> calendarStarts = JsonPath.read(month.getResponse().getContentAsString(), "$[*].startsAt");
            assertTrue(calendarStarts.stream().map(Instant::parse).anyMatch(row.occurrence()::equals),
                    "occurrence " + row.occurrence() + " missing from the month query");
        }
    }

    @Test
    void changingOffset_ReschedulesPendingRemindersInPlace() throws Exception {
        String taskId = createTask(WEEKLY_MONDAY);
        Map<Instant, String> idsBefore = idsByOccurrence(taskId);

        patchTask(taskId, """
                {"reminderMinutesBefore": 60}
                """);

        assertEquals(idsBefore, idsByOccurrence(taskId));
        for (Row row : rows(taskId)) {
            assertEquals("PENDING", row.status());
            assertEquals(row.occurrence().minus(Duration.ofMinutes(60)), row.scheduledAt());
        }
    }

    @Test
    void changingStart_CancelsOldOccurrencesAndSchedulesNewOnes() throws Exception {
        String taskId = createTask(WEEKLY_MONDAY);
        List<Instant> mondays = rows(taskId).stream().map(Row::occurrence).toList();

        patchTask(taskId, """
                {"startsAt": "2026-11-03T16:00:00Z"}
                """);

        for (Row row : rows(taskId)) {
            ZonedDateTime occurrence = row.occurrence().atZone(ZoneOffset.UTC);
            if (mondays.contains(row.occurrence())) {
                assertEquals("CANCELED", row.status());
            } else {
                assertEquals(DayOfWeek.TUESDAY, occurrence.getDayOfWeek());
                assertEquals(LocalTime.of(16, 0), occurrence.toLocalTime());
                assertEquals("PENDING", row.status());
            }
        }
        assertFalse(active(taskId).isEmpty());
    }

    @Test
    void changingRecurrence_KeepsSharedOccurrencesAndAddsTheNewOnes() throws Exception {
        String taskId = createTask(WEEKLY_MONDAY);
        Map<Instant, String> mondays = idsByOccurrence(taskId);

        patchTask(taskId, """
                {"repeatInterval": "daily"}
                """);

        List<Row> rows = rows(taskId);
        // Mondays at 15:00 are daily occurrences too: same rows, still PENDING.
        mondays.forEach((occurrence, id) -> assertEquals(id, idsByOccurrence(taskId).get(occurrence)));
        assertTrue(rows.stream().allMatch(row -> row.status().equals("PENDING")));
        assertTrue(rows.size() >= 7, "daily occurrences of the 7-day window");
        assertTrue(rows.stream().allMatch(row ->
                row.occurrence().atZone(ZoneOffset.UTC).toLocalTime().equals(LocalTime.of(15, 0))));
    }

    @Test
    void disablingRecurrence_CancelsTheSeriesAndKeepsTheSingleOccurrence() throws Exception {
        String taskId = createTask(WEEKLY_MONDAY);
        Instant single = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.HOURS);

        mockMvc.perform(put("/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Now a single meeting", "startsAt": "%s", "reminderMinutesBefore": 30}
                                """.formatted(single)))
                .andExpect(status().isOk());

        assertEquals(List.of(single), active(taskId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETE", "DELETE", "DISABLE_REMINDER"})
    void finishingTheSeries_CancelsEveryPendingReminder(String change) throws Exception {
        String taskId = createTask(WEEKLY_MONDAY);
        assertFalse(active(taskId).isEmpty());

        switch (change) {
            case "COMPLETE" -> patchTask(taskId, """
                    {"completedAt": "%s"}
                    """.formatted(Instant.now()));
            case "DISABLE_REMINDER" -> patchTask(taskId, """
                    {"reminderMinutesBefore": null}
                    """);
            default -> mockMvc.perform(delete("/tasks/{id}", taskId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());
        }

        assertEquals(List.of(), active(taskId));
        assertTrue(rows(taskId).stream().allMatch(row -> row.status().equals("CANCELED")));
    }

    private record Row(String id, Instant occurrence, Instant scheduledAt, String status) {
    }

    private List<Row> rows(String taskId) {
        entityManager.flush();
        return jdbc.query(
                "SELECT id, occurrence_starts_at, scheduled_at, status FROM reminder WHERE task_id = ? ORDER BY occurrence_starts_at",
                (rs, i) -> new Row(
                        rs.getString("id"),
                        rs.getTimestamp("occurrence_starts_at").toInstant(),
                        rs.getTimestamp("scheduled_at").toInstant(),
                        rs.getString("status")),
                taskId);
    }

    private List<Instant> active(String taskId) {
        return rows(taskId).stream()
                .filter(row -> List.of("PENDING", "PROCESSING", "READY").contains(row.status()))
                .map(Row::occurrence)
                .toList();
    }

    private Map<Instant, String> idsByOccurrence(String taskId) {
        return rows(taskId).stream().collect(Collectors.toMap(Row::occurrence, Row::id));
    }

    private String createTask(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void patchTask(String taskId, String body) throws Exception {
        mockMvc.perform(patch("/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
