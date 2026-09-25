package com.localmarts.pos.loyalty.controller;

import com.localmarts.pos.loyalty.entity.TransactionType;
import com.localmarts.pos.loyalty.entity.LoyaltyTransaction;
import com.localmarts.pos.loyalty.service.LoyaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("loyalty")
@RequiredArgsConstructor
public class LoyaltyController {
    private static final int PAGE_SIZE = 20;
    private final LoyaltyService loyaltyService;

    @GetMapping

    public String index(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        Page<LoyaltyTransaction> transactionPage =
                loyaltyService.filter(
                        type,
                        PageRequest.of(Math.max(page, 0), PAGE_SIZE)
                );
        model.addAttribute(
                "transactions",
                transactionPage.getContent()
        );
        model.addAttribute("page", transactionPage);
        model.addAttribute(
                "transactionTypes",
                TransactionType.values()
        );
        model.addAttribute(
                "selectedType",
                type
        );
        return "common/loyalty/index";
    }
}
