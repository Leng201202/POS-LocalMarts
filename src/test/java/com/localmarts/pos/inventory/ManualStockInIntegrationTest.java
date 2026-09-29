package com.localmarts.pos.inventory;

import com.localmarts.pos.audit.AuditRepository;
import com.localmarts.pos.inventory.entity.Inventory;
import com.localmarts.pos.inventory.entity.InventoryType;
import com.localmarts.pos.inventory.service.InventoryService;
import com.localmarts.pos.product.ProductRepository;
import com.localmarts.pos.product.entity.Product;
import com.localmarts.pos.user.UserRepository;
import com.localmarts.pos.user.entity.Role;
import com.localmarts.pos.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ManualStockInIntegrationTest {
    @Autowired InventoryService service;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired InventoryRepository movements;
    @Autowired AuditRepository audits;
    @Autowired EntityManager entityManager;

    @Test
    void receiptUsesCurrentDatabaseStockAndPersistsOneMovementAuditAndSummaryEntry() {
        User actor = User.builder().username("manual-receiver").fullName("Manual Receiver")
                .password("test-only").role(Role.CASHIER).build();
        actor.setCreatedBy(0L);
        users.saveAndFlush(actor);
        Product product = Product.builder().name("Manual Water").barcode("MANUAL-WATER").size("250ml")
                .stockQuantity(10).costPrice(new BigDecimal("5")).sellingPrice(new BigDecimal("8"))
                .mfd(LocalDate.of(2026, 1, 1)).exp(LocalDate.of(2027, 1, 1)).minimumStock(2).build();
        product.setCreatedBy(actor.getId());
        products.saveAndFlush(product);
        // Existing SQLite integration fixtures need this because generated test DDL omits the ID type.
        entityManager.createNativeQuery("update products set id = rowid where id is null").executeUpdate();
        // Change stock after an earlier lookup; the receiving operation must use 15, not 10.
        product.setStockQuantity(15);
        products.saveAndFlush(product);
        entityManager.refresh(product);
        Product original = Product.builder().build();
        org.springframework.beans.BeanUtils.copyProperties(product, original);
        Long id = product.getId();
        Long actorId = actor.getId();
        entityManager.clear();
        long movementCount = movements.count();
        long auditCount = audits.count();
        long productCount = products.count();
        long stockInCount = service.countFilteredByType(null, null, null, InventoryType.STOCK_IN);
        long adjustmentCount = service.countFilteredAdjustments(null, null, null);
        Inventory result = service.manualStockIn(id, 20, "  New delivery  ", actorId);
        entityManager.flush();
        entityManager.createNativeQuery("update inventorys set id = rowid where id is null").executeUpdate();
        entityManager.clear();
        Inventory saved = movements.findById(result.getId()).orElseThrow();
        assertThat(saved.getInventoryType()).isEqualTo(InventoryType.STOCK_IN);
        assertThat(saved.getQuantity()).isEqualTo(20);
        assertThat(saved.getBeforeStock()).isEqualTo(15);
        assertThat(saved.getAfterStock()).isEqualTo(35);
        assertThat(saved.getReason()).isEqualTo("New delivery");
        assertThat(saved.getActor().getId()).isEqualTo(actorId);
        assertThat(saved.getTimestamp()).isNotNull();
        assertThat(org.hibernate.Hibernate.unproxy(products.findById(id).orElseThrow())).usingRecursiveComparison()
                .ignoringFields("stockQuantity", "updatedAt").isEqualTo(original);
        assertThat(products.findById(id).orElseThrow().getStockQuantity()).isEqualTo(35);
        assertThat(products.count()).isEqualTo(productCount);
        assertThat(movements.count()).isEqualTo(movementCount + 1);
        assertThat(audits.count()).isEqualTo(auditCount + 1);
        assertThat(service.countFilteredByType(null, null, null, InventoryType.STOCK_IN)).isEqualTo(stockInCount + 1);
        assertThat(service.countFilteredAdjustments(null, null, null)).isEqualTo(adjustmentCount);
    }
}
