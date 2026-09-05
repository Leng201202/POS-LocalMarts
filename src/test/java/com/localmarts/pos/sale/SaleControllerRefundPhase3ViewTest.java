package com.localmarts.pos.sale;

import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.config.SecurityConfig;
import com.localmarts.pos.customer.CustomerService;
import com.localmarts.pos.customer.entity.Customer;
import com.localmarts.pos.loyalty.service.LoyaltyConfigService;
import com.localmarts.pos.product.entity.Product;
import com.localmarts.pos.sale.dto.RefundItemRequest;
import com.localmarts.pos.sale.dto.RefundRequest;
import com.localmarts.pos.sale.dto.ReturnBreakdownRequest;
import com.localmarts.pos.sale.entity.PaymentMethod;
import com.localmarts.pos.sale.entity.Sale;
import com.localmarts.pos.sale.entity.SaleItem;
import com.localmarts.pos.sale.entity.SaleStatus;
import com.localmarts.pos.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(SaleController.class)
@Import(SecurityConfig.class)
class SaleControllerRefundPhase3ViewTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SaleService saleService;
    @MockitoBean private RefundService refundService;
    @MockitoBean private CustomerService customerService;
    @MockitoBean private LoyaltyConfigService loyaltyConfigService;
    @MockitoBean private CurrentUserService currentUserService;

    @Test
    @WithMockUser(roles = "CASHIER")
    void refundFormShowsHistoricalDiscountedQuantity() throws Exception {
        Sale sale = redeemedSale(PaymentMethod.CASH);
        when(currentUserService.getCurrentUserId(any())).thenReturn(7L);
        when(refundService.getRefundableSale(1L)).thenReturn(sale);
        when(refundService.createRequestFor(sale)).thenReturn(refundRequest(sale));
        when(refundService.getRefundLineUiData(sale)).thenReturn(Map.of(
                11L,
                new RefundService.RefundLineUiData(
                        5,
                        2,
                        new BigDecimal("25.00"),
                        new BigDecimal("2.50"),
                        new BigDecimal("120.00"),
                        25L
                )
        ));

        mockMvc.perform(get("/cashier/sales/1/refund"))
                .andExpect(status().isOk())
                .andExpect(view().name("cashier/refund/form"))
                .andExpect(content().string(containsString("applied to")))
                .andExpect(content().string(containsString("of <span>5</span> units")))
                .andExpect(content().string(containsString("Original line total paid")))
                .andExpect(content().string(containsString(
                        "name=\"items[0].breakdowns[0].reason\""
                )))
                .andExpect(content().string(containsString("Return Breakdown")))
                .andExpect(content().string(containsString("Return Reason")))
                .andExpect(content().string(containsString("Add another reason")))
                .andExpect(content().string(containsString(
                        "If returned units have different reasons, split the refund quantity into separate rows."
                )))
                .andExpect(content().string(containsString(
                        "Reduce a row quantity to add another reason."
                )))
                .andExpect(content().string(containsString("still unassigned.")))
                .andExpect(content().string(containsString("EXPIRED: 'EXPIRED'")))
                .andExpect(content().string(containsString("DEFECTIVE: 'DAMAGED'")))
                .andExpect(content().string(containsString("WRONG_ITEM: 'SELLABLE'")))
                .andExpect(content().string(containsString(
                        "CUSTOMER_CHANGED_MIND: 'SELLABLE'"
                )))
                .andExpect(content().string(containsString("reason.value === 'OTHER'")))
                .andExpect(content().string(containsString("breakdown-condition-group d-none")))
                .andExpect(content().string(not(containsString("Calculate Refund"))))
                .andExpect(content().string(containsString("live-cash-refund")))
                .andExpect(content().string(containsString("data-original-quantity=\"5\"")))
                .andExpect(content().string(containsString("data-discounted-units=\"2\"")))
                .andExpect(content().string(containsString("data-unit-discount=\"2.50\"")))
                .andExpect(content().string(containsString(
                        "data-points-per-discounted-unit=\"25\""
                )))
                .andExpect(content().string(containsString("Overall Refund Note (Optional)")))
                .andExpect(content().string(not(containsString("name=\"reason\""))));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void cashRefundResultShowsMoneyAndBothPointMovements() throws Exception {
        when(currentUserService.getCurrentUserId(any())).thenReturn(7L);

        mockMvc.perform(get("/cashier/sales/1/refund/result")
                        .flashAttr("refundInvoiceNo", "INV-PHASE-3")
                        .flashAttr("refundTotal", new BigDecimal("97.50"))
                        .flashAttr("refundSaleStatus", SaleStatus.PARTIALLY_REFUNDED)
                        .flashAttr("refundPaymentMethod", PaymentMethod.CASH)
                        .flashAttr("returnedUnitCount", 4)
                        .flashAttr("restoredRedeemedPoints", 25L)
                        .flashAttr("reversedEarnedPoints", 10L))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Money Returned")))
                .andExpect(content().string(containsString("Redeemed Points Restored")))
                .andExpect(content().string(containsString("Earned Points Reversed")))
                .andExpect(content().string(containsString("97.50")))
                .andExpect(content().string(containsString("physically return")));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void redeemedSaleReceivesRefundActionInSalesHistory() throws Exception {
        Sale sale = redeemedSale(PaymentMethod.CARD);
        when(currentUserService.getCurrentUserId(any())).thenReturn(7L);
        when(saleService.getSalesHistory(7L)).thenReturn(List.of(sale));
        when(refundService.refundableSaleIds(List.of(sale))).thenReturn(Set.of(1L));
        when(refundService.expiredRefundSaleIds(List.of(sale))).thenReturn(Set.of());

        mockMvc.perform(get("/cashier/sales"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/cashier/sales/1/refund")))
                .andExpect(content().string(containsString("Refund")));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void cardRefundResultStatesThatGatewayReversalIsExternal() throws Exception {
        when(currentUserService.getCurrentUserId(any())).thenReturn(7L);

        mockMvc.perform(get("/cashier/sales/1/refund/result")
                        .flashAttr("refundInvoiceNo", "INV-PHASE-3")
                        .flashAttr("refundTotal", new BigDecimal("120.00"))
                        .flashAttr("refundSaleStatus", SaleStatus.REFUNDED)
                        .flashAttr("refundPaymentMethod", PaymentMethod.CARD)
                        .flashAttr("returnedUnitCount", 5)
                        .flashAttr("restoredRedeemedPoints", 50L)
                        .flashAttr("reversedEarnedPoints", 12L))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "external payment reversal must be handled separately"
                )));
    }

    private Sale redeemedSale(PaymentMethod method) {
        Sale sale = Sale.builder()
                .id(1L)
                .invoiceNo("INV-PHASE-3")
                .cashier(User.builder().fullName("Cashier").build())
                .customer(Customer.builder().name("Member").build())
                .subtotal(new BigDecimal("125.00"))
                .totalDiscount(new BigDecimal("5.00"))
                .pointDiscount(new BigDecimal("5.00"))
                .totalAmount(new BigDecimal("120.00"))
                .paidAmount(new BigDecimal("120.00"))
                .changeAmount(BigDecimal.ZERO)
                .pointEarned(12)
                .pointRedeemed(50)
                .paymentMethod(method)
                .saleStatus(SaleStatus.COMPLETED)
                .timestamp(LocalDateTime.of(2026, 8, 31, 10, 0))
                .build();
        sale.addItem(SaleItem.builder()
                .id(11L)
                .product(Product.builder().id(101L).build())
                .productNameSnapshot("Coke")
                .barcodeSnapshot("COKE")
                .quantity(5)
                .unitPrice(new BigDecimal("25.00"))
                .grossSubtotal(new BigDecimal("125.00"))
                .discountPercentSnapshot(new BigDecimal("10.00"))
                .discountAmount(new BigDecimal("5.00"))
                .netTotal(new BigDecimal("120.00"))
                .costPriceSnapshot(new BigDecimal("20.00"))
                .saleStatus(SaleStatus.COMPLETED)
                .refundQuantity(0)
                .build());
        return sale;
    }

    private RefundRequest refundRequest(Sale sale) {
        ReturnBreakdownRequest breakdown = new ReturnBreakdownRequest();
        breakdown.setQuantity(1);
        RefundItemRequest item = new RefundItemRequest();
        item.setSaleItemId(sale.getItems().getFirst().getId());
        item.setQuantity(1);
        item.setBreakdowns(List.of(breakdown));
        RefundRequest request = new RefundRequest();
        request.setItems(List.of(item));
        return request;
    }
}
