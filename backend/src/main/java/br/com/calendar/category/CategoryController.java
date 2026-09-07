package br.com.calendar.category;

import br.com.calendar.category.dto.CategoryRequestDTO;
import br.com.calendar.category.dto.CategoryResponseDTO;
import br.com.calendar.category.dto.CategoryUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<CategoryResponseDTO> createCategory(
            @Valid @RequestBody CategoryRequestDTO request, Authentication authentication) {
        String userId = authenticatedUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.createCategory(request, userId));
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponseDTO>> getCategories(Authentication authentication) {
        return ResponseEntity.ok(categoryService.getCategories(authenticatedUserId(authentication)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CategoryResponseDTO> updateCategory(
            @PathVariable String id,
            @Valid @RequestBody CategoryUpdateDTO request,
            Authentication authentication) {
        String userId = authenticatedUserId(authentication);
        return ResponseEntity.ok(categoryService.updateCategory(request, id, userId));
    }

    private String authenticatedUserId(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        return authentication.getName();
    }
}
