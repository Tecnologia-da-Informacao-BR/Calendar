package br.com.calendar.category;

import br.com.calendar.category.dto.CategoryRequestDTO;
import br.com.calendar.category.dto.CategoryResponseDTO;
import br.com.calendar.category.dto.CategoryUpdateDTO;
import br.com.calendar.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    private static final String USER_ID = "usr_abc123";

    @Mock
    private CategoryService categoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CategoryController(categoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsCategoryForTheAuthenticatedUser() throws Exception {
        CategoryRequestDTO request = new CategoryRequestDTO("Work", "3366FF", "briefcase");
        CategoryResponseDTO expected = new CategoryResponseDTO(
                "cat_123", "Work", "3366FF", "briefcase");
        when(categoryService.createCategory(request, USER_ID)).thenReturn(expected);

        mockMvc.perform(post("/categories")
                        .principal(new UsernamePasswordAuthenticationToken(USER_ID, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Work\",\"color\":\"3366FF\",\"icon\":\"briefcase\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("cat_123"))
                .andExpect(jsonPath("$.title").value("Work"))
                .andExpect(jsonPath("$.color").value("3366FF"))
                .andExpect(jsonPath("$.icon").value("briefcase"));

        verify(categoryService).createCategory(request, USER_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"title\":null}", "{\"title\":\"   \"}"})
    void rejectsCategoryWhenTitleIsMissingOrBlank(String content) throws Exception {
        mockMvc.perform(post("/categories")
                        .principal(new UsernamePasswordAuthenticationToken(USER_ID, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid fields"))
                .andExpect(jsonPath("$.fields.title").value("Title is required."));

        verifyNoInteractions(categoryService);
    }

    @Test
    void returnsTheAuthenticatedUsersCategories() throws Exception {
        when(categoryService.getCategories(USER_ID)).thenReturn(List.of(
                new CategoryResponseDTO("cat_123", "Work", "3366FF", "briefcase")));

        mockMvc.perform(get("/categories")
                        .principal(new UsernamePasswordAuthenticationToken(USER_ID, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("cat_123"))
                .andExpect(jsonPath("$[0].title").value("Work"))
                .andExpect(jsonPath("$[0].color").value("3366FF"))
                .andExpect(jsonPath("$[0].icon").value("briefcase"));

        verify(categoryService).getCategories(USER_ID);
    }

    @Test
    void returnsAnEmptyArrayWhenTheUserHasNoCategories() throws Exception {
        when(categoryService.getCategories(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/categories")
                        .principal(new UsernamePasswordAuthenticationToken(USER_ID, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void createsCategoryReturns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Work\",\"color\":\"3366FF\",\"icon\":\"briefcase\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCategoriesReturns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatesCategoryForTheAuthenticatedUser() throws Exception {
        CategoryUpdateDTO request = new CategoryUpdateDTO("Personal", null, null);
        CategoryResponseDTO expected = new CategoryResponseDTO(
                "cat_123", "Personal", "3366FF", "briefcase");
        when(categoryService.updateCategory(request, "cat_123", USER_ID)).thenReturn(expected);

        mockMvc.perform(patch("/categories/cat_123")
                        .principal(new UsernamePasswordAuthenticationToken(USER_ID, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Personal\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cat_123"))
                .andExpect(jsonPath("$.title").value("Personal"))
                .andExpect(jsonPath("$.color").value("3366FF"))
                .andExpect(jsonPath("$.icon").value("briefcase"));

        verify(categoryService).updateCategory(request, "cat_123", USER_ID);
    }

    @Test
    void updateCategoryReturns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(patch("/categories/cat_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Personal\"}"))
                .andExpect(status().isUnauthorized());
    }
}
