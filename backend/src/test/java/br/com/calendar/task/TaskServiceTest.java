package br.com.calendar.task;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import br.com.calendar.category.Category;
import br.com.calendar.category.CategoryService;
import br.com.calendar.common.exception.ResourceNotFoundException;
import br.com.calendar.task.dto.TaskMonthResponseDTO;
import br.com.calendar.task.dto.TaskRequestDTO;
import br.com.calendar.task.dto.TaskResponseDTO;
import br.com.calendar.user.User;
import br.com.calendar.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository repository;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private CategoryService categoryService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private TaskService taskService;

    private static final String TASK_ID = "task123";
    private static final String USER_ID = "user123";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
    }

    private TaskRequestDTO validRequest() {
        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("Task");
        request.setAllDay(false);
        request.setStartsAt(Instant.now());
        request.setEndsAt(Instant.now().plus(1, ChronoUnit.HOURS));
        return request;
    }

    private User userWithId(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private void mockAuthenticatedUser(User user) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(user.getId());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    @Test
    void createTask_Success() {
        TaskRequestDTO request = validRequest();

        Task mappedTask = new Task();

        mockAuthenticatedUser(userWithId(USER_ID));
        when(taskMapper.toEntity(request, null)).thenReturn(mappedTask);
        when(repository.save(mappedTask)).thenReturn(mappedTask);
        when(taskMapper.toResponse(mappedTask)).thenReturn(TaskResponseDTO.builder().build());

        assertNotNull(taskService.createTask(request));

        assertEquals(USER_ID, mappedTask.getUser().getId());
        verify(repository).save(mappedTask);
    }

    @Test
    void createTask_MissingTitle_ThrowsIllegalArgumentException() {
        TaskRequestDTO request = validRequest();
        request.setTitle(null);

        assertThrows(IllegalArgumentException.class, () -> taskService.createTask(request));

        verify(repository, never()).save(any());
    }

    @Test
    void createTask_BlankTitle_ThrowsIllegalArgumentException() {
        TaskRequestDTO request = validRequest();
        request.setTitle("   ");

        assertThrows(IllegalArgumentException.class, () -> taskService.createTask(request));

        verify(repository, never()).save(any());
    }

    @Test
    void createTask_MissingStartsAt_ThrowsIllegalArgumentException() {
        TaskRequestDTO request = validRequest();
        request.setStartsAt(null);

        assertThrows(IllegalArgumentException.class, () -> taskService.createTask(request));

        verify(repository, never()).save(any());
    }

    @Test
    void createTask_InvalidDates_ThrowsIllegalArgumentException() {
        TaskRequestDTO request = validRequest();
        request.setStartsAt(Instant.now());
        request.setEndsAt(Instant.now().minus(1, ChronoUnit.HOURS));

        Task mappedTask = new Task();
        mappedTask.setAllDay(request.getAllDay());
        mappedTask.setStartsAt(request.getStartsAt());
        mappedTask.setEndsAt(request.getEndsAt());

        mockAuthenticatedUser(userWithId(USER_ID));
        when(taskMapper.toEntity(request, null)).thenReturn(mappedTask);

        assertThrows(IllegalArgumentException.class, () -> taskService.createTask(request));

        verify(repository, never()).save(any());
    }

    @Test
    void createTask_WithCategory_ValidatesOwnershipAndSetsCategory() {
        TaskRequestDTO request = validRequest();
        request.setCategoryId("cat123");

        Category category = new Category();
        category.setId("cat123");

        Task mappedTask = new Task();

        mockAuthenticatedUser(userWithId(USER_ID));
        when(categoryService.getCategoryOwnedByUser("cat123", USER_ID)).thenReturn(category);
        when(taskMapper.toEntity(request, category)).thenReturn(mappedTask);
        when(repository.save(mappedTask)).thenReturn(mappedTask);
        when(taskMapper.toResponse(mappedTask)).thenReturn(TaskResponseDTO.builder().build());

        assertNotNull(taskService.createTask(request));

        verify(categoryService).getCategoryOwnedByUser("cat123", USER_ID);
    }

    @Test
    void createTask_CategoryNotOwnedByUser_ThrowsAccessDenied() {
        TaskRequestDTO request = validRequest();
        request.setCategoryId("cat999");

        mockAuthenticatedUser(userWithId(USER_ID));
        when(categoryService.getCategoryOwnedByUser("cat999", USER_ID))
                .thenThrow(new AccessDeniedException("Category does not belong to the current user"));

        assertThrows(AccessDeniedException.class, () -> taskService.createTask(request));

        verify(repository, never()).save(any());
    }

    @Test
    void updateTask_Success() {
        TaskRequestDTO request = validRequest();

        Task existingTask = new Task();
        existingTask.setUser(userWithId(USER_ID));

        User currentUser = userWithId(USER_ID);

        when(repository.findById(TASK_ID)).thenReturn(Optional.of(existingTask));
        mockAuthenticatedUser(currentUser);
        when(repository.save(existingTask)).thenReturn(existingTask);
        when(taskMapper.toResponse(existingTask)).thenReturn(TaskResponseDTO.builder().build());

        assertNotNull(taskService.updateTask(request, TASK_ID));
        verify(taskMapper).updateEntity(existingTask, request);
    }

    @Test
    void updateTask_Unauthorized_WhenUserIsNotOwner() {
        TaskRequestDTO request = validRequest();

        Task existingTask = new Task();
        existingTask.setUser(userWithId(USER_ID));

        User currentUser = userWithId("user456");

        when(repository.findById(TASK_ID)).thenReturn(Optional.of(existingTask));
        mockAuthenticatedUser(currentUser);

        assertThrows(AccessDeniedException.class,
                () -> taskService.updateTask(request, TASK_ID));

        verify(repository, never()).save(any());
    }

    @Test
    void updateTask_TaskNotFound_ThrowsResourceNotFoundException() {
        TaskRequestDTO request = validRequest();

        when(repository.findById(TASK_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> taskService.updateTask(request, TASK_ID));

        verify(repository, never()).save(any());
    }

    @Test
    void updateTask_PartialUpdate_KeepsExistingDatesValid() {
        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("Novo título");

        Task existingTask = new Task();
        existingTask.setUser(userWithId(USER_ID));
        existingTask.setAllDay(false);
        existingTask.setStartsAt(Instant.now());
        existingTask.setEndsAt(Instant.now().plus(1, ChronoUnit.HOURS));

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(existingTask));
        when(repository.save(existingTask)).thenReturn(existingTask);
        when(taskMapper.toResponse(existingTask)).thenReturn(TaskResponseDTO.builder().build());

        doAnswer(invocation -> {
            Task t = invocation.getArgument(0);
            t.setTitle("Novo título"); // simula o merge real do mapper
            return null;
        }).when(taskMapper).updateEntity(eq(existingTask), eq(request));

        assertNotNull(taskService.updateTask(request, TASK_ID));
    }

    @Test
    void updateTask_PartialUpdate_InvalidatesExistingDates() {
        // PATCH que muda só o endsAt, tornando o intervalo inválido
        TaskRequestDTO request = new TaskRequestDTO();
        request.setEndsAt(Instant.now().minus(1, ChronoUnit.DAYS));

        Task existingTask = new Task();
        existingTask.setUser(userWithId(USER_ID));
        existingTask.setAllDay(false);
        existingTask.setStartsAt(Instant.now());
        existingTask.setEndsAt(Instant.now().plus(1, ChronoUnit.HOURS));

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(existingTask));

        doAnswer(invocation -> {
            Task t = invocation.getArgument(0);
            t.setEndsAt(request.getEndsAt()); // simula o merge real
            return null;
        }).when(taskMapper).updateEntity(eq(existingTask), eq(request));

        assertThrows(IllegalArgumentException.class,
                () -> taskService.updateTask(request, TASK_ID));

        verify(repository, never()).save(any());
    }

    @Test
    void updateTask_AllDayNullOnEntity_TreatedAsNotAllDay() {
        TaskRequestDTO request = validRequest(); // allDay=false, startsAt antes de endsAt

        Task existingTask = new Task();
        existingTask.setUser(userWithId(USER_ID));
        existingTask.setAllDay(null); // estado inconsistente propositalmente

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(existingTask));

        doAnswer(invocation -> {
            Task t = invocation.getArgument(0);
            t.setAllDay(request.getAllDay());
            t.setStartsAt(request.getStartsAt());
            t.setEndsAt(request.getEndsAt());
            return null;
        }).when(taskMapper).updateEntity(eq(existingTask), eq(request));

        when(repository.save(existingTask)).thenReturn(existingTask);
        when(taskMapper.toResponse(existingTask)).thenReturn(TaskResponseDTO.builder().build());

        assertDoesNotThrow(() -> taskService.updateTask(request, TASK_ID));
    }

    @Test
    void updateTask_AllDayTrue_SkipsDateValidation() {
        TaskRequestDTO request = validRequest();
        request.setAllDay(true);
        request.setStartsAt(Instant.now());
        request.setEndsAt(Instant.now().minus(1, ChronoUnit.HOURS)); // seria inválido se allDay=false

        Task existingTask = new Task();
        existingTask.setUser(userWithId(USER_ID));

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(existingTask));
        when(repository.save(existingTask)).thenReturn(existingTask);
        when(taskMapper.toResponse(existingTask)).thenReturn(TaskResponseDTO.builder().build());

        assertNotNull(taskService.updateTask(request, TASK_ID));
    }

    @Test
    void updateTask_WithCategory_ValidatesOwnershipAndSetsCategory() {
        TaskRequestDTO request = validRequest();
        request.setCategoryId("cat123");

        Task existingTask = new Task();
        existingTask.setUser(userWithId(USER_ID));

        Category category = new Category();
        category.setId("cat123");

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(existingTask));
        when(categoryService.getCategoryOwnedByUser("cat123", USER_ID)).thenReturn(category);
        when(repository.save(existingTask)).thenReturn(existingTask);
        when(taskMapper.toResponse(existingTask)).thenReturn(TaskResponseDTO.builder().build());

        assertNotNull(taskService.updateTask(request, TASK_ID));

        assertEquals(category, existingTask.getCategory());
        verify(categoryService).getCategoryOwnedByUser("cat123", USER_ID);
    }

    @Test
    void updateTask_CategoryNotOwnedByUser_ThrowsAccessDenied() {
        TaskRequestDTO request = validRequest();
        request.setCategoryId("cat999");

        Task existingTask = new Task();
        existingTask.setUser(userWithId(USER_ID));

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(existingTask));
        when(categoryService.getCategoryOwnedByUser("cat999", USER_ID))
                .thenThrow(new AccessDeniedException("Category does not belong to the current user"));

        assertThrows(AccessDeniedException.class,
                () -> taskService.updateTask(request, TASK_ID));

        verify(repository, never()).save(any());
    }

    @Test
    void replaceTask_OmittedFields_ClearsThemOnTheTask() {
        // PUT sem title/description: full replacement deve limpar os campos, não preservá-los
        // (title is only required on create, PUT/PATCH may omit/clear it)
        TaskRequestDTO request = validRequest();
        request.setTitle(null);

        Task existingTask = new Task();
        existingTask.setUser(userWithId(USER_ID));
        existingTask.setTitle("Título antigo");
        existingTask.setDescription("Descrição antiga");

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(existingTask));
        when(repository.save(existingTask)).thenReturn(existingTask);
        when(taskMapper.toResponse(existingTask)).thenReturn(TaskResponseDTO.builder().build());

        doAnswer(invocation -> {
            Task t = invocation.getArgument(0);
            TaskRequestDTO r = invocation.getArgument(1);
            t.setTitle(r.getTitle());
            t.setDescription(r.getDescription());
            return null;
        }).when(taskMapper).replaceEntity(eq(existingTask), eq(request));

        assertNotNull(taskService.replaceTask(request, TASK_ID));

        verify(taskMapper).replaceEntity(existingTask, request);
        verify(taskMapper, never()).updateEntity(any(), any());
        assertEquals(null, existingTask.getTitle());
        assertEquals(null, existingTask.getDescription());
    }

    @Test
    void replaceTask_OmittedCategoryId_ClearsCategory() {
        TaskRequestDTO request = validRequest();

        Task existingTask = new Task();
        existingTask.setUser(userWithId(USER_ID));
        existingTask.setCategory(new Category());

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(existingTask));
        when(repository.save(existingTask)).thenReturn(existingTask);
        when(taskMapper.toResponse(existingTask)).thenReturn(TaskResponseDTO.builder().build());

        assertNotNull(taskService.replaceTask(request, TASK_ID));

        assertEquals(null, existingTask.getCategory());
        verifyNoInteractions(categoryService);
    }

    @Test
    void replaceTask_WithCategory_ValidatesOwnershipAndSetsCategory() {
        TaskRequestDTO request = validRequest();
        request.setCategoryId("cat123");

        Task existingTask = new Task();
        existingTask.setUser(userWithId(USER_ID));

        Category category = new Category();
        category.setId("cat123");

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(existingTask));
        when(categoryService.getCategoryOwnedByUser("cat123", USER_ID)).thenReturn(category);
        when(repository.save(existingTask)).thenReturn(existingTask);
        when(taskMapper.toResponse(existingTask)).thenReturn(TaskResponseDTO.builder().build());

        assertNotNull(taskService.replaceTask(request, TASK_ID));

        assertEquals(category, existingTask.getCategory());
        verify(categoryService).getCategoryOwnedByUser("cat123", USER_ID);
    }

    @Test
    void replaceTask_Unauthorized_WhenUserIsNotOwner() {
        TaskRequestDTO request = validRequest();

        Task existingTask = new Task();
        existingTask.setUser(userWithId(USER_ID));

        mockAuthenticatedUser(userWithId("user456"));
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(existingTask));

        assertThrows(AccessDeniedException.class,
                () -> taskService.replaceTask(request, TASK_ID));

        verify(repository, never()).save(any());
    }

    @Test
    void replaceTask_InvalidDates_ThrowsIllegalArgumentException() {
        TaskRequestDTO request = validRequest();
        request.setStartsAt(Instant.now());
        request.setEndsAt(Instant.now().minus(1, ChronoUnit.HOURS));

        Task existingTask = new Task();
        existingTask.setUser(userWithId(USER_ID));

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(existingTask));

        doAnswer(invocation -> {
            Task t = invocation.getArgument(0);
            TaskRequestDTO r = invocation.getArgument(1);
            t.setAllDay(r.getAllDay());
            t.setStartsAt(r.getStartsAt());
            t.setEndsAt(r.getEndsAt());
            return null;
        }).when(taskMapper).replaceEntity(eq(existingTask), eq(request));

        assertThrows(IllegalArgumentException.class,
                () -> taskService.replaceTask(request, TASK_ID));

        verify(repository, never()).save(any());
    }

    @Test
    void getTasksForDay_ScopesByCurrentUser() {
        mockAuthenticatedUser(userWithId(USER_ID));

        Task task = new Task();
        task.setUser(userWithId(USER_ID));
        when(repository.findActiveTasksForDay(eq(USER_ID), any(), any())).thenReturn(List.of(task));
        when(taskMapper.toResponse(task)).thenReturn(TaskResponseDTO.builder().build());

        List<TaskResponseDTO> result = taskService.getTasksForDay(LocalDate.of(2026, 8, 27));

        assertEquals(1, result.size());
        verify(repository).findActiveTasksForDay(eq(USER_ID), any(), any());
    }

    @Test
    void getUpcomingTasks_UsesCurrentUserNowAndRequestedLimit() {
        mockAuthenticatedUser(userWithId(USER_ID));
        Task task = new Task();
        TaskResponseDTO response = TaskResponseDTO.builder().id(TASK_ID).build();
        when(repository.findUpcomingTasks(eq(USER_ID), any(), any())).thenReturn(List.of(task));
        when(taskMapper.toResponse(task)).thenReturn(response);

        Instant before = Instant.now();
        List<TaskResponseDTO> result = taskService.getUpcomingTasks(7);
        Instant after = Instant.now();

        assertEquals(List.of(response), result);
        ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findUpcomingTasks(eq(USER_ID), nowCaptor.capture(), pageCaptor.capture());
        assertTrue(!nowCaptor.getValue().isBefore(before) && !nowCaptor.getValue().isAfter(after));
        assertEquals(0, pageCaptor.getValue().getPageNumber());
        assertEquals(7, pageCaptor.getValue().getPageSize());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void getUpcomingTasks_InvalidLimit_ThrowsIllegalArgumentException(int limit) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> taskService.getUpcomingTasks(limit));

        assertEquals("Limit must be greater than zero.", exception.getMessage());
        verifyNoInteractions(repository, userRepository, taskMapper);
    }
    
    @Test
    void deleteTask_Success_WhenOwner() {
        Task existingTask = new Task();
        existingTask.setUser(userWithId(USER_ID));

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findByIdAndDeletedAtIsNull(TASK_ID)).thenReturn(Optional.of(existingTask));

        taskService.deleteTask(TASK_ID);

        verify(repository).delete(existingTask);
    }

    @Test
    void deleteTask_Unauthorized_WhenUserIsNotOwner() {
        Task existingTask = new Task();
        existingTask.setUser(userWithId(USER_ID));

        mockAuthenticatedUser(userWithId("user456"));
        when(repository.findByIdAndDeletedAtIsNull(TASK_ID)).thenReturn(Optional.of(existingTask));

        assertThrows(AccessDeniedException.class, () -> taskService.deleteTask(TASK_ID));

        verify(repository, never()).delete(any());
    }

    @Test
    void deleteTask_NoOp_WhenTaskNotFound() {
        when(repository.findByIdAndDeletedAtIsNull(TASK_ID)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> taskService.deleteTask(TASK_ID));

        verify(repository, never()).delete(any());
        verifyNoInteractions(userRepository);
    }

    private void stubMonthResponsePassthrough() {
        // Real toMonthResponse is covered by TaskMapperTest; here we just need
        // the occurrence dates/id passed in to be visible on the returned DTO.
        when(taskMapper.toMonthResponse(any(), any(), any(), any())).thenAnswer(invocation -> {
            String occurrenceId = invocation.getArgument(1);
            Instant occursAt = invocation.getArgument(2);
            Instant occursUntil = invocation.getArgument(3);
            return TaskMonthResponseDTO.builder().id(occurrenceId).startsAt(occursAt).endsAt(occursUntil).build();
        });
    }

    @Test
    void getTasksForMonth_InvalidMonth_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> taskService.getTasksForMonth(0, 2026));
        assertThrows(IllegalArgumentException.class, () -> taskService.getTasksForMonth(13, 2026));

        verifyNoInteractions(repository);
    }

    @Test
    void getTasksForMonth_ScopesByCurrentUserAndComputesMonthRange() {
        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findActiveTasksForMonth(eq(USER_ID), any(), any())).thenReturn(List.of());

        taskService.getTasksForMonth(8, 2026);

        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findActiveTasksForMonth(eq(USER_ID), startCaptor.capture(), endCaptor.capture());

        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), startCaptor.getValue());
        assertEquals(Instant.parse("2026-08-31T23:59:59.999999999Z"), endCaptor.getValue());
    }

    @Test
    void getTasksForMonth_NonRecurringTask_ReturnsSingleOccurrence() {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setUser(userWithId(USER_ID));
        task.setStartsAt(Instant.parse("2026-08-10T10:00:00Z"));
        task.setEndsAt(Instant.parse("2026-08-10T11:00:00Z"));

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findActiveTasksForMonth(eq(USER_ID), any(), any())).thenReturn(List.of(task));
        stubMonthResponsePassthrough();

        List<TaskMonthResponseDTO> result = taskService.getTasksForMonth(8, 2026);

        assertEquals(1, result.size());
        assertEquals(TASK_ID, result.get(0).getId());
        assertEquals(task.getStartsAt(), result.get(0).getStartsAt());
        verify(taskMapper).toMonthResponse(task, TASK_ID, task.getStartsAt(), task.getEndsAt());
    }

    @Test
    void getTasksForMonth_DailyRecurrence_ExpandsOccurrencesWithinMonth() {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setUser(userWithId(USER_ID));
        task.setStartsAt(Instant.parse("2026-08-29T10:00:00Z"));
        task.setEndsAt(Instant.parse("2026-08-29T11:00:00Z"));
        task.setRepeatInterval("daily");

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findActiveTasksForMonth(eq(USER_ID), any(), any())).thenReturn(List.of(task));
        stubMonthResponsePassthrough();

        List<TaskMonthResponseDTO> result = taskService.getTasksForMonth(8, 2026);

        // Aug 29, 30, 31 fall in August; Sep 1 does not.
        assertEquals(3, result.size());
        assertEquals(Instant.parse("2026-08-29T10:00:00Z"), result.get(0).getStartsAt());
        assertEquals(Instant.parse("2026-08-30T10:00:00Z"), result.get(1).getStartsAt());
        assertEquals(Instant.parse("2026-08-31T10:00:00Z"), result.get(2).getStartsAt());
    }

    @Test
    void getTasksForMonth_WeeklyRecurrenceWithStride_SkipsAccordingToStride() {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setUser(userWithId(USER_ID));
        task.setStartsAt(Instant.parse("2026-08-01T10:00:00Z"));
        task.setRepeatInterval("weekly");
        task.setRepeat(2);

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findActiveTasksForMonth(eq(USER_ID), any(), any())).thenReturn(List.of(task));
        stubMonthResponsePassthrough();

        List<TaskMonthResponseDTO> result = taskService.getTasksForMonth(8, 2026);

        // Every 2 weeks from Aug 1: Aug 1, Aug 15, Aug 29 fall in August.
        assertEquals(3, result.size());
        assertEquals(Instant.parse("2026-08-01T10:00:00Z"), result.get(0).getStartsAt());
        assertEquals(Instant.parse("2026-08-15T10:00:00Z"), result.get(1).getStartsAt());
        assertEquals(Instant.parse("2026-08-29T10:00:00Z"), result.get(2).getStartsAt());
    }

    @Test
    void getTasksForMonth_MonthlyRecurrence_ReachesRequestedMonthFromThePast() {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setUser(userWithId(USER_ID));
        task.setStartsAt(Instant.parse("2026-01-15T10:00:00Z"));
        task.setRepeatInterval("monthly");

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findActiveTasksForMonth(eq(USER_ID), any(), any())).thenReturn(List.of(task));
        stubMonthResponsePassthrough();

        List<TaskMonthResponseDTO> result = taskService.getTasksForMonth(8, 2026);

        assertEquals(1, result.size());
        assertEquals(Instant.parse("2026-08-15T10:00:00Z"), result.get(0).getStartsAt());
    }
    
    @Test
    void getTasksForMonth_UnrecognizedRepeatInterval_OnlyIncludesBaseOccurrence() {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setUser(userWithId(USER_ID));
        task.setStartsAt(Instant.parse("2026-08-10T10:00:00Z"));
        task.setRepeatInterval("fortnightly"); // not a recognized value

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findActiveTasksForMonth(eq(USER_ID), any(), any())).thenReturn(List.of(task));
        stubMonthResponsePassthrough();

        List<TaskMonthResponseDTO> result = taskService.getTasksForMonth(8, 2026);

        assertEquals(1, result.size());
        assertEquals(Instant.parse("2026-08-10T10:00:00Z"), result.get(0).getStartsAt());
    }

    @Test
    void getTasksForMonth_OldDailyRecurrence_StillReachesRequestedMonth() {
        // Created ~11 years before the requested month: walking one day at a
        // time from startsAt would hit MAX_OCCURRENCES_PER_TASK (1000) long
        // before reaching August 2026, and silently return nothing.
        Task task = new Task();
        task.setId(TASK_ID);
        task.setUser(userWithId(USER_ID));
        task.setStartsAt(Instant.parse("2015-01-01T10:00:00Z"));
        task.setRepeatInterval("daily");

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findActiveTasksForMonth(eq(USER_ID), any(), any())).thenReturn(List.of(task));
        stubMonthResponsePassthrough();

        List<TaskMonthResponseDTO> result = taskService.getTasksForMonth(8, 2026);

        assertEquals(31, result.size());
        assertEquals(Instant.parse("2026-08-01T10:00:00Z"), result.get(0).getStartsAt());
        assertEquals(Instant.parse("2026-08-31T10:00:00Z"), result.get(30).getStartsAt());
    }

    @Test
    void getTasksForMonth_RecurringOccurrenceFullySpanningMonth_IsIncluded() {
        // First occurrence starts before the requested month and ends after
        // it — neither its start nor its end falls inside the month, but it
        // still spans (and should cover) the whole thing.
        Task task = new Task();
        task.setId(TASK_ID);
        task.setUser(userWithId(USER_ID));
        task.setStartsAt(Instant.parse("2026-07-25T10:00:00Z"));
        task.setEndsAt(Instant.parse("2026-09-05T10:00:00Z"));
        task.setRepeatInterval("yearly");

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findActiveTasksForMonth(eq(USER_ID), any(), any())).thenReturn(List.of(task));
        stubMonthResponsePassthrough();

        List<TaskMonthResponseDTO> result = taskService.getTasksForMonth(8, 2026);

        assertEquals(1, result.size());
        assertEquals(Instant.parse("2026-07-25T10:00:00Z"), result.get(0).getStartsAt());
        assertEquals(Instant.parse("2026-09-05T10:00:00Z"), result.get(0).getEndsAt());
    }

    @Test
    void getTasksForMonth_SortsOccurrencesByStartsAt() {
        Task earlyTask = new Task();
        earlyTask.setId("early");
        earlyTask.setUser(userWithId(USER_ID));
        earlyTask.setStartsAt(Instant.parse("2026-08-20T10:00:00Z"));

        Task lateTask = new Task();
        lateTask.setId("late");
        lateTask.setUser(userWithId(USER_ID));
        lateTask.setStartsAt(Instant.parse("2026-08-05T10:00:00Z"));

        mockAuthenticatedUser(userWithId(USER_ID));
        when(repository.findActiveTasksForMonth(eq(USER_ID), any(), any())).thenReturn(List.of(earlyTask, lateTask));
        stubMonthResponsePassthrough();

        List<TaskMonthResponseDTO> result = taskService.getTasksForMonth(8, 2026);

        assertEquals(2, result.size());
        assertEquals("late", result.get(0).getId());
        assertEquals("early", result.get(1).getId());
    }
}
