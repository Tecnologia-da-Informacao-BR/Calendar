package br.com.calendar.category;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.calendar.category.dto.CategoryRequestDTO;
import br.com.calendar.category.dto.CategoryResponseDTO;
import br.com.calendar.category.dto.CategoryUpdateDTO;
import br.com.calendar.common.exception.ResourceConflictException;
import br.com.calendar.common.exception.ResourceNotFoundException;
import br.com.calendar.task.TaskRepository;
import br.com.calendar.user.User;
import br.com.calendar.user.UserRepository;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TaskRepository taskRepository;
    private final CategoryMapper categoryMapper;
    private final UserRepository userRepository;

    public CategoryService(CategoryRepository categoryRepository, TaskRepository taskRepository, CategoryMapper categoryMapper,
                           UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.taskRepository = taskRepository;
        this.categoryMapper = categoryMapper;
        this.userRepository = userRepository;
    }

    public CategoryResponseDTO createCategory(CategoryRequestDTO request, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Category category = categoryMapper.toEntity(request);
        category.setUser(user);

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponseDTO> getCategories(String userId) {
        return categoryRepository.findAllByUser_IdAndDeletedAtIsNull(userId).stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Category getCategoryOwnedByUser(String categoryId, String userId) {
        return categoryRepository.findByIdAndUser_IdAndDeletedAtIsNull(categoryId, userId)
                .orElseThrow(() -> new AccessDeniedException("Category does not belong to the current user"));
    }

    @Transactional
    public CategoryResponseDTO updateCategory(CategoryUpdateDTO request, String categoryId, String userId) {
        Category category = getCategoryOwnedByUser(categoryId, userId);
        categoryMapper.updateEntity(category, request);
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(String categoryId, String userId) {
        Category category = getCategoryOwnedByUser(categoryId, userId);

        checkCategoryHasNoTasks(categoryId);

        categoryRepository.delete(category);
    }

    private void checkCategoryHasNoTasks(String categoryId) {
        if (taskRepository.existsByCategory_IdAndDeletedAtIsNull(categoryId)) {
            throw new ResourceConflictException("Cannot delete category with associated tasks");
        }
    }



}

