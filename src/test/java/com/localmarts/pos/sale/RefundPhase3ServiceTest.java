package com.localmarts.pos.sale;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.customer.entity.Customer;
import com.localmarts.pos.inventory.service.InventoryService;
import com.localmarts.pos.loyalty.entity.LoyaltyConfig;
import com.localmarts.pos.loyalty.service.LoyaltyConfigService;
import com.localmarts.pos.loyalty.service.LoyaltyService;
import com.localmarts.pos.product.entity.Product;
import com.localmarts.pos.sale.dto.RefundItemRequest;
import com.localmarts.pos.sale.dto.RefundReason;
import com.localmarts.pos.sale.dto.RefundRequest;
import com.localmarts.pos.sale.dto.ReturnBreakdownRequest;
import com.localmarts.pos.sale.dto.ReturnCondition;
import com.localmarts.pos.sale.entity.PaymentMethod;
import com.localmarts.pos.sale.entity.Sale;
import com.localmarts.pos.sale.entity.SaleItem;
import com.localmarts.pos.sale.entity.SaleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundPhase3ServiceTest {

    private static final long ACTOR_ID = 77L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-31T10:00:00Z"),
            ZoneId.of("Asia/Bangkok")
    );

    @Mock private SaleRepository saleRepository;
    @Mock private InventoryService inventoryService;
    @Mock private AuditService auditService;
    @Mock private LoyaltyService loyaltyService;
    @Mock private LoyaltyConfigService loyaltyConfigService;

    private RefundService refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(
                saleRepository,
                inventoryService,
                auditService,
                loyaltyService,
                loyaltyConfigService,
                CLOCK
        );
    }

    @Test
    void returningThreeOfFiveRefundsRegularUnitsAndRestoresNoPoints() {
        Sale sale = redeemedSale(5, 2);
        prepare(sale, SaleStatus.PARTIALLY_REFUNDED);

        RefundService.RefundOutcome outcome = refundService.processRefund(
                sale.getId(), request(sale.getItems().getFirst(), 3), ACTOR_ID
        );

        assertThat(outcome.totalRefundAmount()).isEqualByComparingTo("75.00");
        assertThat(outcome.restoredRedeemedPoints()).isZero();
        verify(loyaltyService, never()).restoreRedeemedPointsForRefund(
                any(), any(), anyLong()
        );
    }

    @Test
    void returningFourOfFiveRefundsOneDiscountedUnitAndRestoresItsPoints() {
        Sale sale = redeemedSale(5, 2);
        prepare(sale, SaleStatus.PARTIALLY_REFUNDED);
        when(loyaltyService.restoreRedeemedPointsForRefund(
                sale.getCustomer(), sale, 25L
        )).thenReturn(new LoyaltyService.LoyaltyRestoreImpact(25L, 5L, 30L));

        RefundService.RefundOutcome outcome = refundService.processRefund(
                sale.getId(), request(sale.getItems().getFirst(), 4), ACTOR_ID
        );

        assertThat(outcome.totalRefundAmount()).isEqualByComparingTo("97.50");
        assertThat(outcome.restoredRedeemedPoints()).isEqualTo(25L);
    }

    @Test
    void mixedBreakdownsDoNotChangePhaseThreeMoneyOrRestoredPoints() {
        Sale sale = redeemedSale(5, 2);
        prepare(sale, SaleStatus.PARTIALLY_REFUNDED);
        when(loyaltyService.restoreRedeemedPointsForRefund(
                sale.getCustomer(), sale, 25L
        )).thenReturn(new LoyaltyService.LoyaltyRestoreImpact(25L, 5L, 30L));
        RefundItemRequest line = selected(sale.getItems().getFirst(), 4);
        line.setBreakdowns(List.of(
                breakdown(2, RefundReason.WRONG_ITEM, ReturnCondition.SELLABLE),
                breakdown(1, RefundReason.DEFECTIVE, ReturnCondition.DAMAGED),
                breakdown(1, RefundReason.EXPIRED, ReturnCondition.EXPIRED)
        ));
        RefundRequest request = new RefundRequest();
        request.setItems(List.of(line));
        request.setPhysicalReturnConfirmed(true);

        RefundService.RefundOutcome outcome = refundService.processRefund(
                sale.getId(), request, ACTOR_ID
        );

        assertThat(outcome.totalRefundAmount()).isEqualByComparingTo("97.50");
        assertThat(outcome.restoredRedeemedPoints()).isEqualTo(25L);
        verify(inventoryService).refund(anyLong(), org.mockito.ArgumentMatchers.eq(2), any(), anyLong());
        verify(inventoryService).recordReturnedDamaged(anyLong(), org.mockito.ArgumentMatchers.eq(1), any(), anyLong());
        verify(inventoryService).recordReturnedExpired(anyLong(), org.mockito.ArgumentMatchers.eq(1), any(), anyLong());
    }

    @Test
    void fullRedeemedLineUsesExactNetTotalAndRestoresAllPoints() {
        Sale sale = redeemedSale(5, 2);
        prepare(sale, SaleStatus.REFUNDED);
        when(loyaltyService.restoreRedeemedPointsForRefund(
                sale.getCustomer(), sale, 50L
        )).thenReturn(new LoyaltyService.LoyaltyRestoreImpact(50L, 5L, 55L));

        RefundService.RefundOutcome outcome = refundService.processRefund(
                sale.getId(), request(sale.getItems().getFirst(), 5), ACTOR_ID
        );

        assertThat(outcome.totalRefundAmount()).isEqualByComparingTo("120.00");
        assertThat(outcome.saleStatus()).isEqualTo(SaleStatus.REFUNDED);
        assertThat(outcome.restoredRedeemedPoints()).isEqualTo(50L);
    }

    @Test
    void allDiscountedLineRestoresPointsForEveryReturnedUnit() {
        Sale sale = redeemedSale(5, 5);
        preparePreview(sale);

        RefundService.RefundPreview preview = refundService.previewRefund(
                sale.getId(), request(sale.getItems().getFirst(), 2)
        );

        assertThat(preview.totalRefundAmount()).isEqualByComparingTo("45.00");
        assertThat(preview.restoredRedeemedPoints()).isEqualTo(50L);
        assertThat(preview.lines().getFirst().regularUnitsReturned()).isZero();
        assertThat(preview.lines().getFirst().discountedUnitsReturned()).isEqualTo(2);
    }

    @Test
    void returningOneOfFiveStillRefundsARegularPriceUnitFirst() {
        Sale sale = redeemedSale(5, 2);
        preparePreview(sale);

        RefundService.RefundPreview preview = refundService.previewRefund(
                sale.getId(), request(sale.getItems().getFirst(), 1)
        );

        assertThat(preview.totalRefundAmount()).isEqualByComparingTo("25.00");
        assertThat(preview.restoredRedeemedPoints()).isZero();
    }

    @Test
    void historicalConfigDeterminesRestoredPoints() {
        Sale sale = redeemedSale(5, 2);
        LoyaltyConfig historical = historicalConfig();
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));
        when(loyaltyConfigService.getSaleConfig(sale.getTimestamp()))
                .thenReturn(historical);

        RefundService.RefundPreview preview = refundService.previewRefund(
                sale.getId(), request(sale.getItems().getFirst(), 4)
        );

        assertThat(preview.restoredRedeemedPoints()).isEqualTo(25L);
        verify(loyaltyConfigService).getSaleConfig(sale.getTimestamp());
        verify(loyaltyService, never()).getCurrentConfig();
    }

    @Test
    void livePreviewDataUsesHistoricalLoyaltyAllocation() {
        Sale sale = redeemedSale(5, 2);
        when(loyaltyConfigService.getSaleConfig(sale.getTimestamp()))
                .thenReturn(historicalConfig());

        RefundService.RefundLineUiData uiData = refundService
                .getRefundLineUiData(sale)
                .get(sale.getItems().getFirst().getId());

        assertThat(uiData.originalQuantity()).isEqualTo(5);
        assertThat(uiData.discountedUnitsOriginal()).isEqualTo(2);
        assertThat(uiData.unitPrice()).isEqualByComparingTo("25.00");
        assertThat(uiData.unitDiscount()).isEqualByComparingTo("2.50");
        assertThat(uiData.netTotal()).isEqualByComparingTo("120.00");
        assertThat(uiData.pointsPerDiscountedUnit()).isEqualTo(25L);
    }

    @Test
    void inconsistentReconstructedPointTotalIsBlockedForManualReview() {
        Sale sale = redeemedSale(5, 2);
        sale.setPointRedeemed(49);
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));
        when(loyaltyConfigService.getSaleConfig(sale.getTimestamp()))
                .thenReturn(historicalConfig());

        assertThatThrownBy(() -> refundService.getRefundableSale(sale.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be reconstructed safely")
                .hasMessageContaining("Manual review");
    }

    @Test
    void ambiguousDiscountedQuantityIsBlockedForManualReview() {
        Sale sale = redeemedSale(5, 2);
        sale.getItems().getFirst().setDiscountAmount(new BigDecimal("3.00"));
        sale.setPointDiscount(new BigDecimal("3.00"));
        sale.setTotalDiscount(new BigDecimal("3.00"));
        sale.setTotalAmount(new BigDecimal("122.00"));
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> refundService.getRefundableSale(sale.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SaleItem cannot be reconstructed safely")
                .hasMessageContaining("Manual review");
    }

    @Test
    void redeemedSaleIsIncludedInRefundSearchAndHistoryEligibility() {
        Sale sale = redeemedSale(5, 2);
        when(saleRepository.searchRefundEligibleSales(
                any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(List.of(sale));
        when(loyaltyConfigService.getSaleConfig(sale.getTimestamp()))
                .thenReturn(historicalConfig());

        assertThat(refundService.searchEligibleSales(
                null, null, null, null, null, null
        )).containsExactly(sale);
        assertThat(refundService.refundableSaleIds(List.of(sale)))
                .containsExactly(sale.getId());
    }

    @Test
    void earnedReversalRunsBeforeRedeemedPointRestoration() {
        Sale sale = redeemedSale(5, 2);
        sale.setPointEarned(12);
        prepare(sale, SaleStatus.PARTIALLY_REFUNDED);
        when(loyaltyService.reverseEarnedPointsForRefund(
                sale.getCustomer(), sale, new BigDecimal("22.50")
        )).thenReturn(new LoyaltyService.LoyaltyRefundImpact(
                12L, 2L, 10L, 5L, -5L
        ));
        when(loyaltyService.restoreRedeemedPointsForRefund(
                sale.getCustomer(), sale, 25L
        )).thenReturn(new LoyaltyService.LoyaltyRestoreImpact(
                25L, -5L, 20L
        ));

        RefundService.RefundOutcome outcome = refundService.processRefund(
                sale.getId(), request(sale.getItems().getFirst(), 4), ACTOR_ID
        );

        assertThat(outcome.reversedEarnedPoints()).isEqualTo(10L);
        assertThat(outcome.restoredRedeemedPoints()).isEqualTo(25L);
        InOrder order = inOrder(loyaltyService);
        order.verify(loyaltyService).reverseEarnedPointsForRefund(
                sale.getCustomer(), sale, new BigDecimal("22.50")
        );
        order.verify(loyaltyService).restoreRedeemedPointsForRefund(
                sale.getCustomer(), sale, 25L
        );
    }

    @Test
    void regularFirstAllocationIsIndependentForMultipleSaleItems() {
        Sale sale = redeemedSale(5, 2);
        SaleItem chips = SaleItem.builder()
                .id(12L)
                .product(Product.builder().id(102L).name("Chips").build())
                .productNameSnapshot("Chips")
                .barcodeSnapshot("CHIPS")
                .quantity(2)
                .unitPrice(new BigDecimal("10.00"))
                .grossSubtotal(new BigDecimal("20.00"))
                .discountPercentSnapshot(new BigDecimal("10.00"))
                .discountAmount(new BigDecimal("1.00"))
                .netTotal(new BigDecimal("19.00"))
                .costPriceSnapshot(new BigDecimal("7.00"))
                .saleStatus(SaleStatus.COMPLETED)
                .refundQuantity(0)
                .build();
        sale.addItem(chips);
        sale.setSubtotal(new BigDecimal("145.00"));
        sale.setPointDiscount(new BigDecimal("6.00"));
        sale.setTotalDiscount(new BigDecimal("6.00"));
        sale.setTotalAmount(new BigDecimal("139.00"));
        sale.setPaidAmount(new BigDecimal("139.00"));
        sale.setPointRedeemed(60);
        preparePreview(sale);

        RefundRequest request = request(sale.getItems().getFirst(), 3);
        request.setItems(List.of(
                request.getItems().getFirst(),
                selected(chips, 2)
        ));

        RefundService.RefundPreview preview = refundService.previewRefund(
                sale.getId(), request
        );

        assertThat(preview.totalRefundAmount()).isEqualByComparingTo("94.00");
        assertThat(preview.restoredRedeemedPoints()).isEqualTo(10L);
        assertThat(preview.lines().get(0).discountedUnitsReturned()).isZero();
        assertThat(preview.lines().get(1).discountedUnitsReturned()).isEqualTo(1);
    }

    @Test
    void pointDiscountWithoutRedeemedPointsIsBlocked() {
        Sale sale = redeemedSale(5, 2);
        sale.setPointRedeemed(0);
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> refundService.getRefundableSale(sale.getId()))
                .hasMessageContaining("inconsistent")
                .hasMessageContaining("Manual review");
    }

    @Test
    void redeemedPointsWithoutPointDiscountAreBlocked() {
        Sale sale = redeemedSale(5, 2);
        sale.setPointDiscount(BigDecimal.ZERO);
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> refundService.getRefundableSale(sale.getId()))
                .hasMessageContaining("inconsistent")
                .hasMessageContaining("Manual review");
    }

    @Test
    void restorationFailurePreventsSuccessAudit() {
        Sale sale = redeemedSale(5, 2);
        prepare(sale, SaleStatus.PARTIALLY_REFUNDED);
        when(loyaltyService.restoreRedeemedPointsForRefund(
                sale.getCustomer(), sale, 25L
        )).thenThrow(new IllegalStateException("restoration failed"));

        assertThatThrownBy(() -> refundService.processRefund(
                sale.getId(), request(sale.getItems().getFirst(), 4), ACTOR_ID
        )).hasMessageContaining("restoration failed");

        verify(auditService, never()).logSuccess(
                any(), any(), any(), any(), any(), any()
        );
    }

    private void prepare(Sale sale, SaleStatus finalStatus) {
        preparePreview(sale);
        when(saleRepository.claimRefund(
                sale.getId(), SaleStatus.COMPLETED, finalStatus
        )).thenReturn(1);
    }

    private void preparePreview(Sale sale) {
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));
        when(loyaltyConfigService.getSaleConfig(sale.getTimestamp()))
                .thenReturn(historicalConfig());
    }

    private Sale redeemedSale(int quantity, int discountedUnits) {
        BigDecimal gross = new BigDecimal("25.00")
                .multiply(BigDecimal.valueOf(quantity));
        BigDecimal discount = new BigDecimal("2.50")
                .multiply(BigDecimal.valueOf(discountedUnits));
        BigDecimal net = gross.subtract(discount);
        Customer customer = Customer.builder()
                .id(9L)
                .name("Member")
                .phoneNumber("0899999999")
                .pointBalance(5L)
                .build();
        Sale sale = Sale.builder()
                .id(1L)
                .invoiceNo("INV-PHASE-3")
                .customer(customer)
                .subtotal(gross)
                .totalDiscount(discount)
                .pointDiscount(discount)
                .totalAmount(net)
                .paidAmount(net)
                .changeAmount(BigDecimal.ZERO)
                .pointEarned(0)
                .pointRedeemed(discountedUnits * 25)
                .paymentMethod(PaymentMethod.CASH)
                .saleStatus(SaleStatus.COMPLETED)
                .timestamp(LocalDateTime.now(CLOCK).minusDays(1))
                .build();
        sale.addItem(SaleItem.builder()
                .id(11L)
                .product(Product.builder().id(101L).name("Coke").build())
                .productNameSnapshot("Coke")
                .barcodeSnapshot("COKE")
                .quantity(quantity)
                .unitPrice(new BigDecimal("25.00"))
                .grossSubtotal(gross)
                .discountPercentSnapshot(new BigDecimal("10.00"))
                .discountAmount(discount)
                .netTotal(net)
                .costPriceSnapshot(new BigDecimal("20.00"))
                .saleStatus(SaleStatus.COMPLETED)
                .refundQuantity(0)
                .build());
        return sale;
    }

    private LoyaltyConfig historicalConfig() {
        return LoyaltyConfig.builder()
                .redeemPoints(100L)
                .redeemValue(new BigDecimal("10.00"))
                .redemptionEnabled(false)
                .build();
    }

    private RefundRequest request(SaleItem item, int quantity) {
        RefundRequest request = new RefundRequest();
        request.setItems(List.of(selected(item, quantity)));
        request.setPhysicalReturnConfirmed(true);
        return request;
    }

    private RefundItemRequest selected(SaleItem item, int quantity) {
        RefundItemRequest line = new RefundItemRequest();
        line.setSaleItemId(item.getId());
        line.setSelected(true);
        line.setQuantity(quantity);
        line.setBreakdowns(List.of(
                breakdown(quantity, RefundReason.DEFECTIVE, ReturnCondition.SELLABLE)
        ));
        return line;
    }

    private ReturnBreakdownRequest breakdown(
            int quantity,
            RefundReason reason,
            ReturnCondition condition
    ) {
        ReturnBreakdownRequest breakdown = new ReturnBreakdownRequest();
        breakdown.setQuantity(quantity);
        breakdown.setReason(reason);
        breakdown.setCondition(condition);
        return breakdown;
    }
}
