package com.localmarts.pos.product;

import com.localmarts.pos.category.CategoryService;
import com.localmarts.pos.product.entity.Product;
import com.localmarts.pos.unit.UnitService;
import com.localmarts.pos.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final UnitService unitService;
    private final UserRepository userRepository;

    public ProductController(
            ProductService productService,
            CategoryService categoryService,
            UnitService unitService,
            UserRepository userRepository) {

        this.productService = productService;
        this.categoryService = categoryService;
        this.unitService = unitService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("products", productService.findAll());
        return "admin/product/index";
    }

    @GetMapping("/create")
    public String create(Model model) {
        model.addAttribute("product", new Product());
        addDropdownData(model);

        return "admin/product/create";
    }

    @PostMapping("/create")
    public String save(
            @ModelAttribute Product product,
            Authentication authentication,
            Model model) {

        try {
            Long actorId = getCurrentUserId(authentication);
            productService.create(product, actorId);

            return "redirect:/products";

        } catch (RuntimeException e) {
            model.addAttribute("product", product);
            model.addAttribute("error", e.getMessage());
            addDropdownData(model);

            return "admin/product/create";
        }
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("product", productService.findById(id));
        addDropdownData(model);

        return "admin/product/edit";
    }

    @PostMapping("/edit/{id}")
    public String update(
            @PathVariable Long id,
            @ModelAttribute Product product,
            Authentication authentication,
            Model model) {

        try {
            Long actorId = getCurrentUserId(authentication);
            productService.update(id, product, actorId);

            return "redirect:/products";

        } catch (RuntimeException e) {
            product.setId(id);

            model.addAttribute("product", product);
            model.addAttribute("error", e.getMessage());
            addDropdownData(model);

            return "admin/product/edit";
        }
    }

    @PostMapping("/delete/{id}")
    public String delete(
            @PathVariable Long id,
            Authentication authentication) {

        Long actorId = getCurrentUserId(authentication);
        productService.softDelete(id, actorId);

        return "redirect:/products";
    }

    private void addDropdownData(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("units", unitService.findAll());
    }

    private Long getCurrentUserId(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new RuntimeException("Logged-in user not found"))
                .getId();
    }
}