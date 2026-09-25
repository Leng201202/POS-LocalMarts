package com.localmarts.pos.customer;

import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.customer.entity.Customer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("customer")
public class CustomerController {
    private static final int PAGE_SIZE = 20;
    private final CustomerService customerService;
    private final CurrentUserService currentUserService;


    @GetMapping
    public String list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        Page<Customer> customerPage = customerService.filter(
                keyword,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE)
        );
        model.addAttribute(
                "customers",
                customerPage.getContent()
        );
        model.addAttribute("page", customerPage);
        model.addAttribute("keyword", keyword);
        return "common/customer/index";
    }

    @GetMapping("create")
    public String create(Model model){
        model.addAttribute("customer",new Customer());
        return "common/customer/create";
    }

    @PostMapping("/create")
    public String create(
            @ModelAttribute Customer customer,
            Authentication authentication
    ){
        Long actorId=currentUserService.getCurrentUserId(authentication);

        customerService.create(customer,actorId);

        return "redirect:/customer";

    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id,Model model){
        model.addAttribute("customer",customerService.findById(id));
        return "common/customer/edit";
    }

    @PostMapping("/{id}/edit")
    public String update(
            @PathVariable Long id,
            @ModelAttribute Customer customer,
            Authentication authentication
    ){
        Long actorId=currentUserService.getCurrentUserId(authentication);
        customerService.update(id,customer,actorId);

        return "redirect:/customer";
    }

    @PostMapping("/delete/{id}")
    public String delete(
            @PathVariable Long id,
            Authentication authentication
    ){
        Long actorId=currentUserService.getCurrentUserId(authentication);
        customerService.softDelete(id,actorId);
        return "redirect:/customer";
    }

}
