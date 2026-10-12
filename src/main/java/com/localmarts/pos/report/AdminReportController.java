package com.localmarts.pos.report;

import com.localmarts.pos.report.dtos.SalesReportDTO;
import com.localmarts.pos.report.services.PaymentReportService;
import com.localmarts.pos.report.services.ProfitReportService;
import com.localmarts.pos.report.services.SalesReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequestMapping("admin/report")
@RequiredArgsConstructor
public class AdminReportController {

    private final PaymentReportService paymentReportService;
    private final SalesReportService salesReportService;
    private final ProfitReportService profitReportService;


    @GetMapping()
    public String index(Model model){
        return "admin/report/index";
    }

    @GetMapping("/sales")
    public String index(
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end,
            Model model
    ) {

        SalesReportDTO report =
                salesReportService.getReport(start, end);

        model.addAttribute("report", report);
        model.addAttribute("start", start);
        model.addAttribute("end", end);

        return "admin/report/sales";
    }

    @GetMapping("/profit")
    public String profit(
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end,
            Model model,
            Sort sort){
        model.addAttribute("summary",profitReportService.getProfitSummary(start,end));
        model.addAttribute("profitTrend",profitReportService.getProfitTrend(start,end));
        model.addAttribute("products",profitReportService.getProductPerformance(start,end,sort));
        model.addAttribute("start",start);
        model.addAttribute("end",end);
        model.addAttribute("sort",sort);
        return "admin/report/profit";
    }

    @GetMapping("/payment")
    public String payment(){
        return "admin/report/payment";
    }

}
