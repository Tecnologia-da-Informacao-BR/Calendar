package br.com.calendar.task;

import br.com.calendar.auth.JwtUtil;
import br.com.calendar.reminder.Reminder;
import br.com.calendar.reminder.ReminderRepository;
import br.com.calendar.reminder.ReminderStatus;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @Autowired
    private ReminderRepository reminderRepository;

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

    @ParameterizedTest
    @ValueSource(strings = {"PATCH", "PUT"})
    void updateTask_ReschedulesReminderWhenStartChanges(String method) throws Exception {
        Instant previousStartsAt = Instant.parse("2026-11-10T18:00:00Z");
        Instant currentStartsAt = Instant.parse("2026-11-10T19:00:00Z");

        MvcResult creationResult = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Rescheduled meeting",
                                    "startsAt": "2026-11-10T18:00:00Z",
                                    "reminderMinutesBefore": 45
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String taskId = JsonPath.read(
                creationResult.getResponse().getContentAsString(),
                "$.id"
        );

        String updatePayload;
        if ("PUT".equals(method)) {
            updatePayload = """
                    {
                        "title": "Rescheduled meeting",
                        "startsAt": "2026-11-10T19:00:00Z",
                        "reminderMinutesBefore": 45
                    }
                    """;
        } else {
            updatePayload = """
                    {"startsAt": "2026-11-10T19:00:00Z"}
                    """;
        }

        mockMvc.perform(request(HttpMethod.valueOf(method), "/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reminderMinutesBefore").value(45));

        entityManager.flush();
        entityManager.clear();

        Reminder previousReminder = reminderRepository
                .findByTask_IdAndOccurrenceStartsAt(taskId, previousStartsAt)
                .orElseThrow();
        Reminder currentReminder = reminderRepository
                .findByTask_IdAndOccurrenceStartsAt(taskId, currentStartsAt)
                .orElseThrow();

        assertEquals(ReminderStatus.CANCELED, previousReminder.getStatus());
        assertEquals(
                Instant.parse("2026-11-10T17:15:00Z"),
                previousReminder.getScheduledAt()
        );
        assertEquals(ReminderStatus.PENDING, currentReminder.getStatus());
        assertEquals(
                Instant.parse("2026-11-10T18:15:00Z"),
                currentReminder.getScheduledAt()
        );
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

    @Test
    void deleteTask_CancelsPendingReminder() throws Exception {
        String taskId = createTaskWithReminder("Deleted meeting", 45);

        mockMvc.perform(delete("/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        Reminder reminder = reminderRepository
                .findByTask_IdAndOccurrenceStartsAt(taskId, Instant.parse("2026-11-10T18:00:00Z"))
                .orElseThrow();

        assertEquals(ReminderStatus.CANCELED, reminder.getStatus());
        assertEquals(Instant.parse("2026-11-10T17:15:00Z"), reminder.getScheduledAt());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM task WHERE id = ? AND deleted_at IS NOT NULL",
                Integer.class,
                taskId
        ));
    }

    @Test
    void deleteTask_KeepsProcessedReminderHistory() throws Exception {
        String taskId = createTaskWithReminder("Already reminded meeting", 45);
        jdbc.update("UPDATE reminder SET status = 'READY' WHERE task_id = ?", taskId);

        mockMvc.perform(delete("/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        assertEquals("READY", jdbc.queryForObject(
                "SELECT status FROM reminder WHERE task_id = ?",
                String.class,
                taskId
        ));
    }

    @Test
    void deleteTask_FromAnotherUser_KeepsReminderPending() throws Exception {
        String taskId = createTaskWithReminder("Protected meeting", 45);

        String otherUserId = "reminder-intruder";
        jdbc.update(
                "INSERT INTO users (id, name, email_confirmed) VALUES (?, ?, false)",
                otherUserId,
                "Intruder"
        );

        mockMvc.perform(delete("/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(otherUserId)))
                .andExpect(status().isForbidden());

        entityManager.flush();
        entityManager.clear();

        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM reminder WHERE task_id = ?",
                String.class,
                taskId
        ));
        assertEquals(USER_ID, jdbc.queryForObject(
                "SELECT user_id FROM reminder WHERE task_id = ?",
                String.class,
                taskId
        ));
    }

    @Test
    void patchTask_CompletingTask_CancelsPendingReminder() throws Exception {
        String taskId = createTaskWithReminder("Completed meeting", 45);

        mockMvc.perform(patch("/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"completedAt": "2026-11-10T12:00:00Z"}
                                """))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        assertEquals("CANCELED", jdbc.queryForObject(
                "SELECT status FROM reminder WHERE task_id = ?",
                String.class,
                taskId
        ));
    }

    @Test
    void patchTask_DisableThenEnableReminder_ReactivatesSameOccurrence() throws Exception {
        String taskId = createTaskWithReminder("Toggled meeting", 45);

        mockMvc.perform(patch("/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reminderMinutesBefore": null}
                                """))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        assertEquals("CANCELED", jdbc.queryForObject(
                "SELECT status FROM reminder WHERE task_id = ?",
                String.class,
                taskId
        ));

        mockMvc.perform(patch("/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reminderMinutesBefore": 30}
                                """))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        Reminder reminder = reminderRepository
                .findByTask_IdAndOccurrenceStartsAt(taskId, Instant.parse("2026-11-10T18:00:00Z"))
                .orElseThrow();

        assertEquals(ReminderStatus.PENDING, reminder.getStatus());
        assertEquals(Instant.parse("2026-11-10T17:30:00Z"), reminder.getScheduledAt());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM reminder WHERE task_id = ?",
                Integer.class,
                taskId
        ));
    }

    private String createTaskWithReminder(String title, int reminderMinutesBefore) throws Exception {
        MvcResult result = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "%s",
                                    "startsAt": "2026-11-10T18:00:00Z",
                                    "reminderMinutesBefore": %d
                                }
                                """.formatted(title, reminderMinutesBefore)))
                .andExpect(status().isCreated())
                .andReturn();

        entityManager.flush();
        entityManager.clear();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
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