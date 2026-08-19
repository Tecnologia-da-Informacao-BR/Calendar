package br.com.calendar.task;

import br.com.calendar.category.Category;
import br.com.calendar.category.CategoryRepository;
import br.com.calendar.common.exception.GlobalExceptionHandler;
import br.com.calendar.common.exception.InvalidTaskTimeRangeException;
import br.com.calendar.task.dto.request.CreateTaskRequest;
import br.com.calendar.task.dto.response.CreateTaskResponse;
import br.com.calendar.task.entity.Task;
import br.com.calendar.common.exception.CategoryNotFoundException;
import br.com.calendar.common.exception.UserNotFoundException;
import br.com.calendar.user.User;
import br.com.calendar.user.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class TaskService {

    private final TaskRepository repository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    public TaskService(TaskRepository repository, UserRepository userRepository, CategoryRepository categoryRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    public CreateTaskResponse create(CreateTaskRequest dto, String userId) {
        if (!dto.allDay() && dto.startsAt().isAfter(dto.endsAt())) {
            throw new InvalidTaskTimeRangeException("Task start time must be before its end time.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Category category = null;
        if (dto.categoryId() != null) {
            category = categoryRepository.findById(dto.categoryId())
                    .orElseThrow(() -> new CategoryNotFoundException("Category not found"));
        }

        Task task = TaskMapper.toEntity(dto, user, category);

        Task saved = repository.save(task);

        return TaskMapper.toResponse(saved);
    }
}
