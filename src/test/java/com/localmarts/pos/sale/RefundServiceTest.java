package com.localmarts.pos.sale;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.audit.entity.AuditDetails;
import com.localmarts.pos.audit.utils.AuditAction;
import com.localmarts.pos.customer.entity.Customer;
import com.localmarts.pos.inventory.service.InventoryService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final Long ACTOR_ID = 77L;
    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Bangkok");
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-31T10:00:00Z"),
            STORE_ZONE
    );

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private AuditService auditService;

    @Mock
    private LoyaltyService loyaltyService;

    @Mock
    private LoyaltyConfigService loyaltyConfigService;

    private RefundService refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(
                saleRepository,
                inventoryService,
                auditService,
                loyaltyService,
                loyaltyConfigService,
                FIXED_CLOCK
        );
    }

    @Test
    void processRefundCalculatesPartialAmountAndMapsDefectiveToDamaged() {
        Sale sale = saleWithTwoItems();
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));
        when(saleRepository.claimRefund(
                sale.getId(),
                SaleStatus.COMPLETED,
                SaleStatus.PARTIALLY_REFUNDED
        )).thenReturn(1);

        RefundRequest request = request(
                selected(sale.getItems().get(0), 2, ReturnCondition.SELLABLE),
                unselected(sale.getItems().get(1))
        );
        request.setNote("Customer returned two products together.");

        RefundService.RefundOutcome outcome = refundService.processRefund(
                sale.getId(),
                request,
                ACTOR_ID
        );

        assertThat(outcome.totalRefundAmount()).isEqualByComparingTo("6.67");
        assertThat(outcome.saleStatus()).isEqualTo(SaleStatus.PARTIALLY_REFUNDED);
        assertThat(outcome.returnedUnitCount()).isEqualTo(2);
        assertThat(sale.getSaleStatus()).isEqualTo(SaleStatus.PARTIALLY_REFUNDED);
        assertThat(sale.getItems().get(0).getRefundQuantity()).isEqualTo(2);
        assertThat(sale.getItems().get(0).getSaleStatus())
                .isEqualTo(SaleStatus.PARTIALLY_REFUNDED);
        assertThat(sale.getTotalAmount()).isEqualByComparingTo("15.00");
        assertThat(sale.getPaidAmount()).isEqualByComparingTo("20.00");

        verify(inventoryService).recordReturnedDamaged(
                101L,
                2,
                "Customer refund INV-TEST-001, sale item 11, returned quantity 2, reason DEFECTIVE, condition DAMAGED",
                ACTOR_ID
        );
        verify(inventoryService, never()).refund(
                any(), any(), any(), any()
        );
        verify(inventoryService, never()).recordReturnedExpired(
                any(), any(), any(), any()
        );

        ArgumentCaptor<List<AuditDetails>> detailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(auditService).logSuccess(
                eq(ACTOR_ID),
                eq(AuditAction.UPDATE),
                eq("Sale"),
                eq(sale.getId()),
                contains("INV-TEST-001"),
                detailsCaptor.capture()
        );
        assertThat(detailsCaptor.getValue())
                .extracting(AuditDetails::getFieldName)
                .contains(
                        "refundTotal",
                        "refundNote",
                        "saleStatus",
                        "item.11.saleItemId",
                        "item.11.refundQuantity",
                        "item.11.breakdown.0.refundReason",
                        "item.11.breakdown.0.returnCondition",
                        "item.11.cashRefundAmount"
                );
        assertThat(detailsCaptor.getValue())
                .noneMatch(detail -> "refundReason".equals(detail.getFieldName()));
    }

    @Test
    void processRefundUsesExactHistoricalNetTotalsForFullRefund() {
        Sale sale = saleWithTwoItems();
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));
        when(saleRepository.claimRefund(
                sale.getId(),
                SaleStatus.COMPLETED,
                SaleStatus.REFUNDED
        )).thenReturn(1);

        RefundRequest request = request(
                selected(
                        sale.getItems().get(0),
                        3,
                        ReturnCondition.DAMAGED,
                        RefundReason.WRONG_ITEM
                ),
                selected(
                        sale.getItems().get(1),
                        1,
                        ReturnCondition.EXPIRED,
                        RefundReason.EXPIRED
                )
        );

        RefundService.RefundOutcome outcome = refundService.processRefund(
                sale.getId(),
                request,
                ACTOR_ID
        );

        assertThat(outcome.totalRefundAmount()).isEqualByComparingTo("15.00");
        assertThat(outcome.saleStatus()).isEqualTo(SaleStatus.REFUNDED);
        assertThat(sale.getItems())
                .allMatch(item -> item.getSaleStatus() == SaleStatus.REFUNDED);
        verify(inventoryService).refund(
                eq(101L),
                eq(3),
                contains("reason WRONG_ITEM, condition SELLABLE"),
                eq(ACTOR_ID)
        );
        verify(inventoryService).recordReturnedExpired(
                eq(102L),
                eq(1),
                contains("reason EXPIRED, condition EXPIRED"),
                eq(ACTOR_ID)
        );

        ArgumentCaptor<List<AuditDetails>> detailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(auditService).logSuccess(
                eq(ACTOR_ID),
                eq(AuditAction.UPDATE),
                eq("Sale"),
                eq(sale.getId()),
                contains("item 11=WRONG_ITEM/SELLABLE, item 12=EXPIRED/EXPIRED"),
                detailsCaptor.capture()
        );
        assertThat(newData(detailsCaptor.getValue(), "item.11.breakdown.0.refundReason"))
                .isEqualTo("WRONG_ITEM");
        assertThat(newData(detailsCaptor.getValue(), "item.12.breakdown.0.refundReason"))
                .isEqualTo("EXPIRED");
        assertThat(newData(detailsCaptor.getValue(), "item.11.breakdown.0.returnCondition"))
                .isEqualTo("SELLABLE");
        assertThat(newData(detailsCaptor.getValue(), "item.12.breakdown.0.returnCondition"))
                .isEqualTo("EXPIRED");
    }

    @Test
    void selectedLineWithoutReasonIsRejected() {
        Sale sale = saleWithTwoItems();
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));
        RefundItemRequest line = selected(
                sale.getItems().get(0), 1, ReturnCondition.SELLABLE
        );
        line.getBreakdowns().getFirst().setReason(null);

        assertThatThrownBy(() -> refundService.previewRefund(
                sale.getId(), request(line)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refund reason is required")
                .hasMessageContaining("First");
    }

    @Test
    void otherReturnTypeWithoutConditionIsRejected() {
        Sale sale = saleWithTwoItems();
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));
        RefundItemRequest line = selected(
                sale.getItems().get(0), 1, ReturnCondition.SELLABLE
        );
        ReturnBreakdownRequest breakdown = line.getBreakdowns().getFirst();
        breakdown.setReason(RefundReason.OTHER);
        breakdown.setCondition(null);

        assertThatThrownBy(() -> refundService.previewRefund(
                sale.getId(), request(line)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Return condition is required")
                .hasMessageContaining("First");
    }

    @Test
    void otherReturnTypeUsesCashierSelectedCondition() {
        Sale sale = saleWithTwoItems();
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));
        when(saleRepository.claimRefund(
                sale.getId(),
                SaleStatus.COMPLETED,
                SaleStatus.PARTIALLY_REFUNDED
        )).thenReturn(1);

        RefundRequest request = request(selected(
                sale.getItems().getFirst(),
                1,
                ReturnCondition.EXPIRED,
                RefundReason.OTHER
        ));

        refundService.processRefund(sale.getId(), request, ACTOR_ID);

        verify(inventoryService).recordReturnedExpired(
                eq(101L),
                eq(1),
                contains("reason OTHER, condition EXPIRED"),
                eq(ACTOR_ID)
        );
        verify(auditService).logSuccess(
                eq(ACTOR_ID),
                eq(AuditAction.UPDATE),
                eq("Sale"),
                eq(sale.getId()),
                contains("item 11=OTHER/EXPIRED"),
                any()
        );
    }

    @Test
    void unselectedLineMayOmitReasonAndCondition() {
        Sale sale = saleWithTwoItems();
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));

        RefundService.RefundPreview preview = refundService.previewRefund(
                sale.getId(),
                request(
                        selected(sale.getItems().get(0), 1, ReturnCondition.SELLABLE),
                        unselected(sale.getItems().get(1))
                )
        );

        assertThat(preview.totalRefundAmount()).isEqualByComparingTo("3.33");
    }

    @Test
    void oneSaleItemAcceptsExpiredAndDamagedBreakdownsWithoutChangingMoney() {
        Sale sale = saleWithTwoItems();
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));
        when(saleRepository.claimRefund(
                sale.getId(),
                SaleStatus.COMPLETED,
                SaleStatus.PARTIALLY_REFUNDED
        )).thenReturn(1);
        RefundItemRequest line = selected(
                sale.getItems().getFirst(), 2, ReturnCondition.SELLABLE
        );
        line.setBreakdowns(List.of(
                breakdown(1, RefundReason.EXPIRED, ReturnCondition.EXPIRED),
                breakdown(1, RefundReason.DEFECTIVE, ReturnCondition.DAMAGED)
        ));

        RefundService.RefundOutcome outcome = refundService.processRefund(
                sale.getId(), request(line), ACTOR_ID
        );

        assertThat(outcome.totalRefundAmount()).isEqualByComparingTo("6.67");
        verify(inventoryService).recordReturnedExpired(
                eq(101L), eq(1), contains("reason EXPIRED, condition EXPIRED"), eq(ACTOR_ID)
        );
        verify(inventoryService).recordReturnedDamaged(
                eq(101L), eq(1), contains("reason DEFECTIVE, condition DAMAGED"), eq(ACTOR_ID)
        );
        ArgumentCaptor<List<AuditDetails>> detailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(auditService).logSuccess(
                eq(ACTOR_ID),
                eq(AuditAction.UPDATE),
                eq("Sale"),
                eq(sale.getId()),
                contains("item 11=EXPIRED/EXPIRED, item 11=DEFECTIVE/DAMAGED"),
                detailsCaptor.capture()
        );
        assertThat(newData(detailsCaptor.getValue(), "item.11.breakdown.0.quantity"))
                .isEqualTo("1");
        assertThat(newData(detailsCaptor.getValue(), "item.11.breakdown.0.refundReason"))
                .isEqualTo("EXPIRED");
        assertThat(newData(detailsCaptor.getValue(), "item.11.breakdown.1.refundReason"))
                .isEqualTo("DEFECTIVE");
    }

    @Test
    void multipleSellableBreakdownsAreProcessedIndependently() {
        Sale sale = saleWithTwoItems();
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));
        when(saleRepository.claimRefund(
                sale.getId(),
                SaleStatus.COMPLETED,
                SaleStatus.PARTIALLY_REFUNDED
        )).thenReturn(1);
        RefundItemRequest line = selected(
                sale.getItems().getFirst(), 2, ReturnCondition.SELLABLE
        );
        line.setBreakdowns(List.of(
                breakdown(1, RefundReason.WRONG_ITEM, ReturnCondition.SELLABLE),
                breakdown(1, RefundReason.CUSTOMER_CHANGED_MIND, ReturnCondition.SELLABLE)
        ));

        refundService.processRefund(sale.getId(), request(line), ACTOR_ID);

        ArgumentCaptor<Integer> quantities = ArgumentCaptor.forClass(Integer.class);
        verify(inventoryService, times(2)).refund(
                eq(101L), quantities.capture(), any(), eq(ACTOR_ID)
        );
        assertThat(quantities.getAllValues()).containsExactly(1, 1);
    }

    @Test
    void breakdownTotalBelowRefundQuantityIsRejected() {
        Sale sale = saleWithTwoItems();
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));
        RefundItemRequest line = selected(
                sale.getItems().getFirst(), 2, ReturnCondition.SELLABLE
        );
        line.setBreakdowns(List.of(
                breakdown(1, RefundReason.WRONG_ITEM, ReturnCondition.SELLABLE)
        ));

        assertThatThrownBy(() -> refundService.previewRefund(
                sale.getId(), request(line)
        )).hasMessage("Return breakdown quantities must equal the selected refund quantity.");
    }

    @Test
    void breakdownTotalAboveRefundQuantityIsRejected() {
        Sale sale = saleWithTwoItems();
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));
        RefundItemRequest line = selected(
                sale.getItems().getFirst(), 2, ReturnCondition.SELLABLE
        );
        line.setBreakdowns(List.of(
                breakdown(2, RefundReason.WRONG_ITEM, ReturnCondition.SELLABLE),
                breakdown(1, RefundReason.DEFECTIVE, ReturnCondition.DAMAGED)
        ));

        assertThatThrownBy(() -> refundService.previewRefund(
                sale.getId(), request(line)
        )).hasMessage("Return breakdown quantities must equal the selected refund quantity.");
    }

    @Test
    void processRefundRejectsSaleAfterSevenDayWindow() {
        Sale sale = saleWithTwoItems();
        sale.setTimestamp(sale.getTimestamp().minusDays(7).minusNanos(1));
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> refundService.processRefund(
                sale.getId(),
                request(selected(sale.getItems().get(0), 1, ReturnCondition.SELLABLE)),
                ACTOR_ID
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refund period expired");

        verify(saleRepository, never()).claimRefund(any(), any(), any());
        verify(auditService, never()).logSuccess(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void processRefundRejectsRedeemedSaleWithoutCustomerAsCorrupt() {
        Sale sale = saleWithTwoItems();
        sale.setPointRedeemed(20);
        sale.setPointDiscount(new BigDecimal("5.00"));
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> refundService.processRefund(
                sale.getId(),
                request(selected(sale.getItems().get(0), 1, ReturnCondition.SELLABLE)),
                ACTOR_ID
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no Customer")
                .hasMessageContaining("Manual review");

        verify(saleRepository, never()).claimRefund(any(), any(), any());
    }

    @Test
    void processRefundRejectsASecondOrConcurrentClaim() {
        Sale sale = saleWithTwoItems();
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));
        when(saleRepository.claimRefund(
                sale.getId(),
                SaleStatus.COMPLETED,
                SaleStatus.PARTIALLY_REFUNDED
        )).thenReturn(0);

        assertThatThrownBy(() -> refundService.processRefund(
                sale.getId(),
                request(selected(sale.getItems().get(0), 1, ReturnCondition.SELLABLE)),
                ACTOR_ID
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been refunded");

        verify(inventoryService, never()).refund(any(), any(), any(), any());
        verify(auditService, never()).logSuccess(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void processRefundReversesEarnedPointsFromRemainingHistoricalValue() {
        Sale sale = saleWithTwoItems();
        sale.setCustomer(Customer.builder().id(9L).pointBalance(12L).build());
        sale.setPointEarned(1);
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));
        when(saleRepository.claimRefund(
                sale.getId(),
                SaleStatus.COMPLETED,
                SaleStatus.PARTIALLY_REFUNDED
        )).thenReturn(1);
        when(loyaltyService.reverseEarnedPointsForRefund(
                sale.getCustomer(),
                sale,
                new BigDecimal("5.00")
        )).thenReturn(new LoyaltyService.LoyaltyRefundImpact(
                1L,
                0L,
                1L,
                12L,
                11L
        ));

        refundService.processRefund(
                sale.getId(),
                request(
                        selected(sale.getItems().get(0), 3, ReturnCondition.SELLABLE),
                        unselected(sale.getItems().get(1))
                ),
                ACTOR_ID
        );

        verify(loyaltyService).reverseEarnedPointsForRefund(
                sale.getCustomer(),
                sale,
                new BigDecimal("5.00")
        );
        ArgumentCaptor<List<AuditDetails>> detailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(auditService).logSuccess(
                eq(ACTOR_ID),
                eq(AuditAction.UPDATE),
                eq("Sale"),
                eq(sale.getId()),
                contains("INV-TEST-001"),
                detailsCaptor.capture()
        );
        assertThat(detailsCaptor.getValue())
                .extracting(AuditDetails::getFieldName)
                .contains(
                        "loyalty.remainingEligibleAmount",
                        "loyalty.originalEarnedPoints",
                        "loyalty.remainingAllowedPoints",
                        "loyalty.reversedPoints",
                        "loyalty.beforeBalance",
                        "loyalty.afterEarnReversalBalance",
                        "loyalty.afterRedeemRestoreBalance"
                );
    }

    @Test
    void walkInRefundDoesNotRunLoyaltyReversal() {
        Sale sale = saleWithTwoItems();
        sale.setPointEarned(10);
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));
        when(saleRepository.claimRefund(
                sale.getId(),
                SaleStatus.COMPLETED,
                SaleStatus.PARTIALLY_REFUNDED
        )).thenReturn(1);

        refundService.processRefund(
                sale.getId(),
                request(selected(sale.getItems().get(0), 1, ReturnCondition.SELLABLE)),
                ACTOR_ID
        );

        verify(loyaltyService, never()).reverseEarnedPointsForRefund(
                any(), any(), any()
        );
    }

    @Test
    void zeroPointSaleDoesNotRunLoyaltyReversal() {
        Sale sale = saleWithTwoItems();
        sale.setCustomer(Customer.builder().id(9L).pointBalance(0L).build());
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));
        when(saleRepository.claimRefund(
                sale.getId(),
                SaleStatus.COMPLETED,
                SaleStatus.PARTIALLY_REFUNDED
        )).thenReturn(1);

        refundService.processRefund(
                sale.getId(),
                request(selected(sale.getItems().get(0), 1, ReturnCondition.SELLABLE)),
                ACTOR_ID
        );

        verify(loyaltyService, never()).reverseEarnedPointsForRefund(
                any(), any(), any()
        );
    }

    @Test
    void loyaltyFailurePreventsRefundSuccessAudit() {
        Sale sale = saleWithTwoItems();
        sale.setCustomer(Customer.builder().id(9L).pointBalance(10L).build());
        sale.setPointEarned(1);
        when(saleRepository.findRefundDetailsById(sale.getId()))
                .thenReturn(Optional.of(sale));
        when(saleRepository.claimRefund(
                sale.getId(),
                SaleStatus.COMPLETED,
                SaleStatus.PARTIALLY_REFUNDED
        )).thenReturn(1);
        when(loyaltyService.reverseEarnedPointsForRefund(
                any(), any(), any()
        )).thenThrow(new IllegalStateException("Historical loyalty config unavailable"));

        assertThatThrownBy(() -> refundService.processRefund(
                sale.getId(),
                request(selected(sale.getItems().get(0), 1, ReturnCondition.SELLABLE)),
                ACTOR_ID
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Historical loyalty config unavailable");

        verify(auditService, never()).logSuccess(
                any(), any(), any(), any(), any(), any()
        );
    }

    private Sale saleWithTwoItems() {
        Sale sale = Sale.builder()
                .id(1L)
                .invoiceNo("INV-TEST-001")
                .subtotal(new BigDecimal("15.00"))
                .totalDiscount(new BigDecimal("0.00"))
                .pointDiscount(new BigDecimal("0.00"))
                .totalAmount(new BigDecimal("15.00"))
                .paidAmount(new BigDecimal("20.00"))
                .changeAmount(new BigDecimal("5.00"))
                .pointEarned(0)
                .pointRedeemed(0)
                .paymentMethod(PaymentMethod.CASH)
                .saleStatus(SaleStatus.COMPLETED)
                .timestamp(java.time.LocalDateTime.now(FIXED_CLOCK).minusDays(1))
                .build();

        sale.addItem(saleItem(11L, 101L, "First", 3, "10.00"));
        sale.addItem(saleItem(12L, 102L, "Second", 1, "5.00"));
        return sale;
    }

    private SaleItem saleItem(
            Long itemId,
            Long productId,
            String name,
            int quantity,
            String netTotal
    ) {
        return SaleItem.builder()
                .id(itemId)
                .product(Product.builder().id(productId).name(name).build())
                .productNameSnapshot(name)
                .quantity(quantity)
                .netTotal(new BigDecimal(netTotal))
                .saleStatus(SaleStatus.COMPLETED)
                .refundQuantity(0)
                .build();
    }

    private RefundRequest request(RefundItemRequest... items) {
        RefundRequest request = new RefundRequest();
        request.setItems(List.of(items));
        request.setPhysicalReturnConfirmed(true);
        return request;
    }

    private RefundItemRequest selected(
            SaleItem item,
            int quantity,
            ReturnCondition condition
    ) {
        return selected(item, quantity, condition, RefundReason.DEFECTIVE);
    }

    private RefundItemRequest selected(
            SaleItem item,
            int quantity,
            ReturnCondition condition,
            RefundReason reason
    ) {
        RefundItemRequest request = new RefundItemRequest();
        request.setSaleItemId(item.getId());
        request.setSelected(true);
        request.setQuantity(quantity);
        request.setBreakdowns(List.of(breakdown(quantity, reason, condition)));
        return request;
    }

    private RefundItemRequest unselected(SaleItem item) {
        RefundItemRequest request = new RefundItemRequest();
        request.setSaleItemId(item.getId());
        request.setQuantity(1);
        return request;
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

    private String newData(List<AuditDetails> details, String fieldName) {
        return details.stream()
                .filter(detail -> fieldName.equals(detail.getFieldName()))
                .map(AuditDetails::getNewData)
                .findFirst()
                .orElse(null);
    }
}
