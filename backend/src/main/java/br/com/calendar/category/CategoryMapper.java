package br.com.calendar.category;

import br.com.calendar.category.dto.CategoryRequestDTO;
import br.com.calendar.category.dto.CategoryResponseDTO;
import br.com.calendar.category.dto.CategoryUpdateDTO;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public Category toEntity(CategoryRequestDTO request) {
        Category category = new Category();
        category.setTitle(request.title());
        category.setColor(request.color());
        category.setIcon(request.icon());
        return category;
    }

    public void updateEntity(Category category, CategoryUpdateDTO request) {
        if (request.title() != null) {
            category.setTitle(request.title());
        }
        if (request.color() != null) {
            category.setColor(request.color());
        }
        if (request.icon() != null) {
            category.setIcon(request.icon());
        }
    }

    public CategoryResponseDTO toResponse(Category category) {
        return new CategoryResponseDTO(
                category.getId(),
                category.getTitle(),
                category.getColor(),
                category.getIcon());
    }
}
