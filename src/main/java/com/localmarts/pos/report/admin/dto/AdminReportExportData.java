package com.localmarts.pos.report.admin.dto;

import com.localmarts.pos.report.admin.model.AdminReportPeriod;
import com.localmarts.pos.report.admin.dto.PaymentAnalysisDTO;
import com.localmarts.pos.report.admin.dto.ProductProfitDTO;
import com.localmarts.pos.report.admin.dto.ProfitSummaryDTO;
import com.localmarts.pos.report.admin.dto.ProfitTrendDTO;
import com.localmarts.pos.report.admin.dto.SalesReportDTO;
import com.localmarts.pos.sale.entity.Sale;

import java.util.List;

/** Complete, date-consistent data set shared by the admin Excel and PDF exporters. */
public record AdminReportExportData(
        AdminReportPeriod period,
        List<Sale> sales,
        SalesReportDTO salesReport,
        ProfitSummaryDTO profitSummary,
        List<ProfitTrendDTO> profitTrend,
        List<ProductProfitDTO> products,
        List<PaymentAnalysisDTO> payments
) {
    public AdminReportExportData {
        sales = List.copyOf(sales);
        profitTrend = List.copyOf(profitTrend);
        products = List.copyOf(products);
        payments = List.copyOf(payments);
    }
}
