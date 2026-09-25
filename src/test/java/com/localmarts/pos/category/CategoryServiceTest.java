package com.localmarts.pos.category;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.category.entity.Category;
import com.localmarts.pos.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AuditService auditService;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(
                categoryRepository,
                productRepository,
                auditService
        );
    }

    @Test
    void cannotDeleteCategoryUsedByProduct() {
        Category category = Category.builder()
                .id(10L)
                .name("Beverages")
                .build();
        when(categoryRepository.findByIdAndActiveTrueAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryId(10L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.softDelete(10L, 7L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(
                        "Category cannot be deleted because it is used by a product"
                );

        verify(categoryRepository, never()).save(category);
        verify(auditService).logFailed(
                7L,
                AuditAction.DELETE,
                "Category",
                10L,
                "Failed to delete category because it is used by a product: Beverages"
        );
    }

    @Test
    void deletesCategoryThatIsNotUsedByProduct() {
        Category category = Category.builder()
                .id(10L)
                .name("Beverages")
                .build();
        when(categoryRepository.findByIdAndActiveTrueAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(category));

        categoryService.softDelete(10L, 7L);

        verify(categoryRepository).save(category);
        verify(auditService).logSuccess(
                7L,
                AuditAction.DELETE,
                "Category",
                10L,
                "Deleted category: Beverages",
                null
        );
    }
}
