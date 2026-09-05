package com.localmarts.pos.sale;

import com.localmarts.pos.audit.AuditRepository;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.customer.CustomerRepository;
import com.localmarts.pos.customer.entity.Customer;
import com.localmarts.pos.inventory.InventoryRepository;
import com.localmarts.pos.loyalty.entity.LoyaltyConfig;
import com.localmarts.pos.loyalty.entity.LoyaltyTransaction;
import com.localmarts.pos.loyalty.entity.TransactionType;
import com.localmarts.pos.loyalty.repository.LoyaltyConfigRepository;
import com.localmarts.pos.loyalty.repository.LoyaltyTransactionRepository;
import com.localmarts.pos.loyalty.service.LoyaltyConfigService;
import com.localmarts.pos.product.ProductRepository;
import com.localmarts.pos.product.entity.Product;
import com.localmarts.pos.sale.dto.CreateSaleRequest;
import com.localmarts.pos.sale.dto.RefundItemRequest;
import com.localmarts.pos.sale.dto.RefundReason;
import com.localmarts.pos.sale.dto.RefundRequest;
import com.localmarts.pos.sale.dto.ReturnBreakdownRequest;
import com.localmarts.pos.sale.dto.ReturnCondition;
import com.localmarts.pos.sale.dto.SaleItemRequest;
import com.localmarts.pos.sale.entity.PaymentMethod;
import com.localmarts.pos.sale.entity.Sale;
import com.localmarts.pos.sale.entity.SaleStatus;
import com.localmarts.pos.user.UserRepository;
import com.localmarts.pos.user.entity.Role;
import com.localmarts.pos.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RefundPhase3IntegrationTest {

    @Autowired private SaleService saleService;
    @Autowired private RefundService refundService;
    @Autowired private SaleRepository saleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private LoyaltyConfigService loyaltyConfigService;
    @Autowired private LoyaltyConfigRepository loyaltyConfigRepository;
    @Autowired private LoyaltyTransactionRepository loyaltyTransactionRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private AuditRepository auditRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void redeemedCheckoutAndRefundCommitAsOneConsistentBusinessFlow() {
        User cashier = User.builder()
                .fullName("Phase 3 Cashier")
                .username("phase3-cashier")
                .password("test-only")
                .role(Role.CASHIER)
                .build();
        cashier.setCreatedBy(0L);
        cashier = userRepository.save(cashier);

        Customer customer = Customer.builder()
                .name("Phase 3 Member")
                .phoneNumber("0891112222")
                .pointBalance(60L)
                .totalSpending(0L)
                .build();
        customer.setCreatedBy(cashier.getId());
        customer = customerRepository.save(customer);

        Product coke = Product.builder()
                .name("Phase 3 Coke")
                .barcode("PHASE3-COKE")
                .sellingPrice(new BigDecimal("25.00"))
                .costPrice(new BigDecimal("20.00"))
                .stockQuantity(10)
                .minimumStock(1)
                .discountPercent(new BigDecimal("10.00"))
                .build();
        coke.setCreatedBy(cashier.getId());
        coke = productRepository.save(coke);
        productRepository.flush();
        Long cokeId = coke.getId();
        entityManager.createNativeQuery(
                "update products set id = rowid where id is null"
        ).executeUpdate();

        LoyaltyConfig config = loyaltyConfigService.getConfig();
        config.setMinimumPurchaseAmount(BigDecimal.ZERO);
        config.setSpendingAmount(new BigDecimal("10.00"));
        config.setEarnedPoints(1L);
        config.setRedeemPoints(100L);
        config.setRedeemValue(new BigDecimal("10.00"));
        config.setMinimumRedeemPoints(30L);
        config.setEarningEnabled(true);
        config.setRedemptionEnabled(true);
        loyaltyConfigRepository.save(config);
        loyaltyConfigRepository.flush();
        entityManager.clear();
        assertThat(productRepository.findByIdAndDeletedAtIsNull(cokeId)).isPresent();

        SaleItemRequest checkoutLine = new SaleItemRequest();
        checkoutLine.setProductId(cokeId);
        checkoutLine.setQuantity(5);
        CreateSaleRequest checkout = new CreateSaleRequest();
        checkout.setItems(List.of(checkoutLine));
        checkout.setCustomerId(customer.getId());
        checkout.setUseLoyaltyDiscount(true);
        checkout.setPaidAmount(new BigDecimal("120.00"));
        checkout.setPaymentMethod(PaymentMethod.CARD);

        Sale sale = saleService.checkout(checkout, cashier.getId());
        Long saleId = sale.getId();
        sale.getItems().forEach(entityManager::detach);
        entityManager.detach(sale);
        entityManager.createNativeQuery(
                "update sales set id = rowid, point_earned = 12 where id is null"
        ).executeUpdate();
        entityManager.createNativeQuery(
                "update sale_items set id = rowid where id is null"
        ).executeUpdate();
        entityManager.clear();
        sale = saleRepository.findRefundDetailsById(saleId).orElseThrow();

        assertThat(sale.getPointRedeemed()).isEqualTo(50);
        assertThat(sale.getPointDiscount()).isEqualByComparingTo("5.00");
        assertThat(sale.getPointEarned()).isEqualTo(12);
        assertThat(customerRepository.findPointBalanceValueById(customer.getId()))
                .contains(22L);

        RefundItemRequest refundLine = new RefundItemRequest();
        refundLine.setSaleItemId(sale.getItems().getFirst().getId());
        refundLine.setSelected(true);
        refundLine.setQuantity(4);
        ReturnBreakdownRequest breakdown = new ReturnBreakdownRequest();
        breakdown.setQuantity(4);
        breakdown.setReason(RefundReason.WRONG_ITEM);
        breakdown.setCondition(ReturnCondition.SELLABLE);
        refundLine.setBreakdowns(List.of(breakdown));
        RefundRequest refund = new RefundRequest();
        refund.setItems(List.of(refundLine));
        refund.setPhysicalReturnConfirmed(true);

        RefundService.RefundOutcome outcome = refundService.processRefund(
                sale.getId(), refund, cashier.getId()
        );

        assertThat(outcome.totalRefundAmount()).isEqualByComparingTo("97.50");
        assertThat(outcome.restoredRedeemedPoints()).isEqualTo(25L);
        assertThat(outcome.reversedEarnedPoints()).isEqualTo(10L);
        assertThat(outcome.saleStatus()).isEqualTo(SaleStatus.PARTIALLY_REFUNDED);
        assertThat(sale.getItems().getFirst().getRefundQuantity()).isEqualTo(4);
        assertThat(productRepository.findById(cokeId).orElseThrow()
                .getStockQuantity()).isEqualTo(9);
        assertThat(customerRepository.findPointBalanceValueById(customer.getId()))
                .contains(37L);

        List<LoyaltyTransaction> transactions =
                loyaltyTransactionRepository.findBySaleIdOrderByTimestampAsc(
                        sale.getId()
                );
        assertThat(transactions)
                .extracting(LoyaltyTransaction::getType)
                .containsExactly(
                        TransactionType.REDEEM,
                        TransactionType.EARN,
                        TransactionType.REFUND_EARN_REVERSAL,
                        TransactionType.REFUND_REDEEM_RESTORE
                );
        assertThat(transactions)
                .extracting(LoyaltyTransaction::getPoints)
                .containsExactly(-50L, 12L, -10L, 25L);
        assertThat(transactions.get(2).getBeforePoint()).isEqualTo(22L);
        assertThat(transactions.get(2).getAfterPoint()).isEqualTo(12L);
        assertThat(transactions.get(3).getBeforePoint()).isEqualTo(12L);
        assertThat(transactions.get(3).getAfterPoint()).isEqualTo(37L);

        assertThat(inventoryRepository.findByProductIdOrderByTimestampDesc(cokeId))
                .hasSize(2);
        Long refundedSaleId = sale.getId();
        assertThat(auditRepository.findAllByOrderByTimestampDesc())
                .anyMatch(audit -> audit.getAction() == AuditAction.UPDATE
                        && "Sale".equals(audit.getEntityName())
                        && refundedSaleId.equals(audit.getRowId())
                        && audit.getDescription().contains("Processed refund"));
    }
}
