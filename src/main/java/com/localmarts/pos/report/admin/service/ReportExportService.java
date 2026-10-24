package com.localmarts.pos.report.admin.service;

import com.localmarts.pos.report.admin.dto.AdminReportExportData;
import com.localmarts.pos.report.admin.model.AdminReportPeriod;
import com.localmarts.pos.report.common.ReportTime;
import com.localmarts.pos.report.admin.repository.AdminReportRepository;
import com.localmarts.pos.report.admin.dto.CashierSaleDTO;
import com.localmarts.pos.report.admin.dto.PaymentAnalysisDTO;
import com.localmarts.pos.report.admin.dto.ProductProfitDTO;
import com.localmarts.pos.report.admin.dto.ProfitSummaryDTO;
import com.localmarts.pos.report.admin.dto.ProfitTrendDTO;
import com.localmarts.pos.sale.entity.Sale;
import com.localmarts.pos.user.UserRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReportExportService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdminReportRepository repository;
    private final UserRepository users;
    private final AdminReportExportDataService combinedReportData;
    private final Clock clock;

    @Autowired
    public ReportExportService(
            AdminReportRepository repository,
            UserRepository users,
            AdminReportExportDataService combinedReportData
    ) {
        this(repository, users, combinedReportData, Clock.system(ReportTime.ZONE));
    }

    ReportExportService(
            AdminReportRepository repository,
            UserRepository users,
            AdminReportExportDataService combinedReportData,
            Clock clock
    ) {
        this.repository = repository;
        this.users = users;
        this.combinedReportData = combinedReportData;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GeneratedExcel exportSales(LocalDate start, LocalDate end, Long cashierId) {
        AdminReportPeriod period = AdminReportPeriod.resolve(start, end, clock);
        String cashierName = null;
        if (cashierId != null) {
            cashierName = users.findById(cashierId)
                    .orElseThrow(() -> new IllegalArgumentException("Cashier account was not found."))
                    .getFullName();
        }
        List<Sale> sales = repository.findSalesForExport(period.start(), period.cutoff(), cashierId);
        String suffix = cashierName == null ? "overall" : safeFilename(cashierName);
        String filename = "sales-report-" + suffix + "-" + period.startDate() + "-to-" + period.endDate() + ".xlsx";
        return new GeneratedExcel(filename, salesWorkbook(period, cashierName, sales));
    }

    @Transactional(readOnly = true)
    public GeneratedExcel exportCombined(LocalDate start, LocalDate end) {
        AdminReportExportData data = combinedReportData.load(start, end);
        String filename = "admin-report-" + data.period().startDate()
                + "-to-" + data.period().endDate() + ".xlsx";
        return new GeneratedExcel(filename, combinedWorkbook(data));
    }

    private byte[] salesWorkbook(AdminReportPeriod period, String cashierName, List<Sale> sales) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            createSalesSheet(workbook, styles, period, cashierName, sales, "Sales Report");
            return write(workbook);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create the sales report export.", exception);
        }
    }

    private byte[] combinedWorkbook(AdminReportExportData data) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            createSalesSheet(workbook, styles, data.period(), null, data.sales(), "Sales");
            createCashierSheet(workbook, styles, data.period(), data.salesReport().cashierSales());
            createProfitSheet(workbook, styles, data.period(), data.profitSummary(), data.profitTrend());
            createProductSheet(workbook, styles, data.period(), data.products());
            createPaymentSheet(workbook, styles, data.period(), data.payments());
            return write(workbook);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create the combined admin report export.", exception);
        }
    }

    private void createSalesSheet(
            XSSFWorkbook workbook,
            Styles styles,
            AdminReportPeriod period,
            String cashierName,
            List<Sale> sales,
            String sheetName
    ) {
        Sheet sheet = workbook.createSheet(sheetName);
        title(sheet, styles, cashierName == null ? "Overall Sales Report" : "Sales Report — " + cashierName, period);

        BigDecimal total = sales.stream()
                .map(Sale::getTotalAmount)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Row summary = sheet.createRow(2);
        summary.createCell(0).setCellValue("Transactions");
        summary.createCell(1).setCellValue(sales.size());
        summary.createCell(2).setCellValue("Revenue");
        money(summary, 3, total, styles);

        String[] headings = {"Sale ID", "Invoice", "Date & Time", "Cashier", "Customer", "Payment", "Amount"};
        header(sheet, 4, headings, styles);
        int rowNumber = 5;
        for (Sale sale : sales) {
            Row row = sheet.createRow(rowNumber++);
            if (sale.getId() != null) row.createCell(0).setCellValue(sale.getId());
            row.createCell(1).setCellValue(sale.getInvoiceNo());
            row.createCell(2).setCellValue(sale.getTimestamp().format(DATE_TIME));
            row.createCell(3).setCellValue(sale.getCashier().getFullName());
            row.createCell(4).setCellValue(sale.getCustomer() == null ? "Walk-in" : sale.getCustomer().getName());
            row.createCell(5).setCellValue(sale.getPaymentMethod().name());
            money(row, 6, sale.getTotalAmount(), styles);
        }
        finishTable(sheet, headings.length, 4, rowNumber - 1, 5);
    }

    private void createCashierSheet(
            XSSFWorkbook workbook,
            Styles styles,
            AdminReportPeriod period,
            List<CashierSaleDTO> cashiers
    ) {
        Sheet sheet = workbook.createSheet("Cashier Sales");
        title(sheet, styles, "Sales by Cashier", period);
        String[] headings = {"Cashier", "Transactions", "Revenue"};
        header(sheet, 3, headings, styles);
        int rowNumber = 4;
        for (CashierSaleDTO cashier : cashiers) {
            Row row = sheet.createRow(rowNumber++);
            row.createCell(0).setCellValue(cashier.cashierName());
            row.createCell(1).setCellValue(cashier.saleCount());
            money(row, 2, cashier.revenue(), styles);
        }
        finishTable(sheet, headings.length, 3, rowNumber - 1, 4);
    }

    private void createProfitSheet(
            XSSFWorkbook workbook,
            Styles styles,
            AdminReportPeriod period,
            ProfitSummaryDTO summary,
            List<ProfitTrendDTO> trend
    ) {
        Sheet sheet = workbook.createSheet("Profit");
        title(sheet, styles, "Profit Report", period);
        String[] labels = {"Total Revenue", "Cost of Products Sold", "Gross Profit", "Net Profit"};
        BigDecimal[] values = {
                summary.totalRevenue(), summary.costOfGoodsSold(), summary.grossProfit(), summary.netProfit()
        };
        for (int index = 0; index < labels.length; index++) {
            Row row = sheet.createRow(3 + index);
            row.createCell(0).setCellValue(labels[index]);
            money(row, 1, values[index], styles);
        }

        String[] headings = {"Trend Period", "Profit"};
        header(sheet, 8, headings, styles);
        int rowNumber = 9;
        for (ProfitTrendDTO point : trend) {
            Row row = sheet.createRow(rowNumber++);
            row.createCell(0).setCellValue(point.label());
            money(row, 1, point.profit(), styles);
        }
        autosize(sheet, 2);
        sheet.createFreezePane(0, 9);
    }

    private void createProductSheet(
            XSSFWorkbook workbook,
            Styles styles,
            AdminReportPeriod period,
            List<ProductProfitDTO> products
    ) {
        Sheet sheet = workbook.createSheet("Product Performance");
        title(sheet, styles, "Product Profit Overview", period);
        String[] headings = {"Product", "Category", "Unit", "Units Sold", "Revenue", "Cost", "Profit"};
        header(sheet, 3, headings, styles);
        int rowNumber = 4;
        for (ProductProfitDTO product : products) {
            Row row = sheet.createRow(rowNumber++);
            row.createCell(0).setCellValue(product.productName());
            row.createCell(1).setCellValue(product.categoryName());
            row.createCell(2).setCellValue(product.unitName());
            row.createCell(3).setCellValue(product.quantitySold());
            money(row, 4, product.revenue(), styles);
            money(row, 5, product.cost(), styles);
            money(row, 6, product.profit(), styles);
        }
        finishTable(sheet, headings.length, 3, rowNumber - 1, 4);
    }

    private void createPaymentSheet(
            XSSFWorkbook workbook,
            Styles styles,
            AdminReportPeriod period,
            List<PaymentAnalysisDTO> payments
    ) {
        Sheet sheet = workbook.createSheet("Payment Analysis");
        title(sheet, styles, "Payment Analysis", period);
        String[] headings = {"Payment Type", "Sale Amount", "Percentage"};
        header(sheet, 3, headings, styles);
        int rowNumber = 4;
        for (PaymentAnalysisDTO payment : payments) {
            Row row = sheet.createRow(rowNumber++);
            row.createCell(0).setCellValue(payment.paymentType());
            money(row, 1, payment.amount(), styles);
            Cell percentage = row.createCell(2);
            percentage.setCellValue(payment.percentage().divide(BigDecimal.valueOf(100)).doubleValue());
            percentage.setCellStyle(styles.percentage);
        }
        finishTable(sheet, headings.length, 3, rowNumber - 1, 4);
    }

    private static void title(Sheet sheet, Styles styles, String text, AdminReportPeriod period) {
        Cell title = sheet.createRow(0).createCell(0);
        title.setCellValue(text);
        title.setCellStyle(styles.title);
        Row periodRow = sheet.createRow(1);
        periodRow.createCell(0).setCellValue("Report period");
        periodRow.createCell(1).setCellValue(period.startDate() + " to " + period.endDate());
    }

    private static void header(Sheet sheet, int rowNumber, String[] headings, Styles styles) {
        Row row = sheet.createRow(rowNumber);
        for (int index = 0; index < headings.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(headings[index]);
            cell.setCellStyle(styles.header);
        }
    }

    private static void money(Row row, int column, BigDecimal value, Styles styles) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? 0 : value.doubleValue());
        cell.setCellStyle(styles.money);
    }

    private static void finishTable(Sheet sheet, int columnCount, int headerRow, int lastRow, int freezeRow) {
        autosize(sheet, columnCount);
        sheet.createFreezePane(0, freezeRow);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                headerRow, Math.max(headerRow, lastRow), 0, columnCount - 1));
    }

    private static void autosize(Sheet sheet, int columnCount) {
        for (int column = 0; column < columnCount; column++) {
            sheet.autoSizeColumn(column);
            sheet.setColumnWidth(column, Math.min(sheet.getColumnWidth(column) + 768, 40 * 256));
        }
    }

    private static byte[] write(XSSFWorkbook workbook) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static String safeFilename(String value) {
        String safe = value.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return safe.isBlank() ? "cashier" : safe;
    }

    public record GeneratedExcel(String filename, byte[] content) {
        public GeneratedExcel {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle header;
        final CellStyle money;
        final CellStyle percentage;

        Styles(XSSFWorkbook workbook) {
            title = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            title.setFont(titleFont);

            header = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setBorderBottom(BorderStyle.THIN);

            DataFormat format = workbook.createDataFormat();
            money = workbook.createCellStyle();
            money.setDataFormat(format.getFormat("#,##0.00"));
            percentage = workbook.createCellStyle();
            percentage.setDataFormat(format.getFormat("0.00%"));
        }
    }
}
