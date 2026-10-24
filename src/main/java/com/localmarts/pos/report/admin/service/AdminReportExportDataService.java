package com.localmarts.pos.report.admin.service;

import com.localmarts.pos.report.admin.dto.AdminReportExportData;
import com.localmarts.pos.report.admin.model.AdminReportPeriod;
import com.localmarts.pos.report.admin.repository.AdminReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class AdminReportExportDataService {
    private final AdminReportRepository repository;
    private final SalesReportService salesReportService;
    private final ProfitReportService profitReportService;
    private final PaymentReportService paymentReportService;

    public AdminReportExportDataService(
            AdminReportRepository repository,
            SalesReportService salesReportService,
            ProfitReportService profitReportService,
            PaymentReportService paymentReportService
    ) {
        this.repository = repository;
        this.salesReportService = salesReportService;
        this.profitReportService = profitReportService;
        this.paymentReportService = paymentReportService;
    }

    @Transactional(readOnly = true)
    public AdminReportExportData load(LocalDate start, LocalDate end) {
        AdminReportPeriod period = salesReportService.resolvePeriod(start, end);
        LocalDate firstDate = period.startDate();
        LocalDate lastDate = period.endDate();

        // Both export formats use this snapshot assembly path so their totals cannot drift apart.
        return new AdminReportExportData(
                period,
                repository.findSalesForExport(period.start(), period.cutoff(), null),
                salesReportService.getReport(firstDate, lastDate),
                profitReportService.getProfitSummary(firstDate, lastDate),
                profitReportService.getProfitTrend(firstDate, lastDate),
                profitReportService.getProductPerformance(firstDate, lastDate, "profit"),
                paymentReportService.getPaymentAnalysis(firstDate, lastDate)
        );
    }
}
