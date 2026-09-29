package com.localmarts.pos.inventory;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.inventory.entity.*;
import com.localmarts.pos.inventory.service.InventoryService;
import com.localmarts.pos.product.ProductRepository;
import com.localmarts.pos.product.entity.Product;
import com.localmarts.pos.user.UserRepository;
import com.localmarts.pos.user.entity.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class ManualStockInServiceTest {
    @Mock InventoryRepository movements;
    @Mock ProductRepository products;
    @Mock UserRepository users;
    @Mock AuditService audits;
    InventoryService service;
    Product product;
    User actor;

    @BeforeEach void setup() {
        service = spy(new InventoryService(movements, products, users, audits));
        actor = User.builder().id(7L).role(Role.ADMIN).build();
        when(users.findById(7L)).thenReturn(Optional.of(actor));
        product = Product.builder().id(1L).name("Water").barcode("123").size("250ml").stockQuantity(15)
                .costPrice(new BigDecimal("5.00")).sellingPrice(new BigDecimal("8.00"))
                .mfd(LocalDate.of(2026, 1, 1)).exp(LocalDate.of(2027, 1, 1))
                .minimumStock(3).discountPercent(new BigDecimal("2.00")).build();
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,15,20,35", "CASHIER,15,20,35", "CASHIER,0,20,20", "ADMIN,2147483646,1,2147483647"})
    void successfulReceipt(Role role, int before, int quantity, int after) {
        actor.setRole(role);
        product.setStockQuantity(before);
        Product original = Product.builder().build();
        org.springframework.beans.BeanUtils.copyProperties(product, original);
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(movements.save(any())).thenAnswer(call -> {
            Inventory movement = call.getArgument(0);
            movement.setId(9L);
            return movement;
        });
        Inventory result = service.manualStockIn(1L, quantity, "  New delivery  ", 7L);
        assertThat(result.getInventoryType()).isEqualTo(InventoryType.STOCK_IN);
        assertThat(result.getQuantity()).isEqualTo(quantity);
        assertThat(result.getBeforeStock()).isEqualTo(before);
        assertThat(result.getAfterStock()).isEqualTo(after);
        assertThat(result.getActor()).isSameAs(actor);
        assertThat(result.getReason()).isEqualTo("New delivery");
        assertThat(product.getStockQuantity()).isEqualTo(after);
        assertThat(product).usingRecursiveComparison().ignoringFields("stockQuantity").isEqualTo(original);
        verify(service, times(1)).stockIn(1L, quantity, "New delivery", 7L);
        verify(products, times(1)).save(product);
        verify(movements, times(1)).save(result);
        verify(audits, times(1)).logSuccess(eq(7L), eq(AuditAction.CREATE), eq("Inventory"), eq(9L),
                eq("Manual Stock In"), argThat(details -> details.size() == 6
                        && details.stream().anyMatch(d -> d.getFieldName().equals("operation")
                            && d.getNewData().equals("Manual Stock In"))
                        && details.stream().anyMatch(d -> d.getFieldName().equals("stock")
                            && d.getOldData().equals(String.valueOf(before)) && d.getNewData().equals(String.valueOf(after)))
                        && details.stream().anyMatch(d -> d.getFieldName().equals("delta")
                            && d.getNewData().equals(String.valueOf(quantity)))));
        verifyNoMoreInteractions(audits);
    }

    @ParameterizedTest @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void nonpositiveQuantityRejected(int quantity) {
        assertThatThrownBy(() -> service.manualStockIn(1L, quantity, "Delivery", 7L))
                .isInstanceOf(IllegalArgumentException.class);
        noWrites();
    }

    @Test void requiredInputs() {
        assertThatThrownBy(() -> service.manualStockIn(null, 1, "Delivery", 7L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.manualStockIn(1L, null, "Delivery", 7L)).isInstanceOf(IllegalArgumentException.class);
        noWrites();
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t\n"})
    void blankReasonRejected(String reason) {
        assertThatThrownBy(() -> service.manualStockIn(1L, 1, reason, 7L)).isInstanceOf(IllegalArgumentException.class);
        noWrites();
    }

    @Test void longReasonRejected() {
        assertThatThrownBy(() -> service.manualStockIn(1L, 1, "a".repeat(256), 7L))
                .isInstanceOf(IllegalArgumentException.class);
        noWrites();
    }

    @Test void unavailableProductRejectedEvenWithDirectId() {
        when(products.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.manualStockIn(1L, 1, "Delivery", 7L)).isInstanceOf(IllegalArgumentException.class);
        when(products.findById(1L)).thenReturn(Optional.of(product));
        product.setActive(false);
        assertThatThrownBy(() -> service.manualStockIn(1L, 1, "Delivery", 7L)).isInstanceOf(IllegalArgumentException.class);
        product.setActive(true);
        product.setDeletedAt(LocalDateTime.now());
        assertThatThrownBy(() -> service.manualStockIn(1L, 1, "Delivery", 7L)).isInstanceOf(IllegalArgumentException.class);
        noWrites();
    }

    @Test void invalidCurrentStockAndOverflowRejected() {
        when(products.findById(1L)).thenReturn(Optional.of(product));
        for (Integer stock : new Integer[]{null, -1, Integer.MAX_VALUE}) {
            product.setStockQuantity(stock);
            assertThatThrownBy(() -> service.manualStockIn(1L, 1, "Delivery", 7L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        noWrites();
    }

    @Test void invalidActorRoleDenied() {
        actor.setRole(null);
        assertThatThrownBy(() -> service.manualStockIn(1L, 1, "Delivery", 7L)).isInstanceOf(AccessDeniedException.class);
        noWrites();
    }

    private void noWrites() {
        verify(service, never()).stockIn(any(), any(), any(), any());
        verify(products, never()).save(any());
        verifyNoInteractions(movements, audits);
    }
}
