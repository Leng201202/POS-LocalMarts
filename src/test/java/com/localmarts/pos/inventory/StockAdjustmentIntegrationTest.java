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
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class StockAdjustmentIntegrationTest {
    @Autowired InventoryService service;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired InventoryRepository movements;
    @Autowired AuditRepository audits;
    @Autowired EntityManager entityManager;

    @Test
    void persistsOneMovementAndAuditWithTimestampAndReloadedStock() {
        User actor = User.builder().username("adjustment-admin").fullName("Adjustment Admin")
                .password("test-only").role(Role.ADMIN).build();
        actor.setCreatedBy(0L);
        users.saveAndFlush(actor);
        Product product = Product.builder().name("Adjustment Milk").stockQuantity(10).build();
        product.setCreatedBy(actor.getId());
        products.saveAndFlush(product);
        // Match the existing refund fixture: SQLite test DDL omits the Product ID type.
        entityManager.createNativeQuery("update products set id = rowid where id is null").executeUpdate();
        Long productId = product.getId();
        Long actorId = actor.getId();
        // Simulate stock changing after the browser's earlier lookup.
        product.setStockQuantity(8);
        products.saveAndFlush(product);
        entityManager.clear();
        long movementCount = movements.count();
        long auditCount = audits.count();
        Inventory result = service.manualAdjustment(productId, InventoryType.ADJUSTMENT, 6, "Physical count", actorId);
        entityManager.flush();
        Long movementId = result.getId();
        entityManager.createNativeQuery("update inventorys set id = rowid where id is null").executeUpdate();
        entityManager.clear();
        Inventory saved = movements.findById(movementId).orElseThrow();
        assertThat(saved.getBeforeStock()).isEqualTo(8);
        assertThat(saved.getAfterStock()).isEqualTo(6);
        assertThat(saved.getQuantity()).isEqualTo(-2);
        assertThat(saved.getTimestamp()).isNotNull();
        assertThat(saved.getActor().getId()).isEqualTo(actorId);
        assertThat(movements.count()).isEqualTo(movementCount + 1);
        assertThat(audits.count()).isEqualTo(auditCount + 1);
        assertThat(products.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(6);
    }
    @Test
    void adjustmentSummaryCountsManualCategoriesAndExcludesRefundConditionHistory() {
        User actor = User.builder().username("summary-admin").fullName("Summary Admin")
                .password("test-only").role(Role.ADMIN).build();
        actor.setCreatedBy(0L);
        users.saveAndFlush(actor);
        Product product = Product.builder().name("water").barcode("WATER-001").stockQuantity(15).build();
        product.setCreatedBy(actor.getId());
        products.saveAndFlush(product);
        entityManager.createNativeQuery("update products set id = rowid where id is null").executeUpdate();

        service.manualAdjustment(product.getId(), InventoryType.DAMAGE, 1, "Same note", actor.getId());
        entityManager.flush();
        entityManager.createNativeQuery("update inventorys set id = rowid where id is null").executeUpdate();
        // Regression: the reported 15 -> 14 DAMAGE operation counts as one adjustment.
        assertThat(service.countFilteredAdjustments(null, null, null)).isEqualTo(1);
        service.manualAdjustment(product.getId(), InventoryType.EXPIRED, 2, "Same note", actor.getId());
        service.manualAdjustment(product.getId(), InventoryType.MISSING, 1, "Same note", actor.getId());
        service.manualAdjustment(product.getId(), InventoryType.ADJUSTMENT, 20, "Same note", actor.getId());
        service.manualAdjustment(product.getId(), InventoryType.ADJUSTMENT, 18, "Same note", actor.getId());
        service.recordReturnedDamaged(product.getId(), 1, "Same note", actor.getId());
        service.recordReturnedExpired(product.getId(), 1, "Same note", actor.getId());
        service.sale(product.getId(), 1, "Same note", actor.getId());
        service.stockIn(product.getId(), 1, "Same note", actor.getId());
        service.refund(product.getId(), 1, "Same note", actor.getId());
        entityManager.flush();
        // Match the existing SQLite fixture workaround before querying joined history.
        entityManager.createNativeQuery("update inventorys set id = rowid where id is null").executeUpdate();

        assertThat(movements.count()).isEqualTo(10);
        assertThat(service.countFilteredAdjustments(null, null, null)).isEqualTo(5);
        assertThat(service.countFilteredAdjustments("  WATER  ", null, actor.getId())).isEqualTo(5);
        assertThat(service.countFilteredAdjustments("001", null, null)).isEqualTo(5);
        assertThat(service.countFilteredAdjustments("absent", null, null)).isZero();
        assertThat(service.countFilteredAdjustments(null, null, -1L)).isZero();
        assertThat(service.countFilteredAdjustments(null, InventoryType.ADJUSTMENT, null)).isEqualTo(2);
        for (InventoryType type : new InventoryType[]{InventoryType.DAMAGE, InventoryType.EXPIRED, InventoryType.MISSING}) {
            assertThat(service.countFilteredAdjustments(null, type, null)).isEqualTo(1);
        }
        for (InventoryType type : new InventoryType[]{InventoryType.SALE, InventoryType.STOCK_IN, InventoryType.REFUND}) {
            assertThat(service.countFilteredAdjustments(null, type, null)).isZero();
        }
    }

}
