package com.localmarts.pos.unit;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/unit")
public class UnitController {

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    // Show unit list
    @GetMapping
    public String index(Model model) {
        model.addAttribute("units", unitService.findAllActive());
        return "admin/unit/index";
    }

    // Show create unit page
    @GetMapping("/create")
    public String create(Model model) {
        model.addAttribute("unit", new Unit());
        return "admin/unit/create";
    }

    // Save unit
    @PostMapping("/save")
    public String save(@ModelAttribute Unit unit) {
        unitService.save(unit);
        return "redirect:/admin/unit";
    }

    // Update unit
    @PostMapping("/update/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute Unit unit) {
        unitService.update(id, unit);
        return "redirect:/admin/unit";
    }

    // Soft delete unit
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        unitService.softDelete(id);
        return "redirect:/admin/unit";
    }
}