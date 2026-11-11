package br.com.calendar.task;

import br.com.calendar.auth.JwtUtil;
import br.com.calendar.reminder.ReadyReminder;
import br.com.calendar.reminder.ReminderDeliveryService;
import br.com.calendar.reminder.ReminderProcessingResult;
import br.com.calendar.reminder.ReminderProcessor;
import br.com.calendar.reminder.ReminderRepository;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Edge cases raised in the review of #228: task changes that happen while a
 * reminder is being processed, and task changes after a reminder already
 * reached READY. In every case the reminder must not end up deliverable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TaskReminderEdgeCasesIntegrationTest {

    private static final String USER_ID = "edge-case-owner";
    private static final Instant STARTS_AT = Instant.parse("2026-11-10T18:00:00Z");
    private static final Instant NEW_STARTS_AT = Instant.parse("2026-11-10T19:00:00Z");
    // After the reminder time (17:15) and before the event start (18:00).
    private static final Instant NOW = Instant.parse("2026-11-10T17:20:00Z");

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

    @Autowired
    private ReminderProcessor reminderProcessor;

    @Autowired
    private ReminderDeliveryService deliveryService;

    private String token;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jdbc.update(
                "INSERT INTO users (id, name, email_confirmed) VALUES (?, ?, false)",
                USER_ID,
                "Edge case owner"
        );
        token = jwtUtil.generateToken(USER_ID);
    }

    // ---------------------------------------------------------------------
    // 1. Task changed while its reminder is being processed (PROCESSING)
    // ---------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETE", "DELETE", "DISABLE_REMINDER", "CHANGE_START"})
    void taskChangedWhileReminderIsProcessing_NeverBecomesReady(String change) throws Exception {
        String taskId = createTaskWithReminder();
        String reminderId = reminderIdOf(taskId, STARTS_AT);

        // A scheduler instance claims the reminder (step 2 of a run)...
        String claimToken = claim();
        assertEquals("PROCESSING", statusOf(reminderId));

        // ...the user changes the task through the API before it finishes...
        applyChange(taskId, change);

        // ...and the scheduler then tries to finish its claim (step 3).
        ReminderProcessingResult result = reminderProcessor.finishClaim(claimToken, NOW);

        assertEquals(0, result.ready());
        assertEquals("CANCELED", statusOf(reminderId));
        assertNull(claimTokenOf(reminderId));
        assertNotDeliverable(reminderId);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE task SET completed_at = TIMESTAMPTZ '2026-11-10T17:16:00Z' WHERE id = ?",
            "UPDATE task SET deleted_at = TIMESTAMPTZ '2026-11-10T17:16:00Z' WHERE id = ?",
            "UPDATE task SET reminder_minutes_before = NULL WHERE id = ?",
            "UPDATE task SET starts_at = TIMESTAMPTZ '2026-11-10T19:00:00Z' WHERE id = ?"
    })
    void schedulerRereadsTaskBeforeMarkingReady(String taskChange) throws Exception {
        String taskId = createTaskWithReminder();
        String reminderId = reminderIdOf(taskId, STARTS_AT);
        String claimToken = claim();

        // The task row changes without going through the reminder lifecycle
        // (worst case): the scheduler must still notice it when finishing.
        jdbc.update(taskChange, taskId);

        reminderProcessor.finishClaim(claimToken, NOW);

        assertEquals("CANCELED", statusOf(reminderId));
        assertNotDeliverable(reminderId);
    }

    // ---------------------------------------------------------------------
    // 2. Task changed after its reminder already reached READY
    // ---------------------------------------------------------------------

    @Test
    void startChangedAfterReminderIsReady_OldReminderIsNotDelivered() throws Exception {
        String taskId = createTaskWithReminder();
        String oldReminderId = reminderIdOf(taskId, STARTS_AT);
        reminderProcessor.finishClaim(claim(), NOW);
        assertEquals("READY", statusOf(oldReminderId));

        applyChange(taskId, "CHANGE_START");

        assertEquals("CANCELED", statusOf(oldReminderId));
        assertNotDeliverable(oldReminderId);

        String newReminderId = reminderIdOf(taskId, NEW_STARTS_AT);
        assertEquals("PENDING", statusOf(newReminderId));
        assertEquals(Instant.parse("2026-11-10T18:15:00Z"), jdbc.queryForObject(
                "SELECT scheduled_at FROM reminder WHERE id = ?",
                Timestamp.class,
                newReminderId
        ).toInstant());
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETE", "DELETE", "DISABLE_REMINDER"})
    void taskChangedAfterReminderIsReady_ReminderIsCanceled(String change) throws Exception {
        String taskId = createTaskWithReminder();
        String reminderId = reminderIdOf(taskId, STARTS_AT);
        reminderProcessor.finishClaim(claim(), NOW);
        assertEquals("READY", statusOf(reminderId));

        applyChange(taskId, change);

        assertEquals("CANCELED", statusOf(reminderId));
        assertNotDeliverable(reminderId);
    }

    @Test
    void readyReminderOfOutdatedOccurrence_IsRejectedByRevalidation() throws Exception {
        String taskId = createTaskWithReminder();
        String reminderId = reminderIdOf(taskId, STARTS_AT);
        reminderProcessor.finishClaim(claim(), NOW);

        // Start time changed directly in the database: the delivery contract
        // is the last line of defense.
        jdbc.update("UPDATE task SET starts_at = TIMESTAMPTZ '2026-11-10T19:00:00Z' WHERE id = ?", taskId);

        assertFalse(deliverableIds().contains(reminderId));
        assertTrue(deliveryService.revalidate(reminderId, NOW).isEmpty());
        assertEquals("CANCELED", statusOf(reminderId));
    }

    // ---------------------------------------------------------------------

    private String createTaskWithReminder() throws Exception {
        MvcResult result = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Edge case meeting",
                                    "startsAt": "2026-11-10T18:00:00Z",
                                    "reminderMinutesBefore": 45
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        entityManager.flush();
        entityManager.clear();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String claim() {
        String claimToken = java.util.UUID.randomUUID().toString();
        reminderRepository.claimDuePending(claimToken, NOW, NOW.plusSeconds(120), 1000);
        return claimToken;
    }

    private void applyChange(String taskId, String change) throws Exception {
        switch (change) {
            case "COMPLETE" -> patchTask(taskId, """
                    {"completedAt": "2026-11-10T17:16:00Z"}
                    """);
            case "DISABLE_REMINDER" -> patchTask(taskId, """
                    {"reminderMinutesBefore": null}
                    """);
            case "CHANGE_START" -> patchTask(taskId, """
                    {"startsAt": "2026-11-10T19:00:00Z"}
                    """);
            case "DELETE" -> mockMvc.perform(delete("/tasks/{id}", taskId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());
            default -> throw new IllegalArgumentException(change);
        }
        entityManager.flush();
        entityManager.clear();
    }

    private void patchTask(String taskId, String body) throws Exception {
        mockMvc.perform(patch("/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void assertNotDeliverable(String reminderId) {
        assertFalse(deliverableIds().contains(reminderId));
        assertTrue(deliveryService.revalidate(reminderId, NOW).isEmpty());
    }

    private List<String> deliverableIds() {
        return deliveryService.findReadyReminders(NOW, 1000).stream()
                .map(ReadyReminder::reminderId)
                .toList();
    }

    private String reminderIdOf(String taskId, Instant occurrenceStartsAt) {
        return jdbc.queryForObject(
                "SELECT id FROM reminder WHERE task_id = ? AND occurrence_starts_at = ?",
                String.class,
                taskId,
                Timestamp.from(occurrenceStartsAt)
        );
    }

    private String statusOf(String reminderId) {
        return jdbc.queryForObject("SELECT status FROM reminder WHERE id = ?", String.class, reminderId);
    }

    private String claimTokenOf(String reminderId) {
        return jdbc.queryForObject("SELECT claim_token FROM reminder WHERE id = ?", String.class, reminderId);
    }
}
