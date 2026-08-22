package br.com.calendar.category;

import br.com.calendar.category.dto.CategoryResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryRepository categoryRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponseDTO> getCategories(String userId) {
        return categoryRepository.findAllByUser_Id(userId).stream()
                .map(categoryMapper::toResponse)
                .toList();
    }
}
