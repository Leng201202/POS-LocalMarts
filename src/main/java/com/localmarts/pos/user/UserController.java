package com.localmarts.pos.user;

import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.user.dto.ResetPasswordRequest;
import com.localmarts.pos.user.entity.Role;
import com.localmarts.pos.user.entity.User;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

@Controller
public class UserController {

    private final UserService userService;
    private final CurrentUserService currentUserService;

    public UserController(UserService userService,
        CurrentUserService currentUserService) {
            
        this.userService = userService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/users")
    public String activeUsers(Model model) {
        model.addAttribute("users", userService.findAllActive());
        return "admin/user/index";
    }

    @GetMapping("/users/inactive")
    public String inactiveUsers(Model model) {
        model.addAttribute("users", userService.findAllInactive());
        return "admin/user/inactive";
    }

    @GetMapping("/users/create")
    public String createUser(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("roles", Role.values());
        return "admin/user/create";
    }

    @PostMapping("/users/save")
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

    @GetMapping("/users/edit/{id}")
    public String editUser(@PathVariable Long id, Model model) {
        model.addAttribute("user", userService.findById(id));
        model.addAttribute("roles", Role.values());
        return "admin/user/edit";
    }

    @PostMapping("/users/update/{id}")
    public String updateUser(@PathVariable Long id,
                            @ModelAttribute User user,
                            Authentication authentication) {

        userService.update(id, user, authentication.getName());
        return "redirect:/users";
    }
    @PostMapping("/users/activate/{id}")
    public String activateUser(@PathVariable Long id,
                            Authentication authentication) {

        userService.activate(id, authentication.getName());
        return "redirect:/users/inactive";
    }
    @PostMapping("/users/deactivate/{id}")
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

    @GetMapping("/users/reset-password/{id}")
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
    
    @PostMapping("/users/reset-password/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String resetPassword(
            @PathVariable Long id,
            @Valid @ModelAttribute("resetPasswordRequest")
            ResetPasswordRequest request,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        User user = userService.findById(id);

        if (!request.getNewPassword()
                .equals(request.getConfirmPassword())) {

            bindingResult.rejectValue(
                    "confirmPassword",
                    "password.mismatch",
                    "Passwords do not match"
            );
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("user", user);
            return "admin/user/reset-password";
        }

        Long actorId =
                currentUserService.getCurrentUserId(authentication);

        userService.resetPassword(
                id,
                request.getNewPassword(),
                actorId
        );

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Password reset successfully for "
                        + user.getUsername()
        );

        return "redirect:/users";
    }
}
