package br.com.calendar.category;

import br.com.calendar.category.dto.CategoryRequestDTO;
import br.com.calendar.category.dto.CategoryResponseDTO;
import br.com.calendar.category.dto.CategoryUpdateDTO;
import br.com.calendar.common.exception.ResourceNotFoundException;
import br.com.calendar.user.User;
import br.com.calendar.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final String USER_ID = "usr_abc123";

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private UserRepository userRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, categoryMapper, userRepository);
    }

    @Test
    void createsCategoryWithRequestFieldsAndAuthenticatedOwner() {
        CategoryRequestDTO request = new CategoryRequestDTO("Work", "3366FF", "briefcase");
        User user = new User();
        user.setId(USER_ID);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponseDTO response = new CategoryService(
                categoryRepository, new CategoryMapper(), userRepository)
                .createCategory(request, USER_ID);

        ArgumentCaptor<Category> savedCategory = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(savedCategory.capture());

        assertEquals("Work", savedCategory.getValue().getTitle());
        assertEquals("3366FF", savedCategory.getValue().getColor());
        assertEquals("briefcase", savedCategory.getValue().getIcon());
        assertSame(user, savedCategory.getValue().getUser());
        assertEquals("Work", response.title());
        assertEquals("3366FF", response.color());
        assertEquals("briefcase", response.icon());
    }

    @Test
    void doesNotCreateCategoryWhenAuthenticatedUserDoesNotExist() {
        CategoryRequestDTO request = new CategoryRequestDTO("Work", "3366FF", "briefcase");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.createCategory(request, USER_ID));

        verifyNoInteractions(categoryRepository, categoryMapper);
    }

    @Test
    void returnsCategoriesForTheAuthenticatedUser() {
        Category category = new Category();
        category.setId("cat_123");
        category.setTitle("Work");
        category.setColor("3366FF");
        category.setIcon("briefcase");

        CategoryResponseDTO expected = new CategoryResponseDTO(
                "cat_123", "Work", "3366FF", "briefcase");

        when(categoryRepository.findAllByUser_IdAndDeletedAtIsNull(USER_ID)).thenReturn(List.of(category));
        when(categoryMapper.toResponse(category)).thenReturn(expected);

        List<CategoryResponseDTO> response = categoryService.getCategories(USER_ID);

        assertEquals(List.of(expected), response);
        verify(categoryRepository).findAllByUser_IdAndDeletedAtIsNull(USER_ID);
        verify(categoryMapper).toResponse(category);
    }

    @Test
    void returnsAnEmptyListWhenTheUserHasNoCategories() {
        when(categoryRepository.findAllByUser_IdAndDeletedAtIsNull(USER_ID)).thenReturn(List.of());

        List<CategoryResponseDTO> response = categoryService.getCategories(USER_ID);

        assertTrue(response.isEmpty());
        verify(categoryRepository).findAllByUser_IdAndDeletedAtIsNull(USER_ID);
        verifyNoInteractions(categoryMapper);
    }

    @Test
    void updatesOnlyTheProvidedFieldsForTheCategoryOwner() {
        User owner = new User();
        owner.setId(USER_ID);

        Category category = new Category();
        category.setId("cat_123");
        category.setUser(owner);
        category.setTitle("Work");
        category.setColor("3366FF");
        category.setIcon("briefcase");

        CategoryUpdateDTO request = new CategoryUpdateDTO("Personal", null, null);

        when(categoryRepository.findById("cat_123")).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponseDTO response = new CategoryService(
                categoryRepository, new CategoryMapper(), userRepository)
                .updateCategory(request, "cat_123", USER_ID);

        ArgumentCaptor<Category> savedCategory = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(savedCategory.capture());

        assertEquals("Personal", savedCategory.getValue().getTitle());
        assertEquals("3366FF", savedCategory.getValue().getColor());
        assertEquals("briefcase", savedCategory.getValue().getIcon());
        assertEquals("Personal", response.title());
        assertEquals("3366FF", response.color());
        assertEquals("briefcase", response.icon());
    }

    @Test
    void doesNotUpdateCategoryThatDoesNotExist() {
        CategoryUpdateDTO request = new CategoryUpdateDTO("Personal", null, null);
        when(categoryRepository.findById("cat_999")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.updateCategory(request, "cat_999", USER_ID));

        verifyNoInteractions(categoryMapper);
    }

    @Test
    void doesNotUpdateCategoryOwnedByAnotherUser() {
        User otherOwner = new User();
        otherOwner.setId("usr_other");

        Category category = new Category();
        category.setId("cat_123");
        category.setUser(otherOwner);

        CategoryUpdateDTO request = new CategoryUpdateDTO("Personal", null, null);
        when(categoryRepository.findById("cat_123")).thenReturn(Optional.of(category));

        assertThrows(AccessDeniedException.class,
                () -> categoryService.updateCategory(request, "cat_123", USER_ID));

        verifyNoInteractions(categoryMapper);
    }
}
