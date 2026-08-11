package com.localmarts.pos.loyalty.controller;

import com.localmarts.pos.loyalty.entity.TransactionType;
import com.localmarts.pos.loyalty.service.LoyaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("loyalty")
@RequiredArgsConstructor
public class LoyaltyController {
    private final LoyaltyService loyaltyService;

    @GetMapping

    public String index(
            @RequestParam(required = false) TransactionType type,
            Model model
    ) {
        model.addAttribute(
                "transactions",
                loyaltyService.filter(type)
        );
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
