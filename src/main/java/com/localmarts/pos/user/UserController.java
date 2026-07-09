package com.localmarts.pos.user;

import com.localmarts.pos.user.entity.Role;
import com.localmarts.pos.user.entity.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userService.findAllActive());
        return "admin/user/index";
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

    @PostMapping("/users/deactivate/{id}")
    public String deactivateUser(@PathVariable Long id) {
        userService.deactivate(id);
        return "redirect:/users";
    }
}
