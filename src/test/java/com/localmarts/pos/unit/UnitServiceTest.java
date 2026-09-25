package com.localmarts.pos.unit;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.product.ProductRepository;
import com.localmarts.pos.unit.entity.Unit;
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
class UnitServiceTest {

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AuditService auditService;

    private UnitService unitService;

    @BeforeEach
    void setUp() {
        unitService = new UnitService(
                unitRepository,
                productRepository,
                auditService
        );
    }

    @Test
    void cannotDeleteUnitUsedByProduct() {
        Unit unit = Unit.builder()
                .id(20L)
                .name("Bottle")
                .build();
        when(unitRepository.findByIdAndDeletedAtIsNull(20L))
                .thenReturn(Optional.of(unit));
        when(productRepository.existsByUnitId(20L)).thenReturn(true);

        assertThatThrownBy(() -> unitService.softDelete(20L, 7L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(
                        "Unit cannot be deleted because it is used by a product"
                );

        verify(unitRepository, never()).save(unit);
        verify(auditService).logFailed(
                7L,
                AuditAction.DELETE,
                "units",
                20L,
                "Failed to delete unit because it is used by a product: Bottle"
        );
    }

    @Test
    void deletesUnitThatIsNotUsedByProduct() {
        Unit unit = Unit.builder()
                .id(20L)
                .name("Bottle")
                .build();
        when(unitRepository.findByIdAndDeletedAtIsNull(20L))
                .thenReturn(Optional.of(unit));

        unitService.softDelete(20L, 7L);

        verify(unitRepository).save(unit);
        verify(auditService).logSuccess(
                7L,
                AuditAction.DELETE,
                "units",
                20L,
                "Deleted unit: Bottle",
                null
        );
    }
}
