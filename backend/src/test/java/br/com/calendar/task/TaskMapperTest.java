package br.com.calendar.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import br.com.calendar.category.Category;
import br.com.calendar.category.CategoryMapper;
import br.com.calendar.task.dto.TaskMonthResponseDTO;
import br.com.calendar.task.dto.TaskRequestDTO;
import br.com.calendar.task.dto.TaskResponseDTO;

class TaskMapperTest {

    private final TaskMapper mapper = new TaskMapper(new CategoryMapper());

    @Test
    void toEntity_IgnoresCompletedAtFromRequest() {
        // A newly created task must always start incomplete, regardless of
        // what the client sends — see issue #110's acceptance criteria.
        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("Task");
        request.setCompletedAt(Instant.now());

        Task task = mapper.toEntity(request, null);

        assertNull(task.getCompletedAt());
    }

    @Test
    void toEntity_MapsPriority() {
        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("Task");
        request.setPriority(TaskPriority.HIGH);

        Task task = mapper.toEntity(request, null);

        assertEquals(TaskPriority.HIGH, task.getPriority());
    }

    @Test
    void toEntity_SetsCategory() {
        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("Task");
        Category category = new Category();
        category.setId("cat123");

        Task task = mapper.toEntity(request, category);

        assertEquals(category, task.getCategory());
    }

    @Test
    void toResponse_MapsPriority() {
        Task task = new Task();
        task.setPriority(TaskPriority.MEDIUM);

        TaskResponseDTO response = mapper.toResponse(task);

        assertEquals(TaskPriority.MEDIUM, response.getPriority());
    }

    @Test
    void updateEntity_OmittedPriority_LeavesItUnchanged() {
        Task task = new Task();
        task.setPriority(TaskPriority.LOW);

        mapper.updateEntity(task, new TaskRequestDTO());

        assertEquals(TaskPriority.LOW, task.getPriority());
    }

    @Test
    void updateEntity_SetPriority_UpdatesIt() {
        Task task = new Task();
        task.setPriority(TaskPriority.LOW);

        TaskRequestDTO request = new TaskRequestDTO();
        request.setPriority(TaskPriority.HIGH);
        mapper.updateEntity(task, request);

        assertEquals(TaskPriority.HIGH, task.getPriority());
    }

    @Test
    void replaceEntity_OmittedPriority_ClearsIt() {
        Task task = new Task();
        task.setPriority(TaskPriority.LOW);

        mapper.replaceEntity(task, new TaskRequestDTO());

        assertNull(task.getPriority());
    }

    @Test
    void toMonthResponse_ResolvesCategory() {
        Category category = new Category();
        category.setId("cat123");
        category.setTitle("Work");
        category.setColor("3366FF");
        category.setIcon("briefcase");

        Task task = new Task();
        task.setCategory(category);

        TaskMonthResponseDTO response = mapper.toMonthResponse(task, "occ1", Instant.now(), null);

        assertEquals("cat123", response.getCategory().id());
        assertEquals("Work", response.getCategory().title());
    }

    @Test
    void toMonthResponse_NoCategory_ReturnsNullCategory() {
        Task task = new Task();

        TaskMonthResponseDTO response = mapper.toMonthResponse(task, "occ1", Instant.now(), null);

        assertNull(response.getCategory());
    }

    @Test
    void toMonthResponse_UsesOccurrenceIdAndDates_NotTheTasksOwn() {
        Task task = new Task();
        task.setId("task123");
        task.setStartsAt(Instant.parse("2026-01-01T00:00:00Z"));
        task.setEndsAt(Instant.parse("2026-01-01T01:00:00Z"));

        Instant occursAt = Instant.parse("2026-08-15T10:00:00Z");
        Instant occursUntil = Instant.parse("2026-08-15T11:00:00Z");

        TaskMonthResponseDTO response = mapper.toMonthResponse(task, "task123_occ", occursAt, occursUntil);

        assertEquals("task123_occ", response.getId());
        assertEquals("task123", response.getTaskId());
        assertEquals(occursAt, response.getStartsAt());
        assertEquals(occursUntil, response.getEndsAt());
    }

    @Test
    void updateEntity_OmittedReminder_PreservesExistingValue() {
        Task task = new Task();
        task.setReminderMinutesBefore(45);

        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("Updated title");

        mapper.updateEntity(task, request);

        assertEquals("Updated title", task.getTitle());
        assertEquals(Integer.valueOf(45), task.getReminderMinutesBefore());
    }

    @Test
    void updateEntity_ExplicitNullReminder_DisablesReminder() {
        Task task = new Task();
        task.setTitle("Original title");
        task.setReminderMinutesBefore(45);

        TaskRequestDTO request = new TaskRequestDTO();
        request.setReminderMinutesBefore(null);

        mapper.updateEntity(task, request);

        assertNull(task.getReminderMinutesBefore());
        assertEquals("Original title", task.getTitle());
    }

    @Test
    void replaceEntity_OmittedReminder_DisablesReminder() {
        Task task = new Task();
        task.setReminderMinutesBefore(45);

        mapper.replaceEntity(task, new TaskRequestDTO());

        assertNull(task.getReminderMinutesBefore());
    }

    @Test
    void toEntity_MapsReminderOffset() {
        TaskRequestDTO request = new TaskRequestDTO();
        request.setReminderMinutesBefore(45);

        Task task = mapper.toEntity(request, null);

        assertEquals(Integer.valueOf(45), task.getReminderMinutesBefore());
    }

    @Test
    void toEntity_WithoutReminder_LeavesItDisabled() {
        TaskRequestDTO request = new TaskRequestDTO();

        Task task = mapper.toEntity(request, null);

        assertNull(task.getReminderMinutesBefore());
    }

    @Test
    void toResponse_MapsReminderOffset() {
        Task task = new Task();
        task.setReminderMinutesBefore(45);

        TaskResponseDTO response = mapper.toResponse(task);

        assertEquals(Integer.valueOf(45), response.getReminderMinutesBefore());
    }

    @Test
    void toMonthResponse_MapsReminderOffset() {
        Task task = new Task();
        task.setReminderMinutesBefore(45);

        Instant occurrenceStartsAt = Instant.parse("2026-11-10T18:00:00Z");

        TaskMonthResponseDTO response = mapper.toMonthResponse(
                task,
                "task1_occurrence",
                occurrenceStartsAt,
                null
        );

        assertEquals(Integer.valueOf(45), response.getReminderMinutesBefore());
    }
}
