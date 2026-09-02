package com.localmarts.pos.customer;

import com.localmarts.pos.customer.entity.Customer;
import com.localmarts.pos.loyalty.service.LoyaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/cashier/customers")
@PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
public class CashierCustomerController {

    private final CustomerService customerService;
    private final LoyaltyService loyaltyService;

    @GetMapping
    public String lookup(
            @RequestParam(required = false) String keyword,
            Model model
    ) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute(
                "customers",
                customerService.searchActiveForCashier(normalizedKeyword)
        );
        model.addAttribute("searchSubmitted", !normalizedKeyword.isBlank());
        return "cashier/customer/index";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Customer customer = customerService.findActiveById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Active customer not found"
                ));

        model.addAttribute("customer", customer);
        model.addAttribute(
                "transactions",
                loyaltyService.findRecentForCustomer(customer.getId())
        );
        return "cashier/customer/detail";
    }
}
