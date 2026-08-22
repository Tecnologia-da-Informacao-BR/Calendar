package br.com.calendar.category;

import br.com.calendar.category.dto.CategoryResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponseDTO toResponse(Category category) {
        return new CategoryResponseDTO(
                category.getId(),
                category.getTitle(),
                category.getColor(),
                category.getIcon());
    }
}
