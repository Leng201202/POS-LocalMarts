package com.localmarts.pos.report.admin.controller;

import com.localmarts.pos.report.admin.model.AdminReportPeriod;
import com.localmarts.pos.common.CurrentUserService;
import com.localmarts.pos.config.SecurityConfig;
import com.localmarts.pos.report.admin.dto.CashierSaleDTO;
import com.localmarts.pos.report.admin.dto.PaymentAnalysisDTO;
import com.localmarts.pos.report.admin.dto.ProductProfitDTO;
import com.localmarts.pos.report.admin.dto.ProfitSummaryDTO;
import com.localmarts.pos.report.admin.dto.ProfitTrendDTO;
import com.localmarts.pos.report.admin.dto.SalesReportDTO;
import com.localmarts.pos.report.admin.service.AdminReportPdfService;
import com.localmarts.pos.report.admin.service.PaymentReportService;
import com.localmarts.pos.report.admin.service.ProfitReportService;
import com.localmarts.pos.report.admin.service.ReportExportService;
import com.localmarts.pos.report.admin.service.SalesReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminReportController.class)
@Import(SecurityConfig.class)
class AdminReportControllerTest {
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 31);

    @Autowired MockMvc mvc;
    @MockitoBean SalesReportService sales;
    @MockitoBean ProfitReportService profits;
    @MockitoBean PaymentReportService payments;
    @MockitoBean ReportExportService exports;
    @MockitoBean AdminReportPdfService pdfs;
    @MockitoBean CurrentUserService currentUser;

    @BeforeEach
    void setUp() {
        var period = new AdminReportPeriod(START, END);
        when(sales.today()).thenReturn(END);
        when(sales.resolvePeriod(any(), any())).thenReturn(period);
        when(sales.getReport(any(), any())).thenReturn(SalesReportDTO.builder()
                .startDate(START).endDate(END).totalSaleCount(2L)
                .totalRevenue(money("150")).averageSale(money("75"))
                .cashierSales(List.of(CashierSaleDTO.builder().cashierId(7L).cashierName("Cashier One")
                        .saleCount(2L).revenue(money("150")).build())).build());
        when(profits.getProfitSummary(any(), any())).thenReturn(ProfitSummaryDTO.builder()
                .totalRevenue(money("150")).costOfGoodsSold(money("70"))
                .grossProfit(money("80")).netProfit(money("80")).build());
        when(profits.getProfitTrend(any(), any())).thenReturn(List.of(new ProfitTrendDTO("1 Oct – 7 Oct", money("80"))));
        when(profits.trendGranularity(any(), any())).thenReturn("Weekly");
        when(profits.getProductPerformance(any(), any(), any())).thenReturn(List.of(
                new ProductProfitDTO("Tea", "Beverage", "Bottle", 3L, money("150"), money("70"), money("80"))));
        when(payments.getPaymentAnalysis(any(), any())).thenReturn(List.of(
                PaymentAnalysisDTO.builder().paymentType("CASH").amount(money("150")).percentage(money("100")).build()));
        when(exports.exportSales(any(), any(), any())).thenReturn(
                new ReportExportService.GeneratedExcel("sales-report-overall.xlsx", new byte[]{1, 2, 3}));
        when(exports.exportCombined(any(), any())).thenReturn(
                new ReportExportService.GeneratedExcel("admin-report-combined.xlsx", new byte[]{4, 5, 6}));
        when(pdfs.exportCombined(any(), any())).thenReturn(
                new AdminReportPdfService.GeneratedPdf("admin-report-combined.pdf", new byte[]{7, 8, 9}));
    }

    @Test
    void adminCanRenderAllReportPages() throws Exception {
        mvc.perform(get("/admin/report").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Admin Reports")))
                .andExpect(content().string(containsString("Export Complete Report")))
                .andExpect(content().string(containsString("/admin/report/export")));
        mvc.perform(get("/admin/report/sales").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Cashier One")))
                .andExpect(content().string(containsString("cashierId=7")));
        mvc.perform(get("/admin/report/profit").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Profit Trend")))
                .andExpect(content().string(containsString("Tea")));
        mvc.perform(get("/admin/report/payment").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Payment Method Breakdown")))
                .andExpect(content().string(containsString("CASH")));
    }

    @Test
    void exportIsAnExcelAttachmentAndReportsAreAdminOnly() throws Exception {
        mvc.perform(get("/admin/report/sales/export").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", containsString("sales-report-overall.xlsx")));
        mvc.perform(get("/admin/report/export").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", containsString("admin-report-combined.xlsx")));
        mvc.perform(get("/admin/report/export").param("format", "pdf")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("admin-report-combined.pdf")));
        mvc.perform(get("/admin/report/sales").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/report/sales/export"))
                .andExpect(status().is3xxRedirection());
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
