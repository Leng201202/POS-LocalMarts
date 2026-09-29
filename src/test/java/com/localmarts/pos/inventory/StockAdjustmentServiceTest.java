package com.localmarts.pos.inventory;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.inventory.entity.Inventory;
import com.localmarts.pos.inventory.entity.InventoryType;
import com.localmarts.pos.inventory.service.InventoryService;
import com.localmarts.pos.product.ProductRepository;
import com.localmarts.pos.product.entity.Product;
import com.localmarts.pos.user.UserRepository;
import com.localmarts.pos.user.entity.Role;
import com.localmarts.pos.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class StockAdjustmentServiceTest {
    @Mock InventoryRepository movements;
    @Mock ProductRepository products;
    @Mock UserRepository users;
    @Mock AuditService audits;
    InventoryService service;
    Product product;
    User actor;

    @BeforeEach
    void setup() {
        service = new InventoryService(movements, products, users, audits);
        product = Product.builder().id(1L).name("Milk").stockQuantity(10).build();
        actor = User.builder().id(7L).role(Role.ADMIN).build();
        when(users.findById(7L)).thenReturn(Optional.of(actor));
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,DAMAGE,3,7,-3", "CASHIER,DAMAGE,3,7,-3", "CASHIER,EXPIRED,2,8,-2",
        "ADMIN,MISSING,4,6,-4", "ADMIN,ADJUSTMENT,15,15,5", "ADMIN,ADJUSTMENT,6,6,-4",
        "ADMIN,ADJUSTMENT,0,0,-10"})
    void successfulMovement(Role role, InventoryType type, int amount, int after, int delta) {
        actor.setRole(role);
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(movements.save(any())).thenAnswer(invocation -> {
            Inventory movement = invocation.getArgument(0);
            movement.setId(9L);
            return movement;
        });
        Inventory result = service.manualAdjustment(1L, type, amount, "  Stock check  ", 7L);
        assertThat(result.getInventoryType()).isEqualTo(type);
        assertThat(result.getBeforeStock()).isEqualTo(10);
        assertThat(result.getAfterStock()).isEqualTo(after);
        assertThat(result.getQuantity()).isEqualTo(delta);
        assertThat(result.getReason()).isEqualTo("Stock check");
        assertThat(result.getActor()).isSameAs(actor);
        assertThat(product.getStockQuantity()).isEqualTo(after);
        verify(movements, times(1)).save(result);
        verify(products, times(1)).save(product);
        verify(audits, times(1)).logSuccess(eq(7L), any(), eq("Inventory"), eq(9L), anyString(),
                argThat(details -> details.size() == 6 && details.stream().anyMatch(
                        d -> d.getFieldName().equals("stock") && d.getOldData().equals("10")
                                && d.getNewData().equals(String.valueOf(after)))));
        verifyNoMoreInteractions(audits);
    }

    @ParameterizedTest
    @CsvSource({"MISSING", "ADJUSTMENT", "STOCK_IN", "SALE", "REFUND"})
    void cashierCannotForgePrivilegedType(InventoryType type) {
        actor.setRole(Role.CASHIER);
        assertThatThrownBy(() -> service.manualAdjustment(1L, type, 1, "Check", 7L))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(products, movements, audits);
    }

    @ParameterizedTest
    @CsvSource({"DAMAGE,11", "EXPIRED,11", "MISSING,11", "ADJUSTMENT,10"})
    void stockRuleFailuresDoNotWrite(InventoryType type, int amount) {
        when(products.findById(1L)).thenReturn(Optional.of(product));
        assertThatThrownBy(() -> service.manualAdjustment(1L, type, amount, "Check", 7L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(products, never()).save(any());
        verifyNoInteractions(movements, audits);
    }

    @ParameterizedTest
    @CsvSource({"DAMAGE,0", "DAMAGE,-1", "EXPIRED,0", "MISSING,-1", "ADJUSTMENT,-1"})
    void invalidAmount(InventoryType type, int amount) {
        assertThatThrownBy(() -> service.manualAdjustment(1L, type, amount, "Check", 7L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(products, movements, audits);
    }

    @Test
    void missingAndOversizedReasonsAndMissingFields() {
        for (String reason : new String[]{null, "", "  ", "a".repeat(256)}) {
            assertThatThrownBy(() -> service.manualAdjustment(1L, InventoryType.DAMAGE, 1, reason, 7L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service.manualAdjustment(null, InventoryType.DAMAGE, 1, "Check", 7L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.manualAdjustment(1L, InventoryType.DAMAGE, null, "Check", 7L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.manualAdjustment(1L, InventoryType.SALE, 1, "Check", 7L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(products, movements, audits);
    }

    @Test
    void unavailableProducts() {
        when(products.findById(1L)).thenReturn(Optional.of(product));
        product.setActive(false);
        assertThatThrownBy(() -> service.manualAdjustment(1L, InventoryType.DAMAGE, 1, "Check", 7L))
                .isInstanceOf(IllegalArgumentException.class);
        product.setActive(true);
        product.setDeletedAt(LocalDateTime.now());
        assertThatThrownBy(() -> service.manualAdjustment(1L, InventoryType.ADJUSTMENT, 5, "Check", 7L))
                .isInstanceOf(IllegalArgumentException.class);
        when(products.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.manualAdjustment(1L, InventoryType.DAMAGE, 1, "Check", 7L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(products, never()).save(any());
        verifyNoInteractions(movements, audits);
    }
}
