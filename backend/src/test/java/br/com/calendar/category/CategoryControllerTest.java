package br.com.calendar.category;

import br.com.calendar.category.dto.CategoryResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                .build();
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
}
