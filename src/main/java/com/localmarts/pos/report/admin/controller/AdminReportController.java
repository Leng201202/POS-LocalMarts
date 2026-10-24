package com.localmarts.pos.report.admin.controller;

import com.localmarts.pos.report.common.ReportDataException;
import com.localmarts.pos.report.admin.dto.PaymentAnalysisDTO;
import com.localmarts.pos.report.admin.dto.ProfitTrendDTO;
import com.localmarts.pos.report.admin.dto.SalesReportDTO;
import com.localmarts.pos.report.admin.model.AdminReportPeriod;
import com.localmarts.pos.report.admin.service.AdminReportPdfService;
import com.localmarts.pos.report.admin.service.PaymentReportService;
import com.localmarts.pos.report.admin.service.ProfitReportService;
import com.localmarts.pos.report.admin.service.ReportExportService;
import com.localmarts.pos.report.admin.service.SalesReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;

@Controller
@RequestMapping("/admin/report")
@RequiredArgsConstructor
public class AdminReportController {
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PaymentReportService paymentReportService;
    private final SalesReportService salesReportService;
    private final ProfitReportService profitReportService;
    private final ReportExportService reportExportService;
    private final AdminReportPdfService adminReportPdfService;

    @GetMapping
    public String index(Model model) {
        LocalDate today = salesReportService.today();
        SalesReportDTO sales = salesReportService.getReport(today, today);
        model.addAttribute("today", today);
        model.addAttribute("monthStart", today.withDayOfMonth(1));
        model.addAttribute("todaySales", sales);
        model.addAttribute("todayProfit", profitReportService.getProfitSummary(today, today));
        return "admin/report/index";
    }

    @GetMapping("/sales")
    public String sales(
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end,
            Model model
    ) {
        AdminReportPeriod period = resolvePeriod(start, end, model);
        SalesReportDTO report = salesReportService.getReport(period.startDate(), period.endDate());
        addPeriod(model, period);
        model.addAttribute("report", report);
        return "admin/report/sales";
    }

    @GetMapping("/sales/export")
    public ResponseEntity<byte[]> exportSales(
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end,
            @RequestParam(required = false) Long cashierId
    ) {
        try {
            ReportExportService.GeneratedExcel export = reportExportService.exportSales(start, end, cashierId);
            return excelResponse(export);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCombined(
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end,
            @RequestParam(defaultValue = "xlsx") String format
    ) {
        try {
            // One endpoint keeps the selected period identical for either download format.
            if ("pdf".equalsIgnoreCase(format)) {
                AdminReportPdfService.GeneratedPdf export = adminReportPdfService.exportCombined(start, end);
                return fileResponse(export.filename(), export.content(), MediaType.APPLICATION_PDF);
            }
            if ("xlsx".equalsIgnoreCase(format) || "excel".equalsIgnoreCase(format)) {
                return excelResponse(reportExportService.exportCombined(start, end));
            }
            throw new IllegalArgumentException("Export format must be xlsx or pdf.");
        } catch (ReportDataException exception) {
            throw new ResponseStatusException(UNPROCESSABLE_CONTENT, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/profit")
    public String profit(
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end,
            @RequestParam(defaultValue = "profit") String sort,
            Model model
    ) {
        AdminReportPeriod period = resolvePeriod(start, end, model);
        String selectedSort = "units".equalsIgnoreCase(sort) ? "units" : "profit";
        List<ProfitTrendDTO> trend = profitReportService.getProfitTrend(period.startDate(), period.endDate());
        addPeriod(model, period);
        model.addAttribute("summary", profitReportService.getProfitSummary(period.startDate(), period.endDate()));
        model.addAttribute("profitTrend", trend);
        model.addAttribute("trendLabels", trend.stream().map(ProfitTrendDTO::label).toList());
        model.addAttribute("trendValues", trend.stream().map(ProfitTrendDTO::profit).toList());
        model.addAttribute("trendGranularity", profitReportService.trendGranularity(period.startDate(), period.endDate()));
        model.addAttribute("products", profitReportService.getProductPerformance(
                period.startDate(), period.endDate(), selectedSort));
        model.addAttribute("sort", selectedSort);
        return "admin/report/profit";
    }

    @GetMapping("/payment")
    public String payment(
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end,
            Model model
    ) {
        AdminReportPeriod period = resolvePeriod(start, end, model);
        List<PaymentAnalysisDTO> payments = paymentReportService.getPaymentAnalysis(
                period.startDate(), period.endDate());
        BigDecimal total = payments.stream()
                .map(PaymentAnalysisDTO::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        addPeriod(model, period);
        model.addAttribute("payments", payments);
        model.addAttribute("total", total);
        return "admin/report/payment";
    }

    private AdminReportPeriod resolvePeriod(LocalDate start, LocalDate end, Model model) {
        try {
            return salesReportService.resolvePeriod(start, end);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            return salesReportService.resolvePeriod(null, null);
        }
    }

    private void addPeriod(Model model, AdminReportPeriod period) {
        LocalDate today = salesReportService.today();
        model.addAttribute("start", period.startDate());
        model.addAttribute("end", period.endDate());
        model.addAttribute("today", today);
        model.addAttribute("weekStart", today.minusDays(6));
        model.addAttribute("monthStart", today.withDayOfMonth(1));
        model.addAttribute("threeMonthStart", today.minusMonths(2).withDayOfMonth(1));
    }

    private ResponseEntity<byte[]> excelResponse(ReportExportService.GeneratedExcel export) {
        return fileResponse(export.filename(), export.content(), XLSX);
    }

    private ResponseEntity<byte[]> fileResponse(String filename, byte[] content, MediaType mediaType) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(content.length)
                .body(content);
    }
}
