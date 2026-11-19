package br.com.calendar.task;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

import br.com.calendar.reminder.ReminderScheduleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.calendar.category.Category;
import br.com.calendar.category.CategoryService;
import br.com.calendar.common.exception.ResourceNotFoundException;
import br.com.calendar.task.dto.TaskMonthResponseDTO;
import br.com.calendar.task.dto.TaskRequestDTO;
import br.com.calendar.task.dto.TaskResponseDTO;
import br.com.calendar.user.User;
import br.com.calendar.user.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository repository;
    private final TaskMapper taskMapper;
    private final CategoryService categoryService;
    private final UserRepository userRepository;
    private final ReminderScheduleService reminderScheduleService;

    /** Largest page the upcoming tasks endpoint accepts (the default is 20). */
    public static final int MAX_UPCOMING_LIMIT = 100;

    /** Start time first, then id, so ties always come out in the same order. */
    private static final Comparator<TaskResponseDTO> CHRONOLOGICAL = Comparator
            .comparing(TaskResponseDTO::getStartsAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TaskResponseDTO::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    @Transactional
    public TaskResponseDTO createTask(TaskRequestDTO request) {
        validateRequiredFields(request);

        User currentUser = currentUser();

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryService.getCategoryOwnedByUser(request.getCategoryId(), currentUser.getId());
        }

        Task task = taskMapper.toEntity(request, category);
        task.setUser(currentUser);

        validateTaskDates(task);

        Task savedTask = repository.save(task);
        reminderScheduleService.scheduleForNewTask(savedTask);
        return taskMapper.toResponse(savedTask);
    }

    /**
     * Tasks that start on the given day (UTC). Recurring tasks are expanded
     * with {@link TaskOccurrences}, the same algorithm the month view and the
     * reminder scheduler use, and each occurrence starting on the day becomes
     * its own entry. Results are in chronological order.
     */
    @Transactional(readOnly = true)
    public List<TaskResponseDTO> getTasksForDay(LocalDate date) {
        String userId = currentUser().getId();
        var startOfDay = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        var endOfDay = date.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
        List<TaskResponseDTO> result = new ArrayList<>();

        for (Task task : repository.findActiveTasksForDay(userId, startOfDay, endOfDay)) {
            if (task.getRepeatInterval() == null) {
                result.add(taskMapper.toResponse(task));
                continue;
            }
            for (TaskOccurrences.Occurrence occurrence : TaskOccurrences.between(task, startOfDay, endOfDay)) {
                // between() also returns an occurrence that started the day
                // before and is still running; like non-recurring tasks, the
                // daily view only lists what starts on the day.
                if (!occurrence.startsAt().isBefore(startOfDay)) {
                    result.add(taskMapper.toOccurrenceResponse(task, occurrence));
                }
            }
        }

        result.sort(CHRONOLOGICAL);
        return result;
    }

    @Transactional(readOnly = true)
    public List<TaskResponseDTO> searchTasks(String keyword) {
        String trimmedKeyword = keyword == null ? "" : keyword.trim();
        if (trimmedKeyword.isBlank()) {
            throw new IllegalArgumentException("Keyword is required.");
        }

        String userId = currentUser().getId();
        return repository.searchActiveTasksByTitle(userId, trimmedKeyword).stream()
                .map(taskMapper::toResponse)
                .toList();
    }

    /**
     * Returns the next incomplete, non-deleted tasks starting at or after
     * Instant.now(), comparing absolute instants (UTC); Task.timezone does not
     * shift this comparison. Recurring tasks contribute their upcoming
     * occurrences, merged with the non-recurring tasks in chronological order.
     *
     * <p>Occurrences are generated on demand: every recurring task starts with
     * only its next occurrence in a priority queue, and a task's following
     * occurrence is computed only after the previous one made it into the
     * result. So the work is about {@code limit + number of recurring tasks}
     * occurrences, instead of {@code limit} occurrences per recurring task.
     *
     * <p>To keep memory low, the queue works with the schedule columns of each
     * recurring task only; the complete tasks are loaded afterwards, in one
     * query, just for the occurrences that were selected.
     */
    @Transactional(readOnly = true)
    public List<TaskResponseDTO> getUpcomingTasks(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be greater than zero.");
        }
        if (limit > MAX_UPCOMING_LIMIT) {
            throw new IllegalArgumentException("Limit must not exceed " + MAX_UPCOMING_LIMIT + ".");
        }

        String userId = currentUser().getId();
        Instant now = Instant.now();
        PriorityQueue<UpcomingCandidate> queue = new PriorityQueue<>(UpcomingCandidate.ORDER);

        for (Task task : repository.findUpcomingTasks(userId, now, PageRequest.of(0, limit))) {
            queue.add(UpcomingCandidate.single(task));
        }
        // Only the schedule columns: enough to order the occurrences.
        for (RecurringTaskSchedule schedule : repository.findActiveRecurringSchedules(userId)) {
            nextOccurrence(schedule.toScheduleOnlyTask(), now).ifPresent(queue::add);
        }

        List<UpcomingCandidate> selected = new ArrayList<>(limit);
        while (selected.size() < limit && !queue.isEmpty()) {
            UpcomingCandidate earliest = queue.poll();
            selected.add(earliest);
            if (earliest.occurrence() != null) {
                // Only now is the task's following occurrence needed.
                Instant afterThisOne = earliest.occurrence().startsAt().plusNanos(1);
                nextOccurrence(earliest.task(), afterThisOne).ifPresent(queue::add);
            }
        }

        Map<String, Task> fullRecurringTasks = loadFullTasks(selected);
        List<TaskResponseDTO> result = new ArrayList<>(selected.size());
        for (UpcomingCandidate candidate : selected) {
            if (candidate.occurrence() == null) {
                result.add(taskMapper.toResponse(candidate.task()));
                continue;
            }
            Task fullTask = fullRecurringTasks.get(candidate.task().getId());
            // Null only if the row vanished between the two queries (the
            // user account was removed); there is nothing left to show.
            if (fullTask != null) {
                result.add(taskMapper.toOccurrenceResponse(fullTask, candidate.occurrence()));
            }
        }
        return result;
    }

    /**
     * Loads, in a single query, the complete recurring tasks behind the
     * selected occurrences (at most {@code limit} tasks, usually fewer).
     */
    private Map<String, Task> loadFullTasks(List<UpcomingCandidate> selected) {
        Set<String> ids = new HashSet<>();
        for (UpcomingCandidate candidate : selected) {
            if (candidate.occurrence() != null) {
                ids.add(candidate.task().getId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Task> byId = new HashMap<>();
        for (Task task : repository.findAllById(ids)) {
            byId.put(task.getId(), task);
        }
        return byId;
    }

    private static Optional<UpcomingCandidate> nextOccurrence(Task task, Instant from) {
        return TaskOccurrences.upcoming(task, from, 1).stream()
                .findFirst()
                .map(occurrence -> UpcomingCandidate.recurring(task, occurrence));
    }

    /**
     * An entry of the upcoming merge: a non-recurring task (occurrence null)
     * or one occurrence of a recurring task. Ordered like {@link #CHRONOLOGICAL}.
     */
    private record UpcomingCandidate(Task task, TaskOccurrences.Occurrence occurrence,
                                     Instant startsAt, String id) {

        static final Comparator<UpcomingCandidate> ORDER = Comparator
                .comparing(UpcomingCandidate::startsAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(UpcomingCandidate::id, Comparator.nullsLast(Comparator.naturalOrder()));

        static UpcomingCandidate single(Task task) {
            return new UpcomingCandidate(task, null, task.getStartsAt(), task.getId());
        }

        static UpcomingCandidate recurring(Task task, TaskOccurrences.Occurrence occurrence) {
            return new UpcomingCandidate(task, occurrence, occurrence.startsAt(), occurrence.id());
        }
    }

    /**
     * Tasks whose starts_at or ends_at falls within the given month, scoped
     * to the current user, excluding soft-deleted tasks — recurring tasks
     * (repeatInterval set) are expanded into their occurrences within the
     * month rather than returned as a single row (see {@link TaskOccurrences},
     * shared with the reminder scheduler).
     */
    public List<TaskMonthResponseDTO> getTasksForMonth(int month, int year) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12.");
        }

        String userId = currentUser().getId();
        YearMonth yearMonth = YearMonth.of(year, month);
        Instant monthStart = yearMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant monthEnd = yearMonth.atEndOfMonth().atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);

        List<TaskMonthResponseDTO> result = new ArrayList<>();
        for (Task task : repository.findActiveTasksForMonth(userId, monthStart, monthEnd)) {
            if (task.getRepeatInterval() == null) {
                result.add(taskMapper.toMonthResponse(task, task.getId(), task.getStartsAt(), task.getEndsAt()));
                continue;
            }

            for (TaskOccurrences.Occurrence occurrence : TaskOccurrences.between(task, monthStart, monthEnd)) {
                result.add(taskMapper.toMonthResponse(task, occurrence.id(), occurrence.startsAt(), occurrence.endsAt()));
            }
        }

        return result.stream()
                .sorted((a, b) -> a.getStartsAt().compareTo(b.getStartsAt()))
                .toList();
    }

    @Transactional
    public void deleteTask(String id) {
        repository.findByIdAndDeletedAtIsNull(id).ifPresent(task -> {
            checkOwnership(task);
            reminderScheduleService.cancelRemindersForTask(task.getId());
            repository.delete(task);
        });
    }
    /**
    * Returns the task history for the currently authenticated user,
    * mapped to TaskResponseDTO. Delegates the actual filtering/ordering
    * logic to the repository query (findTaskHistory).
    */
    public Page<TaskResponseDTO> getTaskHistory(Pageable pageable) {
        String userId = currentUser().getId();
         // "now" is computed here instead of using the database's
        // CURRENT_TIMESTAMP so the reference instant is explicit and
        // testable (can be swapped for a fixed Clock later if needed).
        Instant now = Instant.now();
        return repository.findTaskHistory(userId, now, pageable)
            .map(taskMapper::toResponse);
}
    /**
     * PATCH semantics: fields omitted from the request are left unchanged.
     */
    @Transactional
    public TaskResponseDTO updateTask(TaskRequestDTO request, String taskId) {
        Task existingTask = findOwnedTask(taskId);
        Instant previousStartsAt = existingTask.getStartsAt();
        User currentUser = currentUser();

        if (request.getCategoryId() != null) {
            existingTask.setCategory(categoryService.getCategoryOwnedByUser(request.getCategoryId(), currentUser.getId()));
        }

        taskMapper.updateEntity(existingTask, request);

        validateTaskDates(existingTask);

        Task savedTask = repository.save(existingTask);
        reminderScheduleService.synchronizeAfterTaskUpdate(savedTask, previousStartsAt);
        return taskMapper.toResponse(savedTask);
    }

    /**
     * PUT semantics: full replacement. Fields omitted from the request clear
     * the corresponding value on the task, including the category.
     */
    @Transactional
    public TaskResponseDTO replaceTask(TaskRequestDTO request, String taskId) {
        Task existingTask = findOwnedTask(taskId);
        Instant previousStartsAt = existingTask.getStartsAt();
        User currentUser = currentUser();

        Category category = request.getCategoryId() != null
                ? categoryService.getCategoryOwnedByUser(request.getCategoryId(), currentUser.getId())
                : null;
        existingTask.setCategory(category);

        taskMapper.replaceEntity(existingTask, request);

        validateTaskDates(existingTask);

        Task savedTask = repository.save(existingTask);
        reminderScheduleService.synchronizeAfterTaskUpdate(savedTask, previousStartsAt);
        return taskMapper.toResponse(savedTask);
    }

    private Task findOwnedTask(String taskId) {
        Task task = repository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        checkOwnership(task);

        return task;
    }

    private void checkOwnership(Task task) {
        User currentUser = currentUser();
        if (!Objects.equals(currentUser.getId(), task.getUser().getId())) {
            throw new AccessDeniedException("Task does not belong to the current user");
        }
    }

    /**
     * The JwtFilter authenticates with a Spring Security UserDetails, not our
     * domain User — the authenticated user's id is its name/username, not
     * its principal, so it must be looked up.
     */
    private User currentUser() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /**
     * Only enforced on create — title/startsAt are required to create a
     * task, but PATCH/PUT reuse the same TaskRequestDTO and must still allow
     * omitting them (partial update / clearing a field).
     */
    private void validateRequiredFields(TaskRequestDTO request) {
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("Title is required.");
        }
        if (request.getStartsAt() == null) {
            throw new IllegalArgumentException("Start time is required.");
        }
    }

    private void validateTaskDates(Task task) {
        boolean allDay = Boolean.TRUE.equals(task.getAllDay());

        if (!allDay && task.getStartsAt() != null && task.getEndsAt() != null
                && task.getStartsAt().isAfter(task.getEndsAt())) {
            throw new IllegalArgumentException("Task start time must be before its end time.");
        }
    }



}
