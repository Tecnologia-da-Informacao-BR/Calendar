package br.com.calendar.task;

import br.com.calendar.common.exception.GlobalExceptionHandler;
import br.com.calendar.common.exception.ResourceNotFoundException;
import br.com.calendar.task.dto.TaskMonthResponseDTO;
import br.com.calendar.task.dto.TaskRequestDTO;
import br.com.calendar.task.dto.TaskResponseDTO;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    @Mock
    private TaskService taskService;

    private MockMvc mockMvc;


    @BeforeEach
    void setUp() {
        JsonMapper mapper = JsonMapper.builder()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                .addModule(new SimpleModule().addSerializer(Page.class, new StdSerializer<>(Page.class) {
                    @Override
                    public void serialize(Page value, JsonGenerator gen, SerializationContext ctxt) {
                        gen.writeStartObject();
                        ctxt.defaultSerializeProperty("content", value.getContent(), gen);
                        gen.writeEndObject();
                    }
                }))
                .build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TaskController(taskService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper))
                .build();
    }

    @Test
    void createTask_Returns201_WhenValid() throws Exception {
        TaskResponseDTO response = TaskResponseDTO.builder().id("task1").title("My Task").build();
        when(taskService.createTask(any(TaskRequestDTO.class))).thenReturn(response);

        String payload = """
                {
                    "title": "My Task",
                    "startsAt": "2023-10-10T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("task1"))
                .andExpect(jsonPath("$.title").value("My Task"));

        verify(taskService).createTask(any(TaskRequestDTO.class));
    }

    @Test
    void createTask_Returns400_WhenServiceThrowsIllegalArgumentException() throws Exception {
        when(taskService.createTask(any(TaskRequestDTO.class)))
                .thenThrow(new IllegalArgumentException("Title is required."));

        String payload = """
                {
                    "startsAt": "2023-10-10T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Title is required."));
    }

    @Test
    void getTasksByDay_Returns200_WhenValidDate() throws Exception {
        TaskResponseDTO response = TaskResponseDTO.builder().id("task1").title("My Task").build();
        LocalDate date = LocalDate.of(2023, 10, 10);
        when(taskService.getTasksForDay(date)).thenReturn(List.of(response));

        mockMvc.perform(get("/tasks")
                        .param("date", "2023-10-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("task1"));

        verify(taskService).getTasksForDay(date);
    }

    @Test
    void getTasksByDay_Returns400_WhenInvalidDate() throws Exception {
        mockMvc.perform(get("/tasks")
                        .param("date", "invalid-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTasksByMonth_Returns200_WhenValidMonthAndYear() throws Exception {
        TaskMonthResponseDTO response = TaskMonthResponseDTO.builder().id("task1").title("My Task").build();
        when(taskService.getTasksForMonth(10, 2023)).thenReturn(List.of(response));

        mockMvc.perform(get("/tasks")
                        .param("month", "10")
                        .param("year", "2023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("task1"));

        verify(taskService).getTasksForMonth(10, 2023);
    }

    @Test
    void getTasksByMonth_Returns400_WhenServiceThrowsIllegalArgumentException() throws Exception {
        when(taskService.getTasksForMonth(13, 2023)).thenThrow(new IllegalArgumentException("Month must be between 1 and 12."));

        mockMvc.perform(get("/tasks")
                        .param("month", "13")
                        .param("year", "2023"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Month must be between 1 and 12."));
    }

    @Test
    void getTaskHistory_Returns200_WithPage() throws Exception {
        TaskResponseDTO response = TaskResponseDTO.builder().id("task1").title("My Task").build();
        when(taskService.getTaskHistory(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/tasks/history"))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("task1"));

        verify(taskService).getTaskHistory(any(Pageable.class));
    }

    @Test
    void deleteTask_Returns204_WhenSuccessful() throws Exception {
        doNothing().when(taskService).deleteTask("task1");

        mockMvc.perform(delete("/tasks/task1"))
                .andExpect(status().isNoContent());

        verify(taskService).deleteTask("task1");
    }

    @Test
    void deleteTask_Returns404_WhenNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Task not found")).when(taskService).deleteTask("task1");

        mockMvc.perform(delete("/tasks/task1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Task not found"));
    }

    @Test
    void deleteTask_Returns403_WhenAccessDenied() throws Exception {
        doThrow(new AccessDeniedException("Task does not belong to the current user")).when(taskService).deleteTask("task1");

        mockMvc.perform(delete("/tasks/task1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Task does not belong to the current user"));
    }

    @Test
    void replaceTask_Returns200_WhenSuccessful() throws Exception {
        TaskResponseDTO response = TaskResponseDTO.builder().id("task1").title("Updated Task").build();
        when(taskService.replaceTask(any(TaskRequestDTO.class), eq("task1"))).thenReturn(response);

        String payload = """
                {
                    "title": "Updated Task",
                    "startsAt": "2023-10-10T10:00:00Z"
                }
                """;

        mockMvc.perform(put("/tasks/task1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("task1"))
                .andExpect(jsonPath("$.title").value("Updated Task"));
    }

    @Test
    void patchTask_Returns200_WhenSuccessful() throws Exception {
        TaskResponseDTO response = TaskResponseDTO.builder().id("task1").title("Patched Task").build();
        when(taskService.updateTask(any(TaskRequestDTO.class), eq("task1"))).thenReturn(response);

        String payload = """
                {
                    "title": "Patched Task"
                }
                """;

        mockMvc.perform(patch("/tasks/task1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("task1"))
                .andExpect(jsonPath("$.title").value("Patched Task"));
    }

    @Test
    void patchTask_OmittedReminder_IsMarkedAsAbsent() throws Exception {
        mockMvc.perform(patch("/tasks/task1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Updated title"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<TaskRequestDTO> captor =
                ArgumentCaptor.forClass(TaskRequestDTO.class);

        verify(taskService).updateTask(captor.capture(), eq("task1"));

        TaskRequestDTO request = captor.getValue();

        assertFalse(request.isReminderMinutesBeforePresent());
        assertNull(request.getReminderMinutesBefore());
    }

    @Test
    void patchTask_ExplicitNullReminder_IsMarkedAsPresent() throws Exception {
        mockMvc.perform(patch("/tasks/task1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reminderMinutesBefore": null}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<TaskRequestDTO> captor =
                ArgumentCaptor.forClass(TaskRequestDTO.class);

        verify(taskService).updateTask(captor.capture(), eq("task1"));

        TaskRequestDTO request = captor.getValue();

        assertTrue(request.isReminderMinutesBeforePresent());
        assertNull(request.getReminderMinutesBefore());
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /tasks, -1",
            "POST, /tasks, 525601",
            "PUT, /tasks/task1, -1",
            "PUT, /tasks/task1, 525601",
            "PATCH, /tasks/task1, -1",
            "PATCH, /tasks/task1, 525601"
    })
    void taskEndpoints_RejectInvalidReminderOffsets(
            String method,
            String path,
            int reminderMinutesBefore) throws Exception {
        String payload = """
                {
                    "title": "Meeting",
                    "startsAt": "2026-11-10T18:00:00Z",
                    "reminderMinutesBefore": %d
                }
                """.formatted(reminderMinutesBefore);

        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.reminderMinutesBefore").exists());

        verifyNoInteractions(taskService);
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /tasks, 0, 201",
            "POST, /tasks, 525600, 201",
            "PUT, /tasks/task1, 0, 200",
            "PUT, /tasks/task1, 525600, 200",
            "PATCH, /tasks/task1, 0, 200",
            "PATCH, /tasks/task1, 525600, 200"
    })
    void taskEndpoints_AcceptReminderOffsetBoundaries(
            String method,
            String path,
            int reminderMinutesBefore,
            int expectedStatus) throws Exception {
        String payload = """
                {
                    "title": "Meeting",
                    "startsAt": "2026-11-10T18:00:00Z",
                    "reminderMinutesBefore": %d
                }
                """.formatted(reminderMinutesBefore);

        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is(expectedStatus));

        ArgumentCaptor<TaskRequestDTO> captor =
                ArgumentCaptor.forClass(TaskRequestDTO.class);

        switch (method) {
            case "POST" -> {
                verify(taskService).createTask(captor.capture());
            }
            case "PUT" -> {
                verify(taskService).replaceTask(captor.capture(), eq("task1"));
            }
            case "PATCH" -> {
                verify(taskService).updateTask(captor.capture(), eq("task1"));
            }
            default -> {
                throw new IllegalArgumentException("Unexpected method: " + method);
            }
        }

        TaskRequestDTO capturedRequest = captor.getValue();

        assertTrue(capturedRequest.isReminderMinutesBeforePresent());
        assertEquals(
                Integer.valueOf(reminderMinutesBefore),
                capturedRequest.getReminderMinutesBefore()
        );
    }
}
