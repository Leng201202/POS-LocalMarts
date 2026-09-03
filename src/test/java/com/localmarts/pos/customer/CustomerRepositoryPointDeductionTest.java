package com.localmarts.pos.customer;

import com.localmarts.pos.customer.entity.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CustomerRepositoryPointDeductionTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void conditionalDeductionPreventsSpendingMoreThanCurrentBalance() {
        Customer customer = Customer.builder()
                .name("Concurrent Member")
                .phoneNumber("0899999999")
                .pointBalance(100L)
                .totalSpending(0L)
                .build();
        customer.setActive(true);
        customer.setCreatedBy(0L);
        customer = customerRepository.saveAndFlush(customer);

        int firstDeduction = customerRepository.deductPointsIfAvailable(
                customer.getId(),
                80L
        );
        int secondDeduction = customerRepository.deductPointsIfAvailable(
                customer.getId(),
                80L
        );

        assertThat(firstDeduction).isEqualTo(1);
        assertThat(secondDeduction).isZero();
        assertThat(customerRepository.findPointBalanceValueById(customer.getId()))
                .contains(20L);
    }
}
