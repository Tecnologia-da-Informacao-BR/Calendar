package br.com.calendar.task;

import br.com.calendar.auth.JwtUtil;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TaskDailyIntegrationTest {

    private static final String USER_ID = "daily-owner";
    private static final String OTHER_USER_ID = "daily-other";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtUtil jwtUtil;

    private String token;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jdbc.update("INSERT INTO users (id, name, email_confirmed) VALUES (?, ?, false), (?, ?, false)",
                USER_ID, "Daily owner", OTHER_USER_ID, "Other user");
        token = jwtUtil.generateToken(USER_ID);
    }

    @Test
    void returnsWeeklyOccurrenceOnRequestedDay() throws Exception {
        insertTask("weekly-meeting", USER_ID, "2026-11-02T18:00:00Z", "2026-11-02T18:30:00Z", "weekly", 1);

        daily("2026-11-09")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("weekly-meeting_2026-11-09T18:00:00Z"))
                .andExpect(jsonPath("$[0].taskId").value("weekly-meeting"))
                .andExpect(jsonPath("$[0].startsAt").value("2026-11-09T18:00:00Z"))
                .andExpect(jsonPath("$[0].endsAt").value("2026-11-09T18:30:00Z"))
                .andExpect(jsonPath("$[0].repeatInterval").value("weekly"));
    }

    @ParameterizedTest
    @CsvSource({
            "daily,   1, 2026-11-05, true",
            "daily,   3, 2026-11-04, true",
            "daily,   3, 2026-11-05, false",
            "weekly,  2, 2026-11-15, true",
            "weekly,  2, 2026-11-08, false",
            "monthly, 1, 2027-01-01, true",
            "monthly, 1, 2026-12-02, false",
            "yearly,  1, 2028-11-01, true",
            "yearly,  1, 2027-10-31, false"
    })
    void expandsEveryIntervalRespectingTheStride(String interval, int stride, String day, boolean expected)
            throws Exception {
        insertTask("recurring", USER_ID, "2026-11-01T10:00:00Z", null, interval, stride);

        daily(day)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(expected ? 1 : 0));
    }

    @Test
    void returnsRecurringAndNonRecurringTasksInChronologicalOrder() throws Exception {
        insertTask("lunch", USER_ID, "2026-11-09T12:00:00Z", null, null, null);
        insertTask("standup", USER_ID, "2026-10-01T09:00:00Z", "2026-10-01T09:15:00Z", "daily", 1);
        insertTask("review", USER_ID, "2026-11-02T16:00:00Z", null, "weekly", 1);
        insertTask("early", USER_ID, "2026-11-09T07:00:00Z", null, null, null);

        daily("2026-11-09")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].taskId", contains("early", "standup", "lunch", "review")))
                .andExpect(jsonPath("$[*].startsAt", contains(
                        "2026-11-09T07:00:00Z",
                        "2026-11-09T09:00:00Z",
                        "2026-11-09T12:00:00Z",
                        "2026-11-09T16:00:00Z")))
                // Non-recurring tasks keep their own id.
                .andExpect(jsonPath("$[0].id").value("early"));
    }

    @Test
    void onlyListsOccurrencesStartingOnTheDay() throws Exception {
        // 23:00 to 01:00: the occurrence of the 8th is still running on the 9th.
        insertTask("night-shift", USER_ID, "2026-11-01T23:00:00Z", "2026-11-02T01:00:00Z", "daily", 1);

        daily("2026-11-09")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].startsAt", contains("2026-11-09T23:00:00Z")));
    }

    @Test
    void doesNotListRecurringTaskBeforeItsFirstOccurrence() throws Exception {
        insertTask("future-series", USER_ID, "2026-11-10T09:00:00Z", null, "daily", 1);

        daily("2026-11-09")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void excludesSoftDeletedRecurringTasks() throws Exception {
        insertTask("deleted-series", USER_ID, "2026-11-01T09:00:00Z", null, "daily", 1);
        insertTask("active-series", USER_ID, "2026-11-01T10:00:00Z", null, "daily", 1);
        jdbc.update("UPDATE task SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", "deleted-series");

        daily("2026-11-09")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].taskId", contains("active-series")));
    }

    @Test
    void onlyReturnsOccurrencesOfTheAuthenticatedUser() throws Exception {
        insertTask("mine", USER_ID, "2026-11-01T09:00:00Z", null, "daily", 1);
        insertTask("theirs", OTHER_USER_ID, "2026-11-01T08:00:00Z", null, "daily", 1);

        daily("2026-11-09")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].taskId", contains("mine")));
    }

    @Test
    void occurrenceMatchesTheMonthView() throws Exception {
        insertTask("monthly-sync", USER_ID, "2026-01-31T14:00:00Z", "2026-01-31T15:00:00Z", "monthly", 1);

        String dailyJson = daily("2026-02-28")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String monthJson = mockMvc.perform(get("/tasks")
                        .param("month", "2")
                        .param("year", "2026")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> dailyEntries = JsonPath.read(dailyJson, "$");
        List<Map<String, Object>> monthEntries = JsonPath.read(monthJson, "$");
        assertEquals(1, dailyEntries.size());
        assertEquals(1, monthEntries.size());
        for (String field : List.of("id", "taskId", "startsAt", "endsAt")) {
            assertEquals(monthEntries.get(0).get(field), dailyEntries.get(0).get(field), field);
        }
    }

    private ResultActions daily(String date) throws Exception {
        return mockMvc.perform(get("/tasks")
                .param("date", date)
                .header("Authorization", "Bearer " + token));
    }

    private void insertTask(String id, String userId, String startsAt, String endsAt,
                            String repeatInterval, Integer repeat) {
        jdbc.update("""
                        INSERT INTO task (id, user_id, title, starts_at, ends_at, repeat_interval, repeat)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                userId,
                "Task " + id,
                OffsetDateTime.parse(startsAt),
                endsAt == null ? null : OffsetDateTime.parse(endsAt),
                repeatInterval,
                repeat);
    }
}
