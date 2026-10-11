package com.localmarts.pos.customer;

import com.localmarts.pos.config.SecurityConfig;
import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.customer.entity.Customer;
import com.localmarts.pos.loyalty.entity.LoyaltyTransaction;
import com.localmarts.pos.loyalty.entity.TransactionType;
import com.localmarts.pos.loyalty.service.LoyaltyService;
import com.localmarts.pos.sale.entity.Sale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest({CashierCustomerController.class, CustomerController.class})
@Import(SecurityConfig.class)
class CashierCustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private LoyaltyService loyaltyService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @Test
    @WithMockUser(roles = "CASHIER")
    void cashierCanSearchAndSeeCurrentPointBalance() throws Exception {
        Customer customer = customer(7L, "Christina", "967611709", 11L);
        when(customerService.searchActiveForCashier("9676"))
                .thenReturn(List.of(customer));

        mockMvc.perform(get("/cashier/customers").param("keyword", " 9676 "))
                .andExpect(status().isOk())
                .andExpect(view().name("cashier/customer/index"))
                .andExpect(model().attribute("searchSubmitted", true))
                .andExpect(content().string(containsString("Christina")))
                .andExpect(content().string(containsString("967611709")))
                .andExpect(content().string(containsString("11 pts")));

        verify(customerService).searchActiveForCashier("9676");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessCashierCustomerLookup() throws Exception {
        when(customerService.searchActiveForCashier(""))
                .thenReturn(List.of());

        mockMvc.perform(get("/cashier/customers"))
                .andExpect(status().isOk())
                .andExpect(view().name("cashier/customer/index"));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void blankLookupDoesNotReturnAnUnboundedCustomerList() throws Exception {
        when(customerService.searchActiveForCashier(""))
                .thenReturn(List.of());

        mockMvc.perform(get("/cashier/customers"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("searchSubmitted", false))
                .andExpect(content().string(containsString(
                        "Search for a member to view their points."
                )));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void activeCustomerDetailShowsNewestLoyaltyActivityFirst() throws Exception {
        Customer customer = customer(7L, "Christina", "967611709", 11L);
        LoyaltyTransaction newest = transaction(
                2L,
                LocalDateTime.of(2026, 9, 1, 10, 0),
                TransactionType.REFUND_EARN_REVERSAL,
                -4L,
                15L,
                11L,
                "INV-NEW"
        );
        LoyaltyTransaction older = transaction(
                1L,
                LocalDateTime.of(2026, 8, 30, 10, 0),
                TransactionType.EARN,
                15L,
                0L,
                15L,
                "INV-OLD"
        );
        when(customerService.findActiveById(7L)).thenReturn(Optional.of(customer));
        when(loyaltyService.findRecentForCustomer(7L))
                .thenReturn(List.of(newest, older));

        String html = mockMvc.perform(get("/cashier/customers/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("cashier/customer/detail"))
                .andExpect(content().string(containsString("Current Point Balance")))
                .andExpect(content().string(containsString("REFUND EARN REVERSAL")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(html.indexOf("INV-NEW"))
                .isLessThan(html.indexOf("INV-OLD"));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void inactiveOrDeletedCustomerDetailIsNotAvailable() throws Exception {
        when(customerService.findActiveById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/cashier/customers/99"))
                .andExpect(status().isNotFound());

        verify(loyaltyService, never()).findRecentForCustomer(99L);
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void cashierCannotAccessAdminCustomerManagement() throws Exception {
        mockMvc.perform(get("/customer/create"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void cashierCustomerFeatureHasNoPointMutationEndpoint() throws Exception {
        mockMvc.perform(post("/cashier/customers/7").with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }

    private Customer customer(
            Long id,
            String name,
            String phone,
            Long pointBalance
    ) {
        Customer customer = Customer.builder()
                .id(id)
                .name(name)
                .phoneNumber(phone)
                .pointBalance(pointBalance)
                .build();
        customer.setActive(true);
        return customer;
    }

    private LoyaltyTransaction transaction(
            Long id,
            LocalDateTime timestamp,
            TransactionType type,
            Long points,
            Long before,
            Long after,
            String invoiceNo
    ) {
        return LoyaltyTransaction.builder()
                .id(id)
                .timestamp(timestamp)
                .type(type)
                .points(points)
                .beforePoint(before)
                .afterPoint(after)
                .sale(Sale.builder().invoiceNo(invoiceNo).build())
                .build();
    }
}
