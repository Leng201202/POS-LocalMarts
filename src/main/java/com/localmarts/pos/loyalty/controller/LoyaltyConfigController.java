package com.localmarts.pos.loyalty.controller;

import com.localmarts.pos.loyalty.dtos.LoyaltyConfigRequest;
import com.localmarts.pos.loyalty.entity.LoyaltyConfig;
import com.localmarts.pos.loyalty.service.LoyaltyConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("loyalty-config")
@RequiredArgsConstructor
public class LoyaltyConfigController {

    private static final Long CURRENT_USER_ID = 1L;

    private final LoyaltyConfigService loyaltyConfigService;

    @GetMapping
    public String showConfig(Model model) {

        LoyaltyConfig config = loyaltyConfigService.getConfig();
        LoyaltyConfigRequest request = new LoyaltyConfigRequest(
                config.getMinimumPurchaseAmount(),
                config.getSpendingAmount(),
                config.getEarnedPoints(),
                config.getRedeemPoints(),
                config.getRedeemValue(),
                config.getMinimumRedeemPoints(),
                config.isEarningEnabled(),
                config.isRedemptionEnabled()
        );
        model.addAttribute("config", request);
        return "admin/loyalty/config";
    }

    @PostMapping
    public String updateConfig(
            @Valid @ModelAttribute("config")
            LoyaltyConfigRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "admin/loyalty/config";
        }

        try {
            loyaltyConfigService.update(
                    request,
                    CURRENT_USER_ID
            );
        } catch (IllegalArgumentException exception) {
            model.addAttribute(
                    "businessError",
                    exception.getMessage()
            );

            return "admin/loyalty/config";
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Loyalty configuration updated successfully."
        );

        return "redirect:/loyalty-config";
    }
}