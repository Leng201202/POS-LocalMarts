package com.localmarts.pos.category;

import com.localmarts.pos.category.entity.Category;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<Category> findAll(){
        return categoryRepository.findByDeletedAtIsNull();
    }
    public Category findById(Long id){
        return categoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(()->new RuntimeException("Category is not found"));
    }
    public Category create(Category category,Long created_by){
        if(categoryRepository.existsByNameAndDeletedAtIsNull(category.getName())){
            throw new RuntimeException("Category name already exit");
        }
        category.setCreatedBy(created_by);
        category.setActive(true);
        return categoryRepository.save(category);
    }
    public Category update(Long id, Category updatedCategory, Long updatedBy) {
        Category category = categoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        category.setName(updatedCategory.getName());
        category.setDescription(updatedCategory.getDescription());
        category.setActive(updatedCategory.getActive());
        category.setUpdatedBy(updatedBy);
        return categoryRepository.save(category);
    }



}
