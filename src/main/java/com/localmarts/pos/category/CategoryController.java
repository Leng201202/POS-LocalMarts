package com.localmarts.pos.category;

import com.localmarts.pos.category.entity.Category;
import com.localmarts.pos.common.CurrentUserService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final CurrentUserService currentUserService;


    @GetMapping()
    public String list(Model model){
        model.addAttribute("categories",categoryService.findAll());
        return "admin/category/index";
    }

    @GetMapping("create")
    public String create(Model model){
        model.addAttribute("category",new Category());
        return "admin/category/create";
    }

    @PostMapping("/create")
    public String create(
            @ModelAttribute Category category,
            Authentication authentication) {

        Long actorId =
                currentUserService.getCurrentUserId(authentication);

        categoryService.create(category, actorId);

        return "redirect:/category";
    }
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id,Model model){
        model.addAttribute("category",categoryService.findById(id));
        return "admin/category/edit";
    }

    @PostMapping("/{id}/edit")
    public String update(
            @PathVariable Long id,
            @ModelAttribute Category category,
            Authentication authentication) {

        Long actorId =
                currentUserService.getCurrentUserId(authentication);

        categoryService.update(id, category, actorId);

        return "redirect:/category";
    }

    @PostMapping("/delete/{id}")
    public String delete(
            @PathVariable Long id,
            Authentication authentication) {

        Long actorId =
                currentUserService.getCurrentUserId(authentication);

        categoryService.softDelete(id, actorId);
        return "redirect:/category";
    }
}
