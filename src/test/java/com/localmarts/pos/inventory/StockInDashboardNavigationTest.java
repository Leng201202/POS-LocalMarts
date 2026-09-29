package com.localmarts.pos.inventory;

import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.common.controller.HomeController;
import com.localmarts.pos.config.SecurityConfig;
import com.localmarts.pos.customer.CustomerService;
import com.localmarts.pos.loyalty.service.LoyaltyConfigService;
import com.localmarts.pos.sale.RefundService;
import com.localmarts.pos.sale.SaleController;
import com.localmarts.pos.sale.SaleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({HomeController.class, SaleController.class})
@Import(SecurityConfig.class)
class StockInDashboardNavigationTest {
    @Autowired MockMvc mvc;
    @MockitoBean CurrentUserService currentUser;
    @MockitoBean SaleService sales;
    @MockitoBean RefundService refunds;
    @MockitoBean CustomerService customers;
    @MockitoBean LoyaltyConfigService loyalty;

    @Test @WithMockUser(roles = "ADMIN")
    void adminHasOneStockInActionLeadingToSelection() throws Exception {
        when(currentUser.isAdmin(any())).thenReturn(true);
        mvc.perform(get("/admin")).andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        org.springframework.util.StringUtils.countOccurrencesOf(
                                result.getResponse().getContentAsString(), "href=\"/inventory/stock-in")).isEqualTo(1))
                .andExpect(content().string(containsString("href=\"/inventory/stock-in/methods\"")))
                .andExpect(content().string(not(containsString("Manual Stock In"))))
                .andExpect(content().string(not(containsString("Batch Stock In"))))
                .andExpect(content().string(containsString("Stock Adjustment")));
    }

    @Test @WithMockUser(roles = "CASHIER")
    void cashierHasOneStockInActionLeadingDirectlyToManual() throws Exception {
        when(currentUser.getCurrentUserId(any())).thenReturn(7L);
        when(sales.getTodayRevenue(7L)).thenReturn(BigDecimal.ZERO);
        when(sales.getRecentSales(7L)).thenReturn(List.of());
        mvc.perform(get("/cashier")).andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        org.springframework.util.StringUtils.countOccurrencesOf(
                                result.getResponse().getContentAsString(), "href=\"/inventory/stock-in")).isEqualTo(1))
                .andExpect(content().string(containsString("href=\"/inventory/stock-in/manual\"")))
                .andExpect(content().string(containsString("Receive stock for an existing product.")))
                .andExpect(content().string(not(containsString("Manual Stock In"))))
                .andExpect(content().string(not(containsString("Batch Stock In"))));
    }
}
