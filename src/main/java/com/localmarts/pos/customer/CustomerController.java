package com.localmarts.pos.customer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService customerService;
}
