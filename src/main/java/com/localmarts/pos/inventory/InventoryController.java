package com.localmarts.pos.inventory;

import com.localmarts.pos.inventory.entity.Inventory;
import com.localmarts.pos.inventory.entity.InventoryType;
import com.localmarts.pos.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final UserService userService;

    @GetMapping
    public String index(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) InventoryType type,
            @RequestParam(required = false) Long actorId,
            Model model
    ){
        List<Inventory> inventoryList=inventoryService.filter(keyword, type, actorId);
        model.addAttribute(
                "inventories",inventoryList
        );

        // Filter options
        model.addAttribute("users", userService.findAllActive());
        model.addAttribute("inventoryTypes", InventoryType.values());
        // Keep selected filter values
        model.addAttribute("selectedKeyword", keyword);
        model.addAttribute("selectedType", type);
        model.addAttribute("selectedActorId", actorId);

        // Add dash data
        model.addAttribute("stockInCount",
                inventoryList.stream()
                        .filter(i->i.getInventoryType()==InventoryType.STOCK_IN)
                        .count()
                );

        model.addAttribute("saleCount",
                inventoryList.stream()
                        .filter(i->i.getInventoryType()==InventoryType.SALE)
                        .count()
                );
        model.addAttribute("adjustmentCount",
                inventoryList.stream()
                        .filter(i->i.getInventoryType()==InventoryType.ADJUSTMENT)
                        .count()
                );
        return "common/inventory/index";
    }



    @GetMapping("/adjustment")
    public String adjustmentForm(Model model) {
        //model.addAttribute("products", productService.findAll());
        return "common/inventory/adjustment";
    }

    @GetMapping("/stock-in")
    public String stockInUploadPage() {
        return "common/inventory/stock-in";
    }

    @PostMapping("/stock-in/upload")
    public String uploadStockFile() {
        // read and validate file
        return "inventory/stock-in/review";
    }

    @PostMapping("/stock-in/confirm")
    public String confirmStockIn() {
        // update product stock and save inventory records
        return "redirect:/inventory";
    }

    @PostMapping("/stock-in/cancel")
    public String cancelStockIn() {
        return "redirect:/inventory";
    }
}
