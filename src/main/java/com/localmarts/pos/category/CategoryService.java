package com.localmarts.pos.category;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.audit.entity.AuditDetails;
import com.localmarts.pos.audit.utils.AuditChangeHelper;
import com.localmarts.pos.category.entity.Category;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CategoryService {
    private final String entity="Category";
    private final CategoryRepository categoryRepository;
    private final AuditService auditService;

    public CategoryService(CategoryRepository categoryRepository, AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.auditService = auditService;
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
            auditService.logFailed(created_by, AuditAction.CREATE,entity,null,"Failed to create category. Name already exists: " + category.getName());
            throw new RuntimeException("Category name already exit");
        }
        category.setCreatedBy(created_by);
        category.setActive(true);
        Category savedCategory= categoryRepository.save(category);
        auditService.logSuccess(created_by,AuditAction.CREATE,entity,savedCategory.getId(),"Created category: " + savedCategory.getName());
        return savedCategory;
    }
    public void update(Long id, Category updatedCategory, Long updatedBy) {
        Category category = categoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        if (!category.getName().equalsIgnoreCase(updatedCategory.getName())
                && categoryRepository.existsByNameAndDeletedAtIsNull(updatedCategory.getName())) {
            auditService.logFailed(updatedBy,AuditAction.UPDATE,entity,id,"Failed to update category. Name already exists: " + category.getName());
            throw new RuntimeException("Category name already exists");
        }
        List<AuditDetails> details = new AuditChangeHelper()
                .addChange("name", category.getName(), updatedCategory.getName())
                .addChange("description", category.getDescription(), updatedCategory.getDescription())
                .addChange("active", category.getActive(), updatedCategory.getActive())
                .build();
        category.setName(updatedCategory.getName());
        category.setDescription(updatedCategory.getDescription());
        category.setActive(updatedCategory.getActive());
        category.setUpdatedBy(updatedBy);
        categoryRepository.save(category);
        auditService.logSuccess(
                updatedBy,
                AuditAction.UPDATE,
                "Category",
                id,
                "Updated category: " + category.getName(),
                details
        );
    }

    public void softDelete(Long id, Long deletedBy){
        Category category=categoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(()-> new RuntimeException("Category not found"));
        category.setDeletedBy(deletedBy);
        category.setDeletedAt(LocalDateTime.now());
        category.setActive(false);

        categoryRepository.save(category);
        auditService.logSuccess(deletedBy, AuditAction.DELETE, entity, id, "Deleted category: " + category.getName(), null);
    }

}
