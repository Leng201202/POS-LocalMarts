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

    public Category create(Category category, Long createdBy) {

        category.setName(normalize(category.getName()));
        category.setDescription(normalize(category.getDescription()));

        if (categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(
                category.getName())) {

            auditService.logFailed(
                    createdBy,
                    AuditAction.CREATE,
                    entity,
                    null,
                    "Failed to create category. Name already exists: "
                            + category.getName()
            );

            throw new RuntimeException("Category name already exists");
        }

        category.setCreatedBy(createdBy);
        category.setActive(true);

        Category savedCategory = categoryRepository.save(category);

        auditService.logSuccess(
                createdBy,
                AuditAction.CREATE,
                entity,
                savedCategory.getId(),
                "Created category: " + savedCategory.getName()
        );

        return savedCategory;
    }
    
    public void update(
            Long id,
            Category updatedCategory,
            Long updatedBy) {

        updatedCategory.setName(normalize(updatedCategory.getName()));
        updatedCategory.setDescription(
                normalize(updatedCategory.getDescription())
        );

        Category category = findById(id);

        boolean nameChanged =
                !category.getName()
                        .equalsIgnoreCase(updatedCategory.getName());

        if (nameChanged
                && categoryRepository
                .existsByNameIgnoreCaseAndDeletedAtIsNull(
                        updatedCategory.getName())) {

            auditService.logFailed(
                    updatedBy,
                    AuditAction.UPDATE,
                    entity,
                    id,
                    "Failed to update category. Name already exists: "
                            + updatedCategory.getName()
            );

            throw new RuntimeException("Category name already exists");
        }

        List<AuditDetails> details = new AuditChangeHelper()
                .addChange(
                        "name",
                        category.getName(),
                        updatedCategory.getName()
                )
                .addChange(
                        "description",
                        category.getDescription(),
                        updatedCategory.getDescription()
                )
                .addChange(
                        "active",
                        category.getActive(),
                        updatedCategory.getActive()
                )
                .build();

        category.setName(updatedCategory.getName());
        category.setDescription(updatedCategory.getDescription());
        category.setActive(updatedCategory.getActive());
        category.setUpdatedBy(updatedBy);

        Category savedCategory = categoryRepository.save(category);

        auditService.logSuccess(
                updatedBy,
                AuditAction.UPDATE,
                entity,
                savedCategory.getId(),
                "Updated category: " + savedCategory.getName(),
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
    
    private String normalize(String value) {
    return value == null ? null : value.trim();
}

}
