package com.localmarts.pos.unit;

import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.unit.entity.Unit;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("unit")
public class UnitController {

    private final UnitService unitService;
    private final CurrentUserService currentUserService;

    public UnitController(UnitService unitService,
        CurrentUserService currentUserService) {
        this.unitService = unitService;
        this.currentUserService = currentUserService;
    }

    // Show unit list
    @GetMapping
    public String index(Model model) {
        model.addAttribute("units", unitService.findAll());
        return "admin/unit/index";
    }

    // Show create unit page
    @GetMapping("/create")
    public String create(Model model) {
        model.addAttribute("unit", new Unit());
        return "admin/unit/create";
    }

    // Save unit
    @PostMapping("/create")
    public String save(@ModelAttribute Unit unit, Authentication authentication, Model model) {
        try{
            unitService.create(unit, currentUserService.getCurrentUserId(authentication));
            return "redirect:/unit";
        }catch (RuntimeException e){
            model.addAttribute("unit",unit);
            model.addAttribute("error",e.getMessage());
            return "admin/unit/create";
        }
    }

    // Show edit unit page
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("unit", unitService.findById(id));
        return "admin/unit/edit";
    }

    // Update unit
    @PostMapping("/edit/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute Unit unit,
                         Authentication authentication,
                         Model model) {
        try {
            unitService.update(id, unit, currentUserService.getCurrentUserId(authentication));
            return "redirect:/unit";
        } catch (RuntimeException e) {
            unit.setId(id);
            model.addAttribute("unit", unit);
            model.addAttribute("error", e.getMessage());
            return "admin/unit/edit";
        }
    }

    // Soft delete unit
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id,
         Authentication authentication,
         RedirectAttributes redirectAttributes) {
        try {
            unitService.softDelete(
                    id,
                    currentUserService.getCurrentUserId(authentication)
            );
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }
        return "redirect:/unit";
    }
}
