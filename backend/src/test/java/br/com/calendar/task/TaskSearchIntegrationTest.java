package br.com.calendar.task;

import br.com.calendar.auth.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TaskSearchIntegrationTest {

    private static final String USER_ID = "search-owner";
    private static final String OTHER_USER_ID = "search-other";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtUtil jwtUtil;

    private String token;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jdbc.update("INSERT INTO users (id, name, email_confirmed) VALUES (?, ?, false), (?, ?, false)",
                USER_ID, "Search owner", OTHER_USER_ID, "Other user");
        token = jwtUtil.generateToken(USER_ID);
    }

    @ParameterizedTest
    @CsvSource({
            "Reunião de equipe, uni",
            "Reunião de equipe, REUNIÃO",
            "REUNIÃO DE EQUIPE, reunião",
            "Reunião de equipe, reuniao",
            "Reuniao de equipe, REUNIÃO",
            "Reunia\u0303o de equipe, reuniao"
    })
    void matchesPartialTitleIgnoringCaseAndAccents(String title, String keyword) throws Exception {
        insertTask("matching", USER_ID, title);
        insertTask("unrelated", USER_ID, "Comprar livros");

        search(keyword)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("matching"))
                .andExpect(jsonPath("$[0].title").value(title));
    }

    @Test
    void trimsKeyword() throws Exception {
        insertTask("matching", USER_ID, "Reunião de equipe");

        search(" \t reuniao \n ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("matching"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void rejectsEmptyOrWhitespaceKeyword(String keyword) throws Exception {
        search(keyword)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Keyword is required."));
    }

    @Test
    void rejectsMissingKeyword() throws Exception {
        mockMvc.perform(get("/tasks/search").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "\\", "' OR 1=1 --"})
    void treatsKeywordLiterally(String keyword) throws Exception {
        insertTask("matching", USER_ID, "Anotar " + keyword + " hoje");
        insertTask("unrelated", USER_ID, "Anotar qualquer coisa hoje");

        search(keyword)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("matching"));
    }

    @Test
    void returnsEmptyArrayWhenNothingMatches() throws Exception {
        insertTask("unrelated", USER_ID, "Comprar livros");

        search("reuniao")
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void excludesSoftDeletedTasks() throws Exception {
        insertTask("active", USER_ID, "Reunião ativa");
        insertTask("deleted", USER_ID, "Reunião removida");
        jdbc.update("UPDATE task SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", "deleted");

        search("reuniao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("active"));
    }

    @Test
    void onlyReturnsTasksOwnedByAuthenticatedUser() throws Exception {
        insertTask("owned", USER_ID, "Reunião");
        insertTask("other", OTHER_USER_ID, "Reunião");

        search("reuniao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("owned"));

        token = jwtUtil.generateToken(OTHER_USER_ID);
        search("reuniao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("other"));
    }

    @Test
    void doesNotSearchDescription() throws Exception {
        insertTask("description-only", USER_ID, "Planejamento");
        jdbc.update("UPDATE task SET description = ? WHERE id = ?", "Reunião", "description-only");

        search("reuniao")
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void returnsExistingTaskResponseFormat() throws Exception {
        jdbc.update("INSERT INTO category (id, user_id, title) VALUES (?, ?, ?)",
                "search-category", USER_ID, "Trabalho");
        insertTask("matching", USER_ID, "Reunião de equipe");
        jdbc.update("""
                UPDATE task SET description = ?, category_id = ?, priority = ?, all_day = false,
                    completed_at = TIMESTAMPTZ '2026-10-01 11:00:00+00'
                WHERE id = ?
                """, "Planejamento semanal", "search-category", "HIGH", "matching");

        search("reuniao")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("matching"))
                .andExpect(jsonPath("$[0].title").value("Reunião de equipe"))
                .andExpect(jsonPath("$[0].description").value("Planejamento semanal"))
                .andExpect(jsonPath("$[0].categoryId").value("search-category"))
                .andExpect(jsonPath("$[0].priority").value("HIGH"))
                .andExpect(jsonPath("$[0].allDay").value(false))
                .andExpect(jsonPath("$[0].startsAt").value("2026-10-01T10:00:00Z"))
                .andExpect(jsonPath("$[0].completedAt").value("2026-10-01T11:00:00Z"))
                .andExpect(jsonPath("$[0].deletedAt").value(nullValue()))
                .andExpect(jsonPath("$[0].user").doesNotExist());
    }

    @Test
    void returns401WithoutAuthentication() throws Exception {
        mockMvc.perform(get("/tasks/search").param("keyword", "reuniao"))
                .andExpect(status().isUnauthorized());
    }

    private void insertTask(String id, String userId, String title) {
        jdbc.update("""
                INSERT INTO task (id, user_id, title, starts_at)
                VALUES (?, ?, ?, TIMESTAMPTZ '2026-10-01 10:00:00+00')
                """, id, userId, title);
    }

    private ResultActions search(String keyword) throws Exception {
        return mockMvc.perform(get("/tasks/search")
                .header("Authorization", "Bearer " + token)
                .param("keyword", keyword));
    }
}
