package com.localmarts.pos.sale;

import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.config.SecurityConfig;
import com.localmarts.pos.customer.CustomerService;
import com.localmarts.pos.customer.entity.Customer;
import com.localmarts.pos.loyalty.entity.LoyaltyConfig;
import com.localmarts.pos.loyalty.service.LoyaltyConfigService;
import com.localmarts.pos.sale.entity.PaymentMethod;
import com.localmarts.pos.sale.entity.PaymentStatus;
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
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(SaleController.class)
@Import(SecurityConfig.class)
class SaleControllerLoyaltyViewTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private SaleService saleService;
    @MockitoBean private RefundService refundService;
    @MockitoBean private CustomerService customerService;
    @MockitoBean private LoyaltyConfigService loyaltyConfigService;
    @MockitoBean private CurrentUserService currentUserService;

    @Test
    @WithMockUser(roles = "CASHIER")
    void walkInCheckoutDoesNotRenderLoyaltyRedemptionControl() throws Exception {
        when(saleService.searchProducts("")).thenReturn(List.of());

        mockMvc.perform(get("/cashier/sale"))
                .andExpect(status().isOk())
                .andExpect(view().name("cashier/sale/new"))
                .andExpect(content().string(containsString("walk-in customer")))
                .andExpect(content().string(not(containsString("id=\"loyaltyCard\""))))
                .andExpect(content().string(not(containsString("id=\"useLoyaltyDiscount\""))));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void memberCheckoutRendersBalanceAndAuthoritativeConfigForPreview() throws Exception {
        Customer customer = Customer.builder()
                .id(3L)
                .name("Member One")
                .phoneNumber("0899999999")
                .pointBalance(120L)
                .build();
        customer.setActive(true);
        LoyaltyConfig config = LoyaltyConfig.builder()
                .redeemPoints(100L)
                .redeemValue(new BigDecimal("10.00"))
                .minimumRedeemPoints(50L)
                .redemptionEnabled(true)
                .build();

        when(saleService.searchProducts("")).thenReturn(List.of());
        when(customerService.findActiveByPhoneNumber("0899999999"))
                .thenReturn(Optional.of(customer));
        when(loyaltyConfigService.getConfig()).thenReturn(config);

        mockMvc.perform(get("/cashier/sale")
                        .param("customerPhone", "0899999999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Member One")))
                .andExpect(content().string(containsString("data-customer-points=\"120\"")))
                .andExpect(content().string(containsString("data-minimum-points=\"50\"")))
                .andExpect(content().string(containsString("id=\"useLoyaltyDiscount\"")));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void receiptShowsOnlyActualAppliedLoyaltyDiscount() throws Exception {
        Sale discountedSale = receiptSale(new BigDecimal("1.75"), 18);
        when(currentUserService.getCurrentUserId(org.mockito.ArgumentMatchers.any()))
                .thenReturn(7L);
        when(currentUserService.isAdmin(org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);
        when(saleService.findReceipt(9L, 7L, false)).thenReturn(discountedSale);

        mockMvc.perform(get("/cashier/sales/9/receipt"))
                .andExpect(status().isOk())
                .andExpect(view().name("cashier/receipt"))
                .andExpect(content().string(containsString("Points Redeemed")))
                .andExpect(content().string(containsString("18")))
                .andExpect(content().string(containsString("1.75")));
    }

    private Sale receiptSale(BigDecimal discount, int redeemedPoints) {
        Sale sale = Sale.builder()
                .id(9L)
                .invoiceNo("INV-LOYALTY")
                .cashier(User.builder().fullName("Cashier One").build())
                .subtotal(new BigDecimal("35.00"))
                .totalDiscount(discount)
                .pointDiscount(discount)
                .totalAmount(new BigDecimal("35.00").subtract(discount))
                .paidAmount(new BigDecimal("35.00").subtract(discount))
                .changeAmount(new BigDecimal("0.00"))
                .pointEarned(3)
                .pointRedeemed(redeemedPoints)
                .paymentMethod(PaymentMethod.CARD)
                .paymentStatus(PaymentStatus.PAID)
                .saleStatus(SaleStatus.COMPLETED)
                .timestamp(LocalDateTime.of(2026, 9, 3, 9, 0))
                .build();
        sale.addItem(SaleItem.builder()
                .productNameSnapshot("Dorito")
                .barcodeSnapshot("D-1")
                .quantity(1)
                .unitPrice(new BigDecimal("35.00"))
                .grossSubtotal(new BigDecimal("35.00"))
                .discountPercentSnapshot(new BigDecimal("5.00"))
                .discountAmount(discount)
                .netTotal(new BigDecimal("35.00").subtract(discount))
                .costPriceSnapshot(new BigDecimal("20.00"))
                .saleStatus(SaleStatus.COMPLETED)
                .refundQuantity(0)
                .build());
        return sale;
    }
}
