package com.localmarts.pos.category;

import com.localmarts.pos.category.entity.Category;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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
    public void update(Long id, Category updatedCategory, Long updatedBy) {
        Category category = categoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        if (!category.getName().equalsIgnoreCase(updatedCategory.getName())
                && categoryRepository.existsByNameAndDeletedAtIsNull(updatedCategory.getName())) {
            throw new RuntimeException("Category name already exists");
        }
        category.setName(updatedCategory.getName());
        category.setDescription(updatedCategory.getDescription());
        category.setActive(updatedCategory.getActive());
        category.setUpdatedBy(updatedBy);
        categoryRepository.save(category);
    }

    public void softDelete(Long id, Long deletedBy){
        Category category=categoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(()-> new RuntimeException("Category not found"));
        category.setDeletedBy(deletedBy);
        category.setDeletedAt(LocalDateTime.now());
        category.setActive(false);
        categoryRepository.save(category);
    }

}
