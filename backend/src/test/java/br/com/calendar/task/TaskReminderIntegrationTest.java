package br.com.calendar.task;

import br.com.calendar.auth.JwtUtil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TaskReminderIntegrationTest {

    private static final String USER_ID = "reminder-owner";

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

        jdbc.update(
                "INSERT INTO users (id, name, email_confirmed) VALUES (?, ?, false)",
                USER_ID,
                "Reminder owner"
        );

        token = jwtUtil.generateToken(USER_ID);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 45, 525600})
    void createTask_PersistsReminderOffset(int reminderMinutesBefore) throws Exception {
        String payload = """
                {
                    "title": "Meeting",
                    "startsAt": "2026-11-10T18:00:00Z",
                    "reminderMinutesBefore": %d
                }
                """.formatted(reminderMinutesBefore);

        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reminderMinutesBefore")
                        .value(reminderMinutesBefore));

        entityManager.flush();
        entityManager.clear();

        Integer savedOffset = jdbc.queryForObject(
                """
                SELECT reminder_minutes_before
                FROM task
                WHERE user_id = ? AND title = ?
                """,
                Integer.class,
                USER_ID,
                "Meeting"
        );

        assertEquals(Integer.valueOf(reminderMinutesBefore), savedOffset);
        String reminderStatus = jdbc.queryForObject(
                """
                SELECT r.status
                FROM reminder r
                JOIN task t ON t.id = r.task_id
                WHERE t.user_id = ?
                  AND t.title = ?
                  AND r.user_id = t.user_id
                """,
                String.class,
                USER_ID,
                "Meeting"
        );

        assertEquals("PENDING", reminderStatus);
    }

    @Test
    void patchTask_OmittedReminder_PreservesSavedOffset() throws Exception {
        insertTaskWithReminder();

        mockMvc.perform(patch("/tasks/reminder-task")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Updated meeting"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated meeting"))
                .andExpect(jsonPath("$.reminderMinutesBefore").value(45));

        entityManager.flush();
        entityManager.clear();

        assertEquals(Integer.valueOf(45), jdbc.queryForObject(
                "SELECT reminder_minutes_before FROM task WHERE id = ?",
                Integer.class,
                "reminder-task"
        ));
    }

    @Test
    void patchTask_ExplicitNullReminder_DisablesSavedReminder() throws Exception {
        insertTaskWithReminder();

        mockMvc.perform(patch("/tasks/reminder-task")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reminderMinutesBefore": null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Meeting"))
                .andExpect(jsonPath("$.reminderMinutesBefore").value(nullValue()));

        entityManager.flush();
        entityManager.clear();

        assertNull(jdbc.queryForObject(
                "SELECT reminder_minutes_before FROM task WHERE id = ?",
                Integer.class,
                "reminder-task"
        ));
    }

    @Test
    void createTask_WithoutReminder_PersistsNullOffset() throws Exception {
        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Meeting without reminder",
                                    "startsAt": "2026-11-10T18:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reminderMinutesBefore").value(nullValue()));

        entityManager.flush();
        entityManager.clear();

        assertNull(jdbc.queryForObject(
                """
                SELECT reminder_minutes_before
                FROM task
                WHERE user_id = ? AND title = ?
                """,
                Integer.class,
                USER_ID,
                "Meeting without reminder"
        ));
    }

    @Test
    void replaceTask_OmittedReminder_ClearsSavedOffset() throws Exception {
        insertTaskWithReminder();

        mockMvc.perform(put("/tasks/reminder-task")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Replaced meeting",
                                    "startsAt": "2026-11-10T19:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Replaced meeting"))
                .andExpect(jsonPath("$.reminderMinutesBefore").value(nullValue()));

        entityManager.flush();
        entityManager.clear();

        assertNull(jdbc.queryForObject(
                "SELECT reminder_minutes_before FROM task WHERE id = ?",
                Integer.class,
                "reminder-task"
        ));
    }

    @Test
    void patchTask_UpdatesSavedReminderOffset() throws Exception {
        insertTaskWithReminder();

        mockMvc.perform(patch("/tasks/reminder-task")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reminderMinutesBefore": 30}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Meeting"))
                .andExpect(jsonPath("$.reminderMinutesBefore").value(30));

        entityManager.flush();
        entityManager.clear();

        assertEquals(Integer.valueOf(30), jdbc.queryForObject(
                "SELECT reminder_minutes_before FROM task WHERE id = ?",
                Integer.class,
                "reminder-task"
        ));
    }

    @Test
    void patchTask_RejectsReminderUpdateFromAnotherUser() throws Exception {
        insertTaskWithReminder();

        String otherUserId = "reminder-other";

        jdbc.update(
                "INSERT INTO users (id, name, email_confirmed) VALUES (?, ?, false)",
                otherUserId,
                "Other user"
        );

        String otherToken = jwtUtil.generateToken(otherUserId);

        mockMvc.perform(patch("/tasks/reminder-task")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reminderMinutesBefore": 30}
                                """))
                .andExpect(status().isForbidden());

        entityManager.flush();
        entityManager.clear();

        assertEquals(Integer.valueOf(45), jdbc.queryForObject(
                "SELECT reminder_minutes_before FROM task WHERE id = ?",
                Integer.class,
                "reminder-task"
        ));
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /tasks",
            "PUT, /tasks/reminder-task",
            "PATCH, /tasks/reminder-task"
    })
    void reminderEndpoints_RejectUnauthenticatedRequests(
            String method,
            String path) throws Exception {
        insertTaskWithReminder();

        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Unauthorized change",
                                    "startsAt": "2026-11-10T18:00:00Z",
                                    "reminderMinutesBefore": 30
                                }
                                """))
                .andExpect(status().isUnauthorized());

        entityManager.flush();
        entityManager.clear();

        assertEquals(Integer.valueOf(45), jdbc.queryForObject(
                "SELECT reminder_minutes_before FROM task WHERE id = ?",
                Integer.class,
                "reminder-task"
        ));

        assertEquals(Integer.valueOf(1), jdbc.queryForObject(
                "SELECT COUNT(*) FROM task WHERE user_id = ?",
                Integer.class,
                USER_ID
        ));
    }

    private void insertTaskWithReminder() {
        jdbc.update(
                """
                INSERT INTO task
                    (id, user_id, title, starts_at, reminder_minutes_before)
                VALUES (?, ?, ?, TIMESTAMPTZ '2026-11-10T18:00:00Z', ?)
                """,
                "reminder-task",
                USER_ID,
                "Meeting",
                45
        );
    }
}