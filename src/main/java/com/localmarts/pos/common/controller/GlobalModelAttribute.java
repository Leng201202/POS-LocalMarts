package com.localmarts.pos.common.controller;

import com.localmarts.pos.common.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttribute {

    private final CurrentUserService currentUserService;

    @ModelAttribute
    public void addGlobalAttributes(Model model, Authentication authentication){
        String dashboardUrl="/login";
        boolean showBatchStockIn = false;
        if(authentication!=null&&authentication.isAuthenticated()){
            boolean admin = currentUserService.isAdmin(authentication);
            showBatchStockIn = admin;
            dashboardUrl=admin
                    ? "/admin"
                    : "/cashier";

        }
        model.addAttribute("dashboardUrl",dashboardUrl);
        // Navigation visibility only; SecurityConfig remains the authorization authority.
        model.addAttribute("showBatchStockIn", showBatchStockIn);
        model.addAttribute("stockInUrl", showBatchStockIn
                ? "/inventory/stock-in/methods" : "/inventory/stock-in/manual");
    }
}
