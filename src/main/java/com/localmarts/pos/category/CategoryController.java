package com.localmarts.pos.category;

import com.localmarts.pos.category.entity.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("category")
public class CategoryController {
    private static final Long CURRENT_USER_ID = 1L;
    @Autowired
    private final CategoryService categoryService;
    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

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

    @PostMapping("create")
    public String save(@ModelAttribute Category category,Model model){
        try {
            categoryService.create(category,CURRENT_USER_ID);
            return "redirect:/category";
        }catch (RuntimeException e){
            model.addAttribute("category", category);
            model.addAttribute("error", e.getMessage());
            return "admin/category/create";
        }
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id,Model model){
        model.addAttribute("category",categoryService.findById(id));
        return "admin/category/edit";
    }

    @PostMapping("/edit/{id}")
    public String update(@PathVariable Long id,@ModelAttribute Category category,Model model){
        try{
            categoryService.update(id,category,CURRENT_USER_ID);
            return "redirect:/category";
        }catch (RuntimeException e){
            model.addAttribute("category",category);
            model.addAttribute("error",e.getMessage());
            return "admin/category/edit";
        }
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        categoryService.softDelete(id, CURRENT_USER_ID);
        return "redirect:/category";
    }



}
