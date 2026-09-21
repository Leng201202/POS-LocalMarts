package com.localmarts.pos.user;

import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.user.dto.ResetPasswordRequest;
import com.localmarts.pos.user.entity.Role;
import com.localmarts.pos.user.entity.User;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

@Controller
@RequestMapping("users")
public class UserController {

    private final UserService userService;
    private final CurrentUserService currentUserService;

    public UserController(UserService userService,
        CurrentUserService currentUserService) {
            
        this.userService = userService;
        this.currentUserService = currentUserService;
    }

    @GetMapping()
    public String activeUsers(Model model) {
        model.addAttribute("users", userService.findAllActive());
        return "admin/user/index";
    }

    @GetMapping("/inactive")
    public String inactiveUsers(Model model) {
        model.addAttribute("users", userService.findAllInactive());
        return "admin/user/inactive";
    }

    @GetMapping("/create")
    public String createUser(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("roles", Role.values());
        return "admin/user/create";
    }

    @PostMapping("/save")
    public String saveUser(@ModelAttribute User user,
                        Model model,
                        Authentication authentication) {

        if (userService.existsByUsername(user.getUsername())) {
            model.addAttribute("user", user);
            model.addAttribute("roles", Role.values());
            model.addAttribute("error", "Username already exists.");
            return "admin/user/create";
        }

        userService.save(user, authentication.getName());
        return "redirect:/users";
    }

    @GetMapping("/edit/{id}")
    public String editUser(@PathVariable Long id, Model model) {
        model.addAttribute("user", userService.findById(id));
        model.addAttribute("roles", Role.values());
        return "admin/user/edit";
    }

    @PostMapping("/update/{id}")
    public String updateUser(@PathVariable Long id,
                            @ModelAttribute User user,
                            Authentication authentication) {

        userService.update(id, user, authentication.getName());
        return "redirect:/users";
    }
    @PostMapping("/activate/{id}")
    public String activateUser(@PathVariable Long id,
                            Authentication authentication) {

        userService.activate(id, authentication.getName());
        return "redirect:/users/inactive";
    }
    @PostMapping("/deactivate/{id}")
    public String deactivateUser(@PathVariable Long id,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            userService.deactivate(id, authentication.getName());
            redirectAttributes.addFlashAttribute(
                    "success",
                    "User deactivated successfully."
            );
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    e.getMessage()
            );
        }
        return "redirect:/users";
    }

    @GetMapping("/reset-password/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String showResetPasswordForm(
            @PathVariable Long id,
            Model model) {

        User user = userService.findById(id);

        model.addAttribute("user", user);
        model.addAttribute(
                "resetPasswordRequest",
                new ResetPasswordRequest()
        );

        return "admin/user/reset-password";
    }
    
    @PostMapping("/reset-password/{id}")
    public String resetPassword(
            @PathVariable Long id,
            @Valid @ModelAttribute("resetPasswordRequest")
            ResetPasswordRequest request,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        User user = userService.findById(id);

        if (bindingResult.hasErrors()) {
            model.addAttribute("user", user);
            return "admin/user/reset-password";
        }

        Long actorId =
                currentUserService.getCurrentUserId(authentication);

        try {
            userService.resetPassword(id, request, actorId);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Password reset successfully"
            );

            return "redirect:/users";

        } catch (IllegalArgumentException exception) {

            bindingResult.rejectValue(
                    "confirmPassword",
                    "password.mismatch",
                    exception.getMessage()
            );

            model.addAttribute("user", user);
            return "admin/user/reset-password";
        }
    }
}