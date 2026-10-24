package com.localmarts.pos.report.admin.service;

import com.localmarts.pos.report.common.PdfTextSupport;
import com.localmarts.pos.report.common.ReportDataException;
import com.localmarts.pos.report.admin.dto.AdminReportExportData;
import com.localmarts.pos.report.admin.model.AdminReportPeriod;
import com.localmarts.pos.report.admin.dto.CashierSaleDTO;
import com.localmarts.pos.report.admin.dto.PaymentAnalysisDTO;
import com.localmarts.pos.report.admin.dto.ProductProfitDTO;
import com.localmarts.pos.report.admin.dto.ProfitSummaryDTO;
import com.localmarts.pos.report.admin.dto.ProfitTrendDTO;
import com.localmarts.pos.report.admin.dto.SalesReportDTO;
import com.localmarts.pos.sale.entity.Sale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class AdminReportPdfService {
    private static final String REGULAR_FONT = "fonts/NotoSans-Regular.ttf";
    private static final String BOLD_FONT = "fonts/NotoSans-Bold.ttf";
    private static final String THAI_REGULAR_FONT = "fonts/NotoSansThai-Regular.ttf";
    private static final String THAI_BOLD_FONT = "fonts/NotoSansThai-Bold.ttf";

    private final AdminReportExportDataService combinedReportData;

    public AdminReportPdfService(AdminReportExportDataService combinedReportData) {
        this.combinedReportData = combinedReportData;
    }

    @Transactional(readOnly = true)
    public GeneratedPdf exportCombined(LocalDate start, LocalDate end) {
        AdminReportExportData data = combinedReportData.load(start, end);
        String filename = "admin-report-" + data.period().startDate()
                + "-to-" + data.period().endDate() + ".pdf";
        return new GeneratedPdf(filename, render(data));
    }

    private byte[] render(AdminReportExportData data) {
        try (PDDocument document = new PDDocument();
             InputStream regularStream = new ClassPathResource(REGULAR_FONT).getInputStream();
             InputStream boldStream = new ClassPathResource(BOLD_FONT).getInputStream();
             InputStream thaiRegularStream = new ClassPathResource(THAI_REGULAR_FONT).getInputStream();
             InputStream thaiBoldStream = new ClassPathResource(THAI_BOLD_FONT).getInputStream()) {
            PDType0Font regular = PDType0Font.load(document, regularStream, true);
            PDType0Font bold = PDType0Font.load(document, boldStream, true);
            PDType0Font thaiRegular = PDType0Font.load(document, thaiRegularStream, true);
            PDType0Font thaiBold = PDType0Font.load(document, thaiBoldStream, true);
            Writer writer = new Writer(document, regular, bold, thaiRegular, thaiBold, data.period());
            writer.write(data.sales(), data.salesReport(), data.profitSummary(), data.profitTrend(),
                    data.products(), data.payments());
            writer.finish();
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                document.save(output);
                return output.toByteArray();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new ReportDataException("Admin report PDF cannot be generated.", exception);
        }
    }

    public record GeneratedPdf(String filename, byte[] content) {
        public GeneratedPdf {
            content = Arrays.copyOf(content, content.length);
        }

        @Override
        public byte[] content() {
            return Arrays.copyOf(content, content.length);
        }
    }

    private static final class Writer {
        // Landscape A4 keeps the transaction and product tables readable without dropping columns.
        private static final PDRectangle PAGE_SIZE = new PDRectangle(
                PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
        private static final float MARGIN = 30f;
        private static final float CONTENT_WIDTH = PAGE_SIZE.getWidth() - (2 * MARGIN);
        private static final float TOP = PAGE_SIZE.getHeight() - 32f;
        private static final float BOTTOM = 34f;
        private static final Color INK = new Color(33, 37, 41);
        private static final Color MUTED = new Color(92, 99, 106);
        private static final Color ACCENT = new Color(25, 135, 84);
        private static final Color BORDER = new Color(210, 214, 218);
        private static final Color HEADER_FILL = new Color(229, 242, 235);
        private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        private final PDDocument document;
        private final PDType0Font regular;
        private final PDType0Font bold;
        private final PDType0Font thaiRegular;
        private final PDType0Font thaiBold;
        private final AdminReportPeriod period;
        private PDPageContentStream stream;
        private float y;

        private Writer(
                PDDocument document,
                PDType0Font regular,
                PDType0Font bold,
                PDType0Font thaiRegular,
                PDType0Font thaiBold,
                AdminReportPeriod period
        ) throws IOException {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
            this.thaiRegular = thaiRegular;
            this.thaiBold = thaiBold;
            this.period = period;
            newPage(false);
        }

        private void write(
                List<Sale> sales,
                SalesReportDTO salesReport,
                ProfitSummaryDTO profitSummary,
                List<ProfitTrendDTO> profitTrend,
                List<ProductProfitDTO> products,
                List<PaymentAnalysisDTO> payments
        ) throws IOException {
            documentHeader();
            section("1. Sales Summary");
            table(
                    List.of("Transactions", "Total Revenue", "Average Sale"),
                    List.of(row(
                            Long.toString(salesReport.totalSaleCount()),
                            money(salesReport.totalRevenue()),
                            money(salesReport.averageSale()))),
                    new float[]{CONTENT_WIDTH / 3, CONTENT_WIDTH / 3, CONTENT_WIDTH / 3}
            );

            section("2. Sales by Cashier");
            List<List<String>> cashierRows = new ArrayList<>();
            for (CashierSaleDTO cashier : salesReport.cashierSales()) {
                cashierRows.add(row(cashier.cashierName(), Long.toString(cashier.saleCount()), money(cashier.revenue())));
            }
            table(List.of("Cashier", "Transactions", "Revenue"), cashierRows,
                    new float[]{360f, 170f, CONTENT_WIDTH - 530f});

            section("3. Sales Transactions");
            List<List<String>> saleRows = new ArrayList<>();
            for (Sale sale : sales) {
                saleRows.add(row(
                        value(sale.getId()), sale.getInvoiceNo(), DATE_TIME.format(sale.getTimestamp()),
                        sale.getCashier().getFullName(), sale.getCustomer() == null ? "Walk-in" : sale.getCustomer().getName(),
                        sale.getPaymentMethod().name(), money(sale.getTotalAmount())
                ));
            }
            table(List.of("ID", "Invoice", "Date / Time", "Cashier", "Customer", "Payment", "Amount"), saleRows,
                    new float[]{45f, 125f, 112f, 145f, 135f, 85f, CONTENT_WIDTH - 647f});

            section("4. Profit Summary");
            table(List.of("Total Revenue", "Cost of Products Sold", "Gross Profit", "Net Profit"),
                    List.of(row(money(profitSummary.totalRevenue()), money(profitSummary.costOfGoodsSold()),
                            money(profitSummary.grossProfit()), money(profitSummary.netProfit()))),
                    new float[]{CONTENT_WIDTH / 4, CONTENT_WIDTH / 4, CONTENT_WIDTH / 4, CONTENT_WIDTH / 4});

            section("5. Profit Trend");
            List<List<String>> trendRows = new ArrayList<>();
            for (ProfitTrendDTO point : profitTrend) {
                trendRows.add(row(point.label(), money(point.profit())));
            }
            table(List.of("Period", "Profit"), trendRows, new float[]{480f, CONTENT_WIDTH - 480f});

            section("6. Product Performance");
            List<List<String>> productRows = new ArrayList<>();
            for (ProductProfitDTO product : products) {
                productRows.add(row(product.productName(), product.categoryName(), product.unitName(),
                        Long.toString(product.quantitySold()), money(product.revenue()), money(product.cost()),
                        money(product.profit())));
            }
            table(List.of("Product", "Category", "Unit", "Units", "Revenue", "Cost", "Profit"), productRows,
                    new float[]{185f, 125f, 72f, 65f, 110f, 105f, CONTENT_WIDTH - 662f});

            section("7. Payment Analysis");
            List<List<String>> paymentRows = new ArrayList<>();
            for (PaymentAnalysisDTO payment : payments) {
                paymentRows.add(row(payment.paymentType(), money(payment.amount()),
                        payment.percentage().setScale(2, RoundingMode.HALF_UP).toPlainString() + "%"));
            }
            table(List.of("Payment Type", "Sale Amount", "Percentage"), paymentRows,
                    new float[]{330f, 250f, CONTENT_WIDTH - 580f});
        }

        private void documentHeader() throws IOException {
            drawText("LOCALMART POS", MARGIN, y, bold, 9, ACCENT);
            y -= 25;
            drawText("COMBINED ADMIN REPORT", MARGIN, y, bold, 21, INK);
            y -= 18;
            drawText("Sales, profit, product performance, and payment analysis", MARGIN, y, regular, 9, MUTED);
            y -= 15;
            drawText("Inclusive period: " + period.startDate() + " to " + period.endDate(), MARGIN, y, regular, 9, MUTED);
            y -= 15;
            horizontalLine();
            y -= 4;
        }

        private void section(String heading) throws IOException {
            ensure(50);
            y -= 8;
            drawText(heading, MARGIN, y, bold, 12, ACCENT);
            y -= 17;
        }

        private void table(List<String> headers, List<List<String>> rows, float[] widths) throws IOException {
            validate(headers, widths);
            ensure(rowHeight(headers, widths, bold, 8f) + 20);
            drawRow(headers, widths, true);
            if (rows.isEmpty()) {
                drawRow(rowWithSize("No data for this period.", headers.size()), widths, false);
            } else {
                for (List<String> row : rows) {
                    validate(row, widths);
                    float height = rowHeight(row, widths, regular, 8f);
                    if (y - height < BOTTOM) {
                        newPage(true);
                        drawRow(headers, widths, true);
                    }
                    drawRow(row, widths, false);
                }
            }
            y -= 5;
        }

        private void drawRow(List<String> cells, float[] widths, boolean header) throws IOException {
            PDFont font = header ? bold : regular;
            float size = 8f;
            float height = rowHeight(cells, widths, font, size);
            float x = MARGIN;
            for (int index = 0; index < cells.size(); index++) {
                stream.setNonStrokingColor(header ? HEADER_FILL : Color.WHITE);
                stream.addRect(x, y - height, widths[index], height);
                stream.fill();
                stream.setStrokingColor(BORDER);
                stream.setLineWidth(0.4f);
                stream.addRect(x, y - height, widths[index], height);
                stream.stroke();
                float textY = y - 10;
                for (String line : wrap(cells.get(index), font, size, widths[index] - 8)) {
                    drawText(line, x + 4, textY, font, size, INK);
                    textY -= size + 2;
                }
                x += widths[index];
            }
            y -= height;
        }

        private float rowHeight(List<String> cells, float[] widths, PDFont font, float size) throws IOException {
            int lines = 1;
            for (int index = 0; index < cells.size(); index++) {
                lines = Math.max(lines, wrap(cells.get(index), font, size, widths[index] - 8).size());
            }
            return Math.max(18, 6 + lines * (size + 2));
        }

        private List<String> wrap(String value, PDFont font, float size, float maxWidth) throws IOException {
            String text = value(value).replace('\n', ' ').replace('\r', ' ');
            if (stringWidth(text, font, size) <= maxWidth) return List.of(text);
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (int offset = 0; offset < text.length();) {
                int codePoint = text.codePointAt(offset);
                String character = Character.toString(codePoint);
                if (!current.isEmpty() && stringWidth(current + character, font, size) > maxWidth) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                current.append(character);
                offset += Character.charCount(codePoint);
            }
            if (!current.isEmpty()) lines.add(current.toString());
            return lines.isEmpty() ? List.of("") : lines;
        }

        private void drawText(String text, float x, float baseline, PDFont requested, float size, Color color)
                throws IOException {
            stream.setNonStrokingColor(color);
            float cursor = x;
            for (PdfTextSupport.FontRun run : runs(text, requested)) {
                stream.beginText();
                stream.setFont(run.font(), size);
                stream.newLineAtOffset(cursor, baseline);
                stream.showText(run.text());
                stream.endText();
                cursor += run.font().getStringWidth(run.text()) / 1000f * size;
            }
        }

        private float stringWidth(String text, PDFont requested, float size) throws IOException {
            float width = 0;
            for (PdfTextSupport.FontRun run : runs(text, requested)) {
                width += run.font().getStringWidth(run.text()) / 1000f * size;
            }
            return width;
        }

        private List<PdfTextSupport.FontRun> runs(String text, PDFont requested) throws IOException {
            return PdfTextSupport.runs(value(text), requested == bold ? bold : regular,
                    requested == bold ? thaiBold : thaiRegular);
        }

        private void newPage(boolean continuation) throws IOException {
            closeStream();
            PDPage page = new PDPage(PAGE_SIZE);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = TOP;
            if (continuation) {
                drawText("LOCALMART POS — COMBINED ADMIN REPORT", MARGIN, y, bold, 8, ACCENT);
                y -= 13;
                horizontalLine();
                y -= 10;
            }
        }

        private void finish() throws IOException {
            closeStream();
            int pages = document.getNumberOfPages();
            for (int index = 0; index < pages; index++) {
                try (PDPageContentStream footer = new PDPageContentStream(
                        document, document.getPage(index), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    String text = "Admin report " + period.startDate() + " to " + period.endDate()
                            + "    Page " + (index + 1) + " of " + pages;
                    float width = stringWidth(text, regular, 8);
                    footer.setNonStrokingColor(MUTED);
                    footer.beginText();
                    footer.setFont(regular, 8);
                    footer.newLineAtOffset((PAGE_SIZE.getWidth() - width) / 2, 17);
                    footer.showText(text);
                    footer.endText();
                }
            }
        }

        private void ensure(float height) throws IOException {
            if (y - height < BOTTOM) newPage(true);
        }

        private void horizontalLine() throws IOException {
            stream.setStrokingColor(ACCENT);
            stream.setLineWidth(1f);
            stream.moveTo(MARGIN, y);
            stream.lineTo(PAGE_SIZE.getWidth() - MARGIN, y);
            stream.stroke();
        }

        private void closeStream() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        private static void validate(List<String> cells, float[] widths) {
            if (cells.size() != widths.length) throw new IllegalArgumentException("PDF table column mismatch");
        }

        private static List<String> row(String... values) {
            return List.of(values);
        }

        private static List<String> rowWithSize(String first, int size) {
            List<String> row = new ArrayList<>();
            row.add(first);
            while (row.size() < size) row.add("");
            return row;
        }

        private static String value(Object value) {
            if (value == null) return "N/A";
            String text = value.toString();
            return text.isBlank() ? "N/A" : text;
        }

        private static String money(BigDecimal value) {
            return value == null ? "N/A" : "THB " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
    }
}
