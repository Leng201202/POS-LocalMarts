package com.localmarts.pos.sale;

import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.customer.CustomerService;
import com.localmarts.pos.customer.entity.Customer;
import com.localmarts.pos.loyalty.service.LoyaltyConfigService;
import com.localmarts.pos.sale.dto.CreateSaleRequest;
import com.localmarts.pos.sale.dto.RefundReason;
import com.localmarts.pos.sale.dto.RefundRequest;
import com.localmarts.pos.sale.dto.ReturnCondition;
import com.localmarts.pos.sale.entity.PaymentMethod;
import com.localmarts.pos.sale.entity.Sale;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/cashier")
@PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
public class SaleController {

    private static final int PAGE_SIZE = 20;

    private final SaleService saleService;
    private final RefundService refundService;
    private final CustomerService customerService;
    private final LoyaltyConfigService loyaltyConfigService;
    private final CurrentUserService currentUserService;

    public SaleController(
            SaleService saleService,
            RefundService refundService,
            CustomerService customerService,
            LoyaltyConfigService loyaltyConfigService,
            CurrentUserService currentUserService) {

        this.saleService = saleService;
        this.refundService = refundService;
        this.customerService = customerService;
        this.loyaltyConfigService = loyaltyConfigService;
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

        return "cashier/report/receipt";
    }

    @GetMapping("/refunds")
    public String refundSearch(
            @RequestParam(required = false) String invoiceNo,
            @RequestParam(required = false) String customerPhone,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateTo,
            @RequestParam(required = false) BigDecimal totalAmount,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(defaultValue = "0") int page,
            Authentication authentication,
            Model model) {

        currentUserService.getCurrentUserId(authentication);

        try {
            Page<Sale> salesPage = refundService.searchEligibleSales(
                    invoiceNo,
                    customerPhone,
                    dateFrom,
                    dateTo,
                    totalAmount,
                    paymentMethod,
                    PageRequest.of(Math.max(page, 0), PAGE_SIZE)
            );
            model.addAttribute("sales", salesPage.getContent());
            model.addAttribute("page", salesPage);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            model.addAttribute("sales", List.of());
            model.addAttribute(
                    "page",
                    Page.empty(PageRequest.of(Math.max(page, 0), PAGE_SIZE))
            );
        }

        model.addAttribute("invoiceNo", invoiceNo);
        model.addAttribute("customerPhone", customerPhone);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("selectedPaymentMethod", paymentMethod);
        model.addAttribute("paymentMethods", PaymentMethod.values());

        return "cashier/refund/index";
    }

    @GetMapping("/sales/{saleId}/refund")
    public String refundForm(
            @PathVariable Long saleId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        currentUserService.getCurrentUserId(authentication);

        try {
            Sale sale = refundService.getRefundableSale(saleId);
            RefundRequest request = refundService.createRequestFor(sale);
            addRefundFormData(model, sale, request, null);
            return "cashier/refund/form";
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/cashier/refunds";
        }
    }

    @PostMapping("/sales/{saleId}/refund")
    public String processRefund(
            @PathVariable Long saleId,
            @RequestParam(defaultValue = "confirm") String action,
            @Valid @ModelAttribute("refundRequest") RefundRequest request,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        Long actorId = currentUserService.getCurrentUserId(authentication);

        if (bindingResult.hasErrors()) {
            return renderRefundFormAfterError(
                    saleId,
                    request,
                    null,
                    model,
                    redirectAttributes
            );
        }

        if ("preview".equals(action)) {
            try {
                RefundService.RefundPreview preview =
                        refundService.previewRefund(saleId, request);
                Sale sale = refundService.getRefundableSale(saleId);
                addRefundFormData(model, sale, request, preview);
                return "cashier/refund/form";
            } catch (RuntimeException exception) {
                return renderRefundFormAfterError(
                        saleId,
                        request,
                        exception.getMessage(),
                        model,
                        redirectAttributes
                );
            }
        }

        if (!"confirm".equals(action)) {
            return renderRefundFormAfterError(
                    saleId,
                    request,
                    "Invalid refund action",
                    model,
                    redirectAttributes
            );
        }

        try {
            RefundService.RefundOutcome outcome = refundService.processRefund(
                    saleId,
                    request,
                    actorId
            );

            redirectAttributes.addFlashAttribute(
                    "refundInvoiceNo",
                    outcome.invoiceNo()
            );
            redirectAttributes.addFlashAttribute(
                    "refundTotal",
                    outcome.totalRefundAmount()
            );
            redirectAttributes.addFlashAttribute(
                    "refundSaleStatus",
                    outcome.saleStatus()
            );
            redirectAttributes.addFlashAttribute(
                    "refundPaymentMethod",
                    outcome.paymentMethod()
            );
            redirectAttributes.addFlashAttribute(
                    "returnedUnitCount",
                    outcome.returnedUnitCount()
            );
            redirectAttributes.addFlashAttribute(
                    "restoredRedeemedPoints",
                    outcome.restoredRedeemedPoints()
            );
            redirectAttributes.addFlashAttribute(
                    "reversedEarnedPoints",
                    outcome.reversedEarnedPoints()
            );

            return "redirect:/cashier/sales/"
                    + outcome.saleId()
                    + "/refund/result";
        } catch (IllegalArgumentException exception) {
            return renderRefundFormAfterError(
                    saleId,
                    request,
                    exception.getMessage(),
                    model,
                    redirectAttributes
            );
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/cashier/refunds";
        }
    }

    @GetMapping("/sales/{saleId}/refund/result")
    public String refundResult(
            @PathVariable Long saleId,
            Authentication authentication,
            Model model) {

        currentUserService.getCurrentUserId(authentication);

        if (!model.containsAttribute("refundInvoiceNo")) {
            return "redirect:/cashier/refunds";
        }

        model.addAttribute("saleId", saleId);
        return "cashier/refund/result";
    }

    @GetMapping("/sales")
    public String salesHistory(
            @RequestParam(defaultValue = "0") int page,
            Authentication authentication,
            Model model) {

        Long cashierId =
                currentUserService.getCurrentUserId(authentication);

        Page<Sale> salesPage = saleService.getSalesHistory(
                cashierId,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE)
        );
        List<Sale> sales = salesPage.getContent();
        model.addAttribute("sales", sales);
        model.addAttribute("page", salesPage);
        model.addAttribute(
                "refundableSaleIds",
                refundService.refundableSaleIds(sales)
        );
        model.addAttribute(
                "expiredRefundSaleIds",
                refundService.expiredRefundSaleIds(sales)
        );

        return "cashier/sale/index";
    }

    private String renderRefundFormAfterError(
            Long saleId,
            RefundRequest request,
            String error,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Sale sale = refundService.getRefundableSale(saleId);
            if (error != null) {
                model.addAttribute("error", error);
            }
            addRefundFormData(model, sale, request, null);
            return "cashier/refund/form";
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/cashier/refunds";
        }
    }

    private void addRefundFormData(
            Model model,
            Sale sale,
            RefundRequest request,
            RefundService.RefundPreview refundPreview
    ) {
        model.addAttribute("sale", sale);
        model.addAttribute("refundRequest", request);
        model.addAttribute("returnConditions", ReturnCondition.values());
        model.addAttribute("refundReasons", RefundReason.values());
        model.addAttribute(
                "refundLineUiData",
                refundService.getRefundLineUiData(sale)
        );
        model.addAttribute("refundPreview", refundPreview);
        Map<Long, RefundService.RefundLinePreview> previewLines =
                refundPreview == null
                        ? Map.of()
                        : refundPreview.lines().stream().collect(
                                Collectors.toMap(
                                        RefundService.RefundLinePreview::saleItemId,
                                        Function.identity()
                                )
                        );
        model.addAttribute("refundPreviewLines", previewLines);
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
        model.addAttribute(
                "loyaltyConfig",
                resolvedCustomer == null
                        ? null
                        : loyaltyConfigService.getConfig()
        );
    }
}
