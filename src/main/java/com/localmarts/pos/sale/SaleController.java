package com.localmarts.pos.sale;

import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.customer.CustomerService;
import com.localmarts.pos.customer.entity.Customer;
import com.localmarts.pos.sale.dto.CreateSaleRequest;
import com.localmarts.pos.sale.entity.PaymentMethod;
import com.localmarts.pos.sale.entity.Sale;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/cashier")
@PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
public class SaleController {

    private final SaleService saleService;
    private final CustomerService customerService;
    private final CurrentUserService currentUserService;

    public SaleController(
            SaleService saleService,
            CustomerService customerService,
            CurrentUserService currentUserService) {

        this.saleService = saleService;
        this.customerService = customerService;
        this.currentUserService = currentUserService;
    }

    @GetMapping({"", "/"})
    public String dashboard(
            Authentication authentication,
            Model model) {

        Long actorId =
                currentUserService.getCurrentUserId(authentication);

        model.addAttribute(
                "todaySaleCount",
                saleService.getTodayCompletedSaleCount(actorId)
        );
        model.addAttribute(
                "todayRevenue",
                saleService.getTodayRevenue(actorId)
        );
        model.addAttribute(
                "recentSales",
                saleService.getRecentSales(actorId)
        );

        return "cashier/index";
    }

    @GetMapping("/sale")
    public String salePage(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) String customerPhone,
            Model model) {

        CreateSaleRequest saleRequest =
                (CreateSaleRequest) model.getAttribute("saleRequest");

        if (saleRequest == null) {
            saleRequest = new CreateSaleRequest();
            model.addAttribute("saleRequest", saleRequest);
        }

        addSalePageData(model, query);
        addCustomerLookupData(model, saleRequest, customerPhone);

        return "cashier/sale/new";
    }

    @PostMapping("/sale/complete")
    public String completeSale(
            @Valid @ModelAttribute("saleRequest")
            CreateSaleRequest request,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            addSalePageData(model, "");
            addCustomerLookupData(model, request, null);
            return "cashier/sale/new";
        }

        try {
            Long actorId =
                    currentUserService.getCurrentUserId(authentication);

            Sale sale = saleService.checkout(request, actorId);

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Sale completed successfully"
            );

            return "redirect:/cashier/sales/"
                    + sale.getId()
                    + "/receipt";

        } catch (RuntimeException exception) {
            model.addAttribute("error", exception.getMessage());
            addSalePageData(model, "");
            addCustomerLookupData(model, request, null);
            return "cashier/sale/new";
        }
    }

    @GetMapping("/sales/{id}/receipt")
    public String receipt(
            @PathVariable Long id,
            Authentication authentication,
            Model model) {

        Long actorId =
                currentUserService.getCurrentUserId(authentication);
        boolean isAdmin =
                currentUserService.isAdmin(authentication);

        model.addAttribute(
                "sale",
                saleService.findReceipt(id, actorId, isAdmin)
        );

        return "cashier/receipt";
    }

    private void addSalePageData(Model model, String query) {
        model.addAttribute("query", query);
        model.addAttribute(
                "products",
                saleService.searchProducts(query)
        );
        model.addAttribute(
                "paymentMethods",
                PaymentMethod.values()
        );
    }

    private void addCustomerLookupData(
            Model model,
            CreateSaleRequest saleRequest,
            String customerPhone) {

        Customer resolvedCustomer = null;

        if (customerPhone != null && !customerPhone.isBlank()) {
            resolvedCustomer = customerService
                    .findActiveByPhoneNumber(customerPhone)
                    .orElse(null);

            saleRequest.setCustomerId(
                    resolvedCustomer == null
                            ? null
                            : resolvedCustomer.getId()
            );

        } else if (saleRequest.getCustomerId() != null) {
            resolvedCustomer = customerService
                    .findActiveById(saleRequest.getCustomerId())
                    .orElse(null);
        }

        model.addAttribute(
                "customerPhone",
                resolvedCustomer != null
                        ? resolvedCustomer.getPhoneNumber()
                        : customerPhone
        );
        model.addAttribute("resolvedCustomer", resolvedCustomer);
    }
}
