package com.localmarts.pos.unit;

import com.localmarts.pos.unit.entity.Unit;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("unit")
public class UnitController {

    private static final Long CURRENT_USER_ID = 1L;

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
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
    public String save(@ModelAttribute Unit unit, Model model) {
        try{
            unitService.create(unit, CURRENT_USER_ID);
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
                         Model model) {
        try {
            unitService.update(id, unit, CURRENT_USER_ID);
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
    public String delete(@PathVariable Long id) {
        unitService.softDelete(id, CURRENT_USER_ID);
        return "redirect:/unit";
    }
}
