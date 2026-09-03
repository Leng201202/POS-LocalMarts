package com.localmarts.pos.sale;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.customer.CustomerService;
import com.localmarts.pos.customer.entity.Customer;
import com.localmarts.pos.inventory.service.InventoryService;
import com.localmarts.pos.loyalty.entity.LoyaltyConfig;
import com.localmarts.pos.loyalty.service.LoyaltyService;
import com.localmarts.pos.product.ProductRepository;
import com.localmarts.pos.product.ProductService;
import com.localmarts.pos.product.entity.Product;
import com.localmarts.pos.sale.dto.CreateSaleRequest;
import com.localmarts.pos.sale.dto.SaleItemRequest;
import com.localmarts.pos.sale.entity.PaymentMethod;
import com.localmarts.pos.sale.entity.Sale;
import com.localmarts.pos.user.UserRepository;
import com.localmarts.pos.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaleServiceTest {

    private static final Long ACTOR_ID = 7L;

    @Mock private SaleRepository saleRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductService productService;
    @Mock private CustomerService customerService;
    @Mock private LoyaltyService loyaltyService;
    @Mock private InventoryService inventoryService;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    private SaleService saleService;

    @BeforeEach
    void setUp() {
        saleService = new SaleService(
                saleRepository,
                productRepository,
                productService,
                customerService,
                loyaltyService,
                inventoryService,
                userRepository,
                auditService
        );
        User cashier = User.builder().id(ACTOR_ID).build();
        cashier.setActive(true);
        when(userRepository.findById(ACTOR_ID))
                .thenReturn(Optional.of(cashier));
        when(saleRepository.existsByInvoiceNo(any())).thenReturn(false);
        lenient().when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale sale = invocation.getArgument(0);
            sale.setId(99L);
            return sale;
        });
    }

    @Test
    void walkInPaysFullPriceEvenWhenProductHasLoyaltyPercentage() {
        Product coke = product(1L, "Coke", "25.00", "10.00", 10);
        when(productRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(coke));

        Sale sale = saleService.checkout(
                request(null, false, "50.00", item(1L, 2)),
                ACTOR_ID
        );

        assertThat(sale.getSubtotal()).isEqualByComparingTo("50.00");
        assertThat(sale.getTotalDiscount()).isEqualByComparingTo("0.00");
        assertThat(sale.getPointDiscount()).isEqualByComparingTo("0.00");
        assertThat(sale.getPointRedeemed()).isZero();
        assertThat(sale.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(sale.getItems().getFirst().getDiscountAmount())
                .isEqualByComparingTo("0.00");
        assertThat(sale.getItems().getFirst().getNetTotal())
                .isEqualByComparingTo("50.00");
        assertThat(sale.getItems().getFirst().getDiscountPercentSnapshot())
                .isEqualByComparingTo("10.00");
        verify(loyaltyService, never()).redeemPoints(any(), any(), anyLong());
    }

    @Test
    void memberSayingNoReceivesNoDiscountAndEarnsFromFullTotal() {
        Customer customer = Customer.builder().id(3L).pointBalance(120L).build();
        LoyaltyConfig config = config(true, 50L);
        Product coke = product(1L, "Coke", "25.00", "10.00", 10);
        when(customerService.findActiveById(3L)).thenReturn(Optional.of(customer));
        when(loyaltyService.getCurrentConfig()).thenReturn(config);
        when(productRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(coke));
        when(loyaltyService.earnPoint(
                eq(customer),
                any(Sale.class),
                eq(new BigDecimal("50.00")),
                eq(config)
        ))
                .thenReturn(5L);

        Sale sale = saleService.checkout(
                request(3L, false, "50.00", item(1L, 2)),
                ACTOR_ID
        );

        assertThat(sale.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(sale.getPointDiscount()).isEqualByComparingTo("0.00");
        assertThat(sale.getPointRedeemed()).isZero();
        assertThat(sale.getPointEarned()).isEqualTo(5);
        verify(loyaltyService, never()).redeemPoints(any(), any(), anyLong());
    }

    @Test
    void loyaltyYesAppliesCompleteCartBenefitAndCeilingPointCost() {
        Customer customer = Customer.builder().id(3L).pointBalance(120L).build();
        LoyaltyConfig config = config(true, 50L);
        Product coke = product(1L, "Coke", "25.00", "10.00", 10);
        Product dorito = product(2L, "Dorito", "35.00", "5.00", 10);
        Product water = product(3L, "Water", "18.00", "0.00", 10);
        when(customerService.findActiveById(3L)).thenReturn(Optional.of(customer));
        when(loyaltyService.getCurrentConfig()).thenReturn(config);
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(coke));
        when(productRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(dorito));
        when(productRepository.findByIdAndDeletedAtIsNull(3L)).thenReturn(Optional.of(water));
        when(loyaltyService.earnPoint(
                eq(customer),
                any(Sale.class),
                eq(new BigDecimal("114.25")),
                eq(config)
        ))
                .thenReturn(11L);

        Sale sale = saleService.checkout(
                request(
                        3L,
                        true,
                        "114.25",
                        item(1L, 2),
                        item(2L, 1),
                        item(3L, 2)
                ),
                ACTOR_ID
        );

        assertThat(sale.getSubtotal()).isEqualByComparingTo("121.00");
        assertThat(sale.getPointDiscount()).isEqualByComparingTo("6.75");
        assertThat(sale.getTotalDiscount()).isEqualByComparingTo("6.75");
        assertThat(sale.getTotalAmount()).isEqualByComparingTo("114.25");
        assertThat(sale.getPointRedeemed()).isEqualTo(68);
        assertThat(sale.getPointEarned()).isEqualTo(11);
        assertThat(sale.getItems())
                .extracting(item -> item.getDiscountAmount().toPlainString())
                .containsExactly("5.00", "1.75", "0.00");
        assertThat(sale.getItems())
                .extracting(item -> item.getNetTotal().toPlainString())
                .containsExactly("45.00", "33.25", "36.00");
        verify(loyaltyService).redeemPoints(customer, sale, 68L);
        verify(loyaltyService).earnPoint(
                customer,
                sale,
                new BigDecimal("114.25"),
                config
        );
    }

    @Test
    void allOrNothingRejectsCartBelowMinimumWithoutApplyingPartialDiscount() {
        Customer customer = Customer.builder().id(3L).pointBalance(120L).build();
        LoyaltyConfig config = config(true, 60L);
        Product coke = product(1L, "Coke", "25.00", "10.00", 10);
        when(customerService.findActiveById(3L)).thenReturn(Optional.of(customer));
        when(loyaltyService.getCurrentConfig()).thenReturn(config);
        when(productRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(coke));

        assertThatThrownBy(() -> saleService.checkout(
                request(3L, true, "45.00", item(1L, 2)),
                ACTOR_ID
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("below the minimum");

        verify(loyaltyService, never()).redeemPoints(any(), any(), anyLong());
        verify(saleRepository, never()).save(any(Sale.class));
    }

    @Test
    void allOrNothingRejectsBalanceBelowCompleteCartRequirement() {
        Customer customer = Customer.builder().id(3L).pointBalance(67L).build();
        LoyaltyConfig config = config(true, 50L);
        Product coke = product(1L, "Coke", "25.00", "10.00", 10);
        Product dorito = product(2L, "Dorito", "35.00", "5.00", 10);
        when(customerService.findActiveById(3L)).thenReturn(Optional.of(customer));
        when(loyaltyService.getCurrentConfig()).thenReturn(config);
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(coke));
        when(productRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(dorito));

        assertThatThrownBy(() -> saleService.checkout(
                request(3L, true, "78.25", item(1L, 2), item(2L, 1)),
                ACTOR_ID
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("complete loyalty discount");

        verify(loyaltyService, never()).redeemPoints(any(), any(), anyLong());
    }

    @Test
    void disabledRedemptionLeavesNormalMemberCheckoutAvailable() {
        Customer customer = Customer.builder().id(3L).pointBalance(120L).build();
        LoyaltyConfig config = config(false, 50L);
        Product coke = product(1L, "Coke", "25.00", "10.00", 10);
        when(customerService.findActiveById(3L)).thenReturn(Optional.of(customer));
        when(loyaltyService.getCurrentConfig()).thenReturn(config);
        when(productRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(coke));

        Sale sale = saleService.checkout(
                request(3L, false, "50.00", item(1L, 2)),
                ACTOR_ID
        );

        assertThat(sale.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(sale.getPointRedeemed()).isZero();
        verify(loyaltyService, never()).redeemPoints(any(), any(), anyLong());
    }

    @Test
    void inventoryFailureStopsCheckoutBeforeSaleOrPointDeduction() {
        Product coke = product(1L, "Coke", "25.00", "10.00", 10);
        when(productRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(coke));
        doThrow(new IllegalStateException("Inventory update failed"))
                .when(inventoryService)
                .sale(eq(1L), eq(2), any(), eq(ACTOR_ID));

        assertThatThrownBy(() -> saleService.checkout(
                request(null, false, "50.00", item(1L, 2)),
                ACTOR_ID
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Inventory update failed");

        verify(saleRepository, never()).save(any(Sale.class));
        verify(loyaltyService, never()).redeemPoints(any(), any(), anyLong());
        verify(auditService, never()).logSuccess(anyLong(), any(), any(), anyLong(), any());
    }

    @Test
    void redemptionFailureStopsEarningAndAuditAndEscapesTransactionalCheckout() {
        Customer customer = Customer.builder().id(3L).pointBalance(120L).build();
        LoyaltyConfig config = config(true, 50L);
        Product coke = product(1L, "Coke", "25.00", "10.00", 10);
        when(customerService.findActiveById(3L)).thenReturn(Optional.of(customer));
        when(loyaltyService.getCurrentConfig()).thenReturn(config);
        when(productRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(coke));
        doThrow(new IllegalArgumentException("Points changed concurrently"))
                .when(loyaltyService)
                .redeemPoints(eq(customer), any(Sale.class), eq(50L));

        assertThatThrownBy(() -> saleService.checkout(
                request(3L, true, "45.00", item(1L, 2)),
                ACTOR_ID
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Points changed concurrently");

        verify(loyaltyService, never()).earnPoint(any(), any(), any(), any());
        verify(auditService, never()).logSuccess(anyLong(), any(), any(), anyLong(), any());
    }

    @Test
    void auditFailureEscapesCheckoutSoSpringCanRollBackTheTransaction() {
        Product coke = product(1L, "Coke", "25.00", "10.00", 10);
        when(productRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(coke));
        doThrow(new IllegalStateException("Audit unavailable"))
                .when(auditService)
                .logSuccess(eq(ACTOR_ID), any(), eq("Sale"), eq(99L), any());

        assertThatThrownBy(() -> saleService.checkout(
                request(null, false, "50.00", item(1L, 2)),
                ACTOR_ID
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Audit unavailable");
    }

    private CreateSaleRequest request(
            Long customerId,
            boolean useLoyalty,
            String paidAmount,
            SaleItemRequest... items
    ) {
        CreateSaleRequest request = new CreateSaleRequest();
        request.setCustomerId(customerId);
        request.setUseLoyaltyDiscount(useLoyalty);
        request.setItems(List.of(items));
        request.setPaidAmount(new BigDecimal(paidAmount));
        request.setPaymentMethod(PaymentMethod.CARD);
        return request;
    }

    private SaleItemRequest item(Long productId, int quantity) {
        SaleItemRequest item = new SaleItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    private Product product(
            Long id,
            String name,
            String price,
            String loyaltyPercent,
            int stock
    ) {
        Product product = Product.builder()
                .id(id)
                .name(name)
                .barcode("BC-" + id)
                .sellingPrice(new BigDecimal(price))
                .costPrice(BigDecimal.ONE)
                .discountPercent(new BigDecimal(loyaltyPercent))
                .stockQuantity(stock)
                .build();
        product.setActive(true);
        return product;
    }

    private LoyaltyConfig config(boolean redemptionEnabled, long minimumPoints) {
        return LoyaltyConfig.builder()
                .minimumPurchaseAmount(new BigDecimal("100.00"))
                .spendingAmount(new BigDecimal("10.00"))
                .earnedPoints(1L)
                .redeemPoints(100L)
                .redeemValue(new BigDecimal("10.00"))
                .minimumRedeemPoints(minimumPoints)
                .earningEnabled(true)
                .redemptionEnabled(redemptionEnabled)
                .build();
    }
}
