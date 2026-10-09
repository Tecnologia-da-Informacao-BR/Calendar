package br.com.calendar.task;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

        return taskMapper.toResponse(repository.save(task));
    }

    public List<TaskResponseDTO> getTasksForDay(LocalDate date) {
        String userId = currentUser().getId();
        var startOfDay = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        var endOfDay = date.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
        return repository.findActiveTasksForDay(userId, startOfDay, endOfDay).stream()
                .map(taskMapper::toResponse)
                .toList();
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
     * Returns incomplete, non-deleted tasks whose persisted startsAt is at or
     * after Instant.now(), comparing absolute instants (UTC). Task.timezone
     * does not shift this comparison. Recurrences are not expanded.
     */
    @Transactional(readOnly = true)
    public List<TaskResponseDTO> getUpcomingTasks(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be greater than zero.");
        }

        String userId = currentUser().getId();
        Instant now = Instant.now();
        return repository.findUpcomingTasks(userId, now, PageRequest.of(0, limit)).stream()
                .map(taskMapper::toResponse)
                .toList();
    }

    /**
     * Tasks whose starts_at or ends_at falls within the given month, scoped
     * to the current user, excluding soft-deleted tasks — recurring tasks
     * (repeatInterval set) are expanded into their occurrences within the
     * month rather than returned as a single row.
     *
     * Recurrence model (RFC 5545-style, the same one Google
     * Calendar/Outlook/etc. use, simplified to the two columns this table
     * has): repeatInterval is the cadence unit (daily/weekly/monthly/yearly),
     * repeat is the stride ("every N <repeatInterval>s", defaulting to 1).
     * There's no stored end condition, so a recurring task repeats
     * indefinitely.
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

            for (Occurrence occurrence : expandOccurrences(task, monthStart, monthEnd)) {
                result.add(taskMapper.toMonthResponse(task, occurrence.id(), occurrence.startsAt(), occurrence.endsAt()));
            }
        }

        return result.stream()
                .sorted((a, b) -> a.getStartsAt().compareTo(b.getStartsAt()))
                .toList();
    }

    private record Occurrence(String id, Instant startsAt, Instant endsAt) {
    }

    // Safety net for how many occurrences a single month can legitimately
    // contain (daily with stride 1 tops out at 31) — not a substitute for
    // correctly locating where iteration should start, which
    // fastForwardOnOrBefore handles regardless of how old the task is.
    private static final int MAX_OCCURRENCES_PER_TASK = 1000;

    private List<Occurrence> expandOccurrences(Task task, Instant monthStart, Instant monthEnd) {
        ChronoUnit unit = chronoUnit(task.getRepeatInterval());
        if (unit == null) {
            // Unrecognized repeatInterval: treat as a single, non-repeating
            // occurrence rather than guessing a cadence.
            return overlapsMonth(task.getStartsAt(), task.getEndsAt(), monthStart, monthEnd)
                    ? List.of(new Occurrence(task.getId() + "_" + task.getStartsAt(), task.getStartsAt(), task.getEndsAt()))
                    : List.of();
        }

        Duration eventDuration = task.getEndsAt() != null
                ? Duration.between(task.getStartsAt(), task.getEndsAt())
                : null;
        int stride = task.getRepeat() != null && task.getRepeat() > 0 ? task.getRepeat() : 1;

        List<Occurrence> occurrences = new ArrayList<>();
        // Jump straight to the last occurrence at/before the start of the
        // month instead of walking one stride at a time from the task's
        // original startsAt — otherwise a long-lived daily/weekly task hits
        // MAX_OCCURRENCES_PER_TASK before ever reaching a month that's years
        // out, and silently disappears from it.
        Instant cursor = fastForwardOnOrBefore(task.getStartsAt(), unit, stride, monthStart);

        for (int i = 0; i < MAX_OCCURRENCES_PER_TASK && !cursor.isAfter(monthEnd); i++) {
            Instant occursAt = cursor;
            Instant occursUntil = eventDuration != null ? occursAt.plus(eventDuration) : null;

            if (overlapsMonth(occursAt, occursUntil, monthStart, monthEnd)) {
                occurrences.add(new Occurrence(task.getId() + "_" + occursAt, occursAt, occursUntil));
            }

            cursor = cursor.atZone(ZoneOffset.UTC).plus(stride, unit).toInstant();
        }

        return occurrences;
    }

    /**
     * Does an event running from `start` (to `end`, or a single instant if
     * `end` is null) overlap the [monthStart, monthEnd] range at all? Covers
     * starting in the month, ending in the month, and fully spanning it.
     */
    private static boolean overlapsMonth(Instant start, Instant end, Instant monthStart, Instant monthEnd) {
        if (start.isAfter(monthEnd)) {
            return false;
        }
        Instant effectiveEnd = end != null ? end : start;
        return !effectiveEnd.isBefore(monthStart);
    }

    /**
     * The largest value of the form `start + k*stride*unit` (k >= 0) that is
     * <= target, computed directly instead of by iterating — `unit.between`
     * gives the exact number of whole units between two instants, so this is
     * O(1) regardless of how far apart `start` and `target` are.
     */
    private static Instant fastForwardOnOrBefore(Instant start, ChronoUnit unit, int stride, Instant target) {
        if (!start.isBefore(target)) {
            return start;
        }

        long unitsBetween = unit.between(start.atZone(ZoneOffset.UTC), target.atZone(ZoneOffset.UTC));
        long strides = unitsBetween / stride;
        return start.atZone(ZoneOffset.UTC).plus(strides * stride, unit).toInstant();
    }

    private static ChronoUnit chronoUnit(String repeatInterval) {
        return switch (repeatInterval.toLowerCase()) {
            case "daily" -> ChronoUnit.DAYS;
            case "weekly" -> ChronoUnit.WEEKS;
            case "monthly" -> ChronoUnit.MONTHS;
            case "yearly" -> ChronoUnit.YEARS;
            default -> null;
        };
    }

    public void deleteTask(String id) {
        repository.findByIdAndDeletedAtIsNull(id).ifPresent(task -> {
            checkOwnership(task);
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
    public TaskResponseDTO updateTask(TaskRequestDTO request, String taskId) {
        Task existingTask = findOwnedTask(taskId);
        User currentUser = currentUser();

        if (request.getCategoryId() != null) {
            existingTask.setCategory(categoryService.getCategoryOwnedByUser(request.getCategoryId(), currentUser.getId()));
        }

        taskMapper.updateEntity(existingTask, request);

        validateTaskDates(existingTask);

        return taskMapper.toResponse(repository.save(existingTask));
    }

    /**
     * PUT semantics: full replacement. Fields omitted from the request clear
     * the corresponding value on the task, including the category.
     */
    public TaskResponseDTO replaceTask(TaskRequestDTO request, String taskId) {
        Task existingTask = findOwnedTask(taskId);
        User currentUser = currentUser();

        Category category = request.getCategoryId() != null
                ? categoryService.getCategoryOwnedByUser(request.getCategoryId(), currentUser.getId())
                : null;
        existingTask.setCategory(category);

        taskMapper.replaceEntity(existingTask, request);

        validateTaskDates(existingTask);

        return taskMapper.toResponse(repository.save(existingTask));
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
