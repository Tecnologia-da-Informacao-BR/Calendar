package br.com.calendar.task;

import br.com.calendar.common.exception.CategoryNotFoundException;
import br.com.calendar.common.exception.GlobalExceptionHandler;
import br.com.calendar.common.exception.InvalidTaskTimeRangeException;
import br.com.calendar.common.exception.UserNotFoundException;
import br.com.calendar.category.Category;
import br.com.calendar.category.CategoryRepository;
import br.com.calendar.task.dto.request.CreateTaskRequest;
import br.com.calendar.task.dto.response.CreateTaskResponse;
import br.com.calendar.task.entity.Task;
import br.com.calendar.task.entity.TaskPriority;
import br.com.calendar.user.User;
import br.com.calendar.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final String USER_ID = "usr_abc123";
    private static final String CATEGORY_ID = "cat_xyz789";

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, userRepository, categoryRepository);
    }

    @Test
    void createWithValidDataReturnsResponse() {
        User user = new User();
        user.setId(USER_ID);

        Instant now = Instant.now();
        CreateTaskRequest request = new CreateTaskRequest(
                "Meeting", "Team sync", "America/Sao_Paulo", "Room 1",
                "pending", now, now.plusSeconds(3600),
                null, 1, "daily", false, TaskPriority.MEDIUM);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId("task_abc123");
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            return task;
        });

        CreateTaskResponse response = taskService.create(request, USER_ID);

        assertNotNull(response);
        assertEquals("task_abc123", response.id());
        assertEquals("Meeting", response.title());
        assertEquals(USER_ID, response.userId());
        assertNull(response.categoryId());
        assertEquals(TaskPriority.MEDIUM, response.priority());
        verify(categoryRepository, never()).findById(any());
    }

    @Test
    void createWithCategoryLooksUpCategory() {
        User user = new User();
        user.setId(USER_ID);

        Category category = new Category();
        category.setId(CATEGORY_ID);

        Instant now = Instant.now();
        CreateTaskRequest request = new CreateTaskRequest(
                "Meeting", "Team sync", "America/Sao_Paulo", "Room 1",
                "pending", now, now.plusSeconds(3600),
                CATEGORY_ID, 1, "daily", false, TaskPriority.HIGH);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId("task_abc123");
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            return task;
        });

        CreateTaskResponse response = taskService.create(request, USER_ID);

        assertNotNull(response);
        assertEquals(CATEGORY_ID, response.categoryId());
        assertEquals(TaskPriority.HIGH, response.priority());
        verify(categoryRepository).findById(CATEGORY_ID);
    }

    @Test
    void createThrowsWhenEndsAtBeforeStartsAtAndNotAllDay() {
        Instant now = Instant.now();
        CreateTaskRequest request = new CreateTaskRequest(
                "Meeting", "Team sync", "America/Sao_Paulo", "Room 1",
                "pending", now, now.minusSeconds(3600),
                null, null, null, false, TaskPriority.LOW);

        assertThrows(InvalidTaskTimeRangeException.class,
                () -> taskService.create(request, USER_ID));
    }

    @Test
    void createAllowsEndsAtBeforeStartsAtWhenAllDay() {
        User user = new User();
        user.setId(USER_ID);

        Instant now = Instant.now();
        CreateTaskRequest request = new CreateTaskRequest(
                "Holiday", "Day off", "America/Sao_Paulo", null,
                "pending", now, now.minusSeconds(3600),
                null, null, null, true, TaskPriority.LOW);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId("task_abc123");
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            return task;
        });

        CreateTaskResponse response = taskService.create(request, USER_ID);

        assertNotNull(response);
        assertEquals("Holiday", response.title());
    }

    @Test
    void createThrowsWhenUserNotFound() {
        when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

        Instant now = Instant.now();
        CreateTaskRequest request = new CreateTaskRequest(
                "Meeting", "Team sync", "America/Sao_Paulo", "Room 1",
                "pending", now, now.plusSeconds(3600),
                null, null, null, false, TaskPriority.MEDIUM);

        UserNotFoundException ex = assertThrows(UserNotFoundException.class,
                () -> taskService.create(request, "nonexistent"));
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void createThrowsWhenCategoryNotFound() {
        User user = new User();
        user.setId(USER_ID);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(categoryRepository.findById("nonexistent")).thenReturn(Optional.empty());

        Instant now = Instant.now();
        CreateTaskRequest request = new CreateTaskRequest(
                "Meeting", "Team sync", "America/Sao_Paulo", "Room 1",
                "pending", now, now.plusSeconds(3600),
                "nonexistent", null, null, false, TaskPriority.MEDIUM);

        CategoryNotFoundException ex = assertThrows(CategoryNotFoundException.class,
                () -> taskService.create(request, USER_ID));
        assertEquals("Category not found", ex.getMessage());
    }
}
