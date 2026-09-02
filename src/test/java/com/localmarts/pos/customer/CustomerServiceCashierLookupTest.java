package com.localmarts.pos.customer;

import com.localmarts.pos.audit.AuditService;
import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.customer.entity.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceCashierLookupTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private AuditService auditService;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(
                customerRepository,
                currentUserService,
                auditService
        );
    }

    @Test
    void activeCustomerSearchTrimsNameOrPhoneKeyword() {
        Customer customer = Customer.builder()
                .id(1L)
                .name("Christina")
                .phoneNumber("967611709")
                .pointBalance(11L)
                .build();
        when(customerRepository.searchActiveForCashier("Christ"))
                .thenReturn(List.of(customer));

        List<Customer> result = customerService.searchActiveForCashier(
                "  Christ  "
        );

        assertThat(result).containsExactly(customer);
        verify(customerRepository).searchActiveForCashier("Christ");
    }

    @Test
    void blankSearchDoesNotLoadCustomers() {
        assertThat(customerService.searchActiveForCashier("  ")).isEmpty();
        verify(customerRepository, never()).searchActiveForCashier(
                org.mockito.ArgumentMatchers.anyString()
        );
    }
}
