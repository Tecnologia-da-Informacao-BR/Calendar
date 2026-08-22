package br.com.calendar.category;

import br.com.calendar.category.dto.CategoryResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, categoryMapper);
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

        when(categoryRepository.findAllByUser_Id(USER_ID)).thenReturn(List.of(category));
        when(categoryMapper.toResponse(category)).thenReturn(expected);

        List<CategoryResponseDTO> response = categoryService.getCategories(USER_ID);

        assertEquals(List.of(expected), response);
        verify(categoryRepository).findAllByUser_Id(USER_ID);
        verify(categoryMapper).toResponse(category);
    }

    @Test
    void returnsAnEmptyListWhenTheUserHasNoCategories() {
        when(categoryRepository.findAllByUser_Id(USER_ID)).thenReturn(List.of());

        List<CategoryResponseDTO> response = categoryService.getCategories(USER_ID);

        assertTrue(response.isEmpty());
        verify(categoryRepository).findAllByUser_Id(USER_ID);
        verifyNoInteractions(categoryMapper);
    }
}
