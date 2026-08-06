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
        if(authentication!=null&&authentication.isAuthenticated()){
            dashboardUrl=currentUserService.isAdmin(authentication)
                    ? "/admin"
                    : "/cashier";

        }
        model.addAttribute("dashboardUrl",dashboardUrl);
    }
}
