package com.localmarts.pos.user;

import com.localmarts.pos.user.entity.Role;
import com.localmarts.pos.user.entity.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.Authentication;

@Controller
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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
    public String saveUser(@ModelAttribute User user, Model model) {
        if (userService.existsByUsername(user.getUsername())) {
            model.addAttribute("user", user);
            model.addAttribute("roles", Role.values());
            model.addAttribute("error", "Username already exists.");
            return "admin/user/create";
        }

        userService.save(user);
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
                            @ModelAttribute User user) {
        userService.update(id, user);
        return "redirect:/users";
    }

    @PostMapping("/users/activate/{id}")
    public String activateUser(@PathVariable Long id) {
        userService.activate(id);
        return "redirect:/users";
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
}
