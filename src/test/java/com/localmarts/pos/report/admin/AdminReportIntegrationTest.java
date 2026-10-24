package com.localmarts.pos.report.admin;

import com.localmarts.pos.category.entity.Category;
import com.localmarts.pos.product.entity.Product;
import com.localmarts.pos.report.admin.dto.ProfitTrendDTO;
import com.localmarts.pos.report.admin.service.AdminReportPdfService;
import com.localmarts.pos.report.admin.service.PaymentReportService;
import com.localmarts.pos.report.admin.service.ProfitReportService;
import com.localmarts.pos.report.admin.service.ReportExportService;
import com.localmarts.pos.report.admin.service.SalesReportService;
import com.localmarts.pos.sale.entity.PaymentMethod;
import com.localmarts.pos.sale.entity.PaymentStatus;
import com.localmarts.pos.sale.entity.Sale;
import com.localmarts.pos.sale.entity.SaleItem;
import com.localmarts.pos.sale.entity.SaleStatus;
import com.localmarts.pos.unit.entity.Unit;
import com.localmarts.pos.user.entity.Role;
import com.localmarts.pos.user.entity.User;
import jakarta.persistence.EntityManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AdminReportIntegrationTest {
    @Autowired EntityManager entityManager;
    @Autowired SalesReportService sales;
    @Autowired ProfitReportService profits;
    @Autowired PaymentReportService payments;
    @Autowired ReportExportService exports;
    @Autowired AdminReportPdfService pdfs;

    private User firstCashier;

    @BeforeEach
    void setUp() {
        firstCashier = cashier("report-cashier-one", "Cashier One");
        User secondCashier = cashier("report-cashier-two", "Cashier Two");

        Category category = Category.builder().name("Beverage").description("Drinks").build();
        category.setCreatedBy(0L);
        entityManager.persist(category);
        Unit unit = Unit.builder().name("Bottle").abbreviation("btl").build();
        unit.setCreatedBy(0L);
        entityManager.persist(unit);
        Product product = Product.builder()
                .name("Tea").barcode("REPORT-TEA").sellingPrice(money("50"))
                .costPrice(money("30")).discountPercent(BigDecimal.ZERO)
                .stockQuantity(100).minimumStock(1).category(category).unit(unit).build();
        product.setCreatedBy(0L);
        entityManager.persist(product);
        entityManager.flush();
        Long productId = product.getId();
        entityManager.createNativeQuery("update products set id = rowid where id is null")
                .executeUpdate();
        entityManager.clear();
        product = entityManager.find(Product.class, productId);

        sale(firstCashier, product, "REPORT-001", LocalDateTime.of(2026, 10, 2, 10, 0),
                PaymentMethod.CASH, 2, "100.00", "30.00", PaymentStatus.PAID, SaleStatus.COMPLETED);
        sale(secondCashier, product, "REPORT-002", LocalDateTime.of(2026, 10, 10, 11, 0),
                PaymentMethod.CARD, 1, "50.00", "10.00", PaymentStatus.PAID, SaleStatus.COMPLETED);
        sale(firstCashier, product, "REPORT-PENDING", LocalDateTime.of(2026, 10, 11, 11, 0),
                PaymentMethod.QR, 1, "999.00", "1.00", PaymentStatus.PENDING, SaleStatus.COMPLETED);
        entityManager.flush();
        entityManager.createNativeQuery("update sales set id = rowid where id is null")
                .executeUpdate();
        entityManager.createNativeQuery("update sale_items set id = rowid where id is null")
                .executeUpdate();
        entityManager.clear();
    }

    @Test
    void salesProfitProductAndPaymentReportsUseTheSameInclusivePeriod() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);

        var salesReport = sales.getReport(start, end);
        assertThat(salesReport.totalSaleCount()).isEqualTo(2);
        assertThat(salesReport.totalRevenue()).isEqualByComparingTo("150.00");
        assertThat(salesReport.averageSale()).isEqualByComparingTo("75.00");
        assertThat(salesReport.cashierSales()).extracting(row -> row.cashierName())
                .containsExactly("Cashier One", "Cashier Two");

        var summary = profits.getProfitSummary(start, end);
        assertThat(summary.totalRevenue()).isEqualByComparingTo("150.00");
        assertThat(summary.costOfGoodsSold()).isEqualByComparingTo("70.00");
        assertThat(summary.grossProfit()).isEqualByComparingTo("80.00");
        assertThat(summary.netProfit()).isEqualByComparingTo("80.00");

        var product = profits.getProductPerformance(start, end, "profit").getFirst();
        assertThat(product.productName()).isEqualTo("Tea");
        assertThat(product.categoryName()).isEqualTo("Beverage");
        assertThat(product.unitName()).isEqualTo("Bottle");
        assertThat(product.quantitySold()).isEqualTo(3);
        assertThat(product.revenue()).isEqualByComparingTo("150.00");
        assertThat(product.cost()).isEqualByComparingTo("70.00");

        var paymentRows = payments.getPaymentAnalysis(start, end);
        assertThat(paymentRows).extracting(row -> row.paymentType()).containsExactly("CASH", "CARD");
        assertThat(paymentRows.get(0).percentage()).isEqualByComparingTo("66.67");
        assertThat(paymentRows.get(1).percentage()).isEqualByComparingTo("33.33");
    }

    @Test
    void monthUsesWeeklyTrendAndExportsOverallOrOneCashier() throws Exception {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        var trend = profits.getProfitTrend(start, end);
        assertThat(profits.trendGranularity(start, end)).isEqualTo("Weekly");
        assertThat(trend).hasSize(5);
        assertThat(trend.get(0).profit()).isEqualByComparingTo("40.00");
        assertThat(trend.get(1).profit()).isEqualByComparingTo("40.00");

        var threeMonths = profits.getProfitTrend(LocalDate.of(2026, 8, 1), end);
        assertThat(profits.trendGranularity(LocalDate.of(2026, 8, 1), end)).isEqualTo("Monthly");
        assertThat(threeMonths).extracting(ProfitTrendDTO::label)
                .containsExactly("Aug 2026", "Sep 2026", "Oct 2026");
        assertThat(threeMonths.getLast().profit()).isEqualByComparingTo("80.00");

        var overall = exports.exportSales(start, end, null);
        assertThat(overall.filename()).contains("overall").endsWith(".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(overall.content()))) {
            assertThat(workbook.getSheet("Sales Report").getRow(2).getCell(1).getNumericCellValue()).isEqualTo(2);
            assertThat(workbook.getSheet("Sales Report").getLastRowNum()).isEqualTo(6);
        }

        var cashier = exports.exportSales(start, end, firstCashier.getId());
        assertThat(cashier.filename()).contains("cashier-one");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(cashier.content()))) {
            assertThat(workbook.getSheet("Sales Report").getRow(2).getCell(1).getNumericCellValue()).isEqualTo(1);
            assertThat(workbook.getSheet("Sales Report").getLastRowNum()).isEqualTo(5);
        }

        var combined = exports.exportCombined(start, end);
        assertThat(combined.filename()).isEqualTo("admin-report-2026-10-01-to-2026-10-31.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(combined.content()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(5);
            assertThat(workbook.getSheet("Sales").getRow(2).getCell(1).getNumericCellValue()).isEqualTo(2);
            assertThat(workbook.getSheet("Cashier Sales").getRow(4).getCell(0).getStringCellValue())
                    .isEqualTo("Cashier One");
            assertThat(workbook.getSheet("Profit").getRow(5).getCell(1).getNumericCellValue()).isEqualTo(80);
            assertThat(workbook.getSheet("Product Performance").getRow(4).getCell(0).getStringCellValue())
                    .isEqualTo("Tea");
            assertThat(workbook.getSheet("Payment Analysis").getRow(4).getCell(0).getStringCellValue())
                    .isEqualTo("CASH");
        }

        var combinedPdf = pdfs.exportCombined(start, end);
        assertThat(combinedPdf.filename()).isEqualTo("admin-report-2026-10-01-to-2026-10-31.pdf");
        assertThat(combinedPdf.content()).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        try (PDDocument document = Loader.loadPDF(combinedPdf.content())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("COMBINED ADMIN REPORT", "Sales Transactions", "Profit Summary",
                    "Product Performance", "Payment Analysis", "Tea", "CASH");
        }
    }

    private User cashier(String username, String name) {
        User user = User.builder().username(username).fullName(name).password("encoded").role(Role.CASHIER).build();
        user.setCreatedBy(0L);
        entityManager.persist(user);
        return user;
    }

    private void sale(User cashier, Product product, String invoice, LocalDateTime timestamp,
                      PaymentMethod method, int quantity, String revenue, String cost,
                      PaymentStatus paymentStatus, SaleStatus saleStatus) {
        BigDecimal total = money(revenue);
        Sale sale = Sale.builder()
                .invoiceNo(invoice).cashier(cashier).subtotal(total).totalDiscount(money("0"))
                .pointDiscount(money("0")).totalAmount(total).paidAmount(total).changeAmount(money("0"))
                .pointEarned(0).pointRedeemed(0).paymentMethod(method).paymentStatus(paymentStatus)
                .saleStatus(saleStatus).timestamp(timestamp).build();
        sale.addItem(SaleItem.builder()
                .product(product).productNameSnapshot(product.getName()).barcodeSnapshot(product.getBarcode())
                .unitSnapshot(product.getUnit().getName()).quantity(quantity)
                .unitPrice(total.divide(BigDecimal.valueOf(quantity))).grossSubtotal(total)
                .discountPercentSnapshot(money("0")).discountAmount(money("0")).netTotal(total)
                .costPriceSnapshot(money(cost)).saleStatus(saleStatus).refundQuantity(0).build());
        entityManager.persist(sale);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
