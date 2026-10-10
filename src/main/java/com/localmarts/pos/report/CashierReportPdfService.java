package com.localmarts.pos.report;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.BreakIterator;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class CashierReportPdfService {
    private static final String REGULAR_FONT = "fonts/NotoSans-Regular.ttf";
    private static final String BOLD_FONT = "fonts/NotoSans-Bold.ttf";
    private static final String THAI_REGULAR_FONT = "fonts/NotoSansThai-Regular.ttf";
    private static final String THAI_BOLD_FONT = "fonts/NotoSansThai-Bold.ttf";

    private final CashierReportSubmissionService submissions;

    public CashierReportPdfService(CashierReportSubmissionService submissions) {
        this.submissions = submissions;
    }

    public Optional<GeneratedPdf> generate(Long actorId, Long reportId) {
        return submissions.view(actorId, reportId).map(this::render);
    }

    private GeneratedPdf render(SavedReportView saved) {
        try (PDDocument document = new PDDocument();
             InputStream regularStream = new ClassPathResource(REGULAR_FONT).getInputStream();
             InputStream boldStream = new ClassPathResource(BOLD_FONT).getInputStream();
             InputStream thaiRegularStream = new ClassPathResource(THAI_REGULAR_FONT).getInputStream();
             InputStream thaiBoldStream = new ClassPathResource(THAI_BOLD_FONT).getInputStream()) {
            PDType0Font regular = PDType0Font.load(document, regularStream, true);
            PDType0Font bold = PDType0Font.load(document, boldStream, true);
            PDType0Font thaiRegular = PDType0Font.load(document, thaiRegularStream, true);
            PDType0Font thaiBold = PDType0Font.load(document, thaiBoldStream, true);
            ReportWriter writer = new ReportWriter(document, regular, bold, thaiRegular, thaiBold, saved.id());
            writer.write(saved);
            writer.finish();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return new GeneratedPdf(
                    "cashier-sale-report-" + saved.id() + ".pdf",
                    output.toByteArray()
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new ReportDataException("Saved report PDF cannot be generated.", exception);
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

    private static final class ReportWriter {
        private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
        private static final float MARGIN = 38f;
        private static final float CONTENT_WIDTH = PAGE_SIZE.getWidth() - (2 * MARGIN);
        private static final float TOP = PAGE_SIZE.getHeight() - 42f;
        private static final float BOTTOM = 44f;
        private static final Color INK = new Color(33, 37, 41);
        private static final Color MUTED = new Color(92, 99, 106);
        private static final Color ACCENT = new Color(31, 78, 121);
        private static final Color BORDER = new Color(210, 214, 218);
        private static final Color HEADER_FILL = new Color(236, 239, 242);
        private static final Color CASH_FILL = new Color(241, 247, 255);
        private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);
        private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm:ss", Locale.ENGLISH);

        private final PDDocument document;
        private final PDType0Font regular;
        private final PDType0Font bold;
        private final PDType0Font thaiRegular;
        private final PDType0Font thaiBold;
        private final Long reportId;
        private PDPageContentStream stream;
        private float y;

        private ReportWriter(PDDocument document, PDType0Font regular, PDType0Font bold,
                             PDType0Font thaiRegular, PDType0Font thaiBold, Long reportId) throws IOException {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
            this.thaiRegular = thaiRegular;
            this.thaiBold = thaiBold;
            this.reportId = reportId;
            newPage(false);
        }

        private void write(SavedReportView saved) throws IOException {
            CashierReportSnapshot report = saved.snapshot();
            documentHeader(saved, report);

            section("A. Report Information", 42);
            table(
                    List.of("Field", "Saved value"),
                    List.of(
                            row("Report type", displayType(saved.reportType())),
                            row("Inclusive period", DATE.format(saved.startDate()) + " - " + DATE.format(saved.endDate())),
                            row("Cashier", value(report.cashierName()) + " (" + value(report.cashierUsername()) + ")"),
                            row("Generated timestamp", DATE_TIME.format(saved.generatedAt()) + " Asia/Bangkok"),
                            row("Data-through cutoff", DATE_TIME.format(report.period().cutoff()) + " Asia/Bangkok (exclusive)"),
                            row("Submitted timestamp", DATE_TIME.format(saved.submittedAt()) + " Asia/Bangkok"),
                            row("Report ID", saved.id().toString())
                    ),
                    new float[]{145f, CONTENT_WIDTH - 145f}
            );

            section("B. Sales Summary", 42);
            CashierReportSnapshot.SalesSummary summary = report.summary();
            String average = summary.averageSaleValue() == null ? "N/A" : money(summary.averageSaleValue());
            String bestSeller = summary.bestSeller() == null
                    ? "None"
                    : value(summary.bestSeller().name()) + " - " + summary.bestSeller().netUnits() + " net units";
            table(
                    List.of("Measure", "Value"),
                    List.of(
                            row("Total sales transactions", Long.toString(summary.transactions())),
                            row("Total Sales (All Payment Methods)", money(summary.salesAmount())),
                            row("Average sale value", average),
                            row("Total discount", money(summary.totalDiscount())),
                            row("Refunds against these sales", summary.refundCount() + " events / " + money(summary.refundAmount())),
                            row("Retained sale amount", money(summary.retainedAmount())),
                            row("Best-selling product", bestSeller)
                    ),
                    new float[]{255f, CONTENT_WIDTH - 255f}
            );

            String cashierName = value(report.cashierName());
            section("C. Payment Summary", 74);
            paragraph("Receipts are from the selected sales. Refunds in this summary were processed by "
                    + cashierName + " during the report period, including refunds for another cashier's sales.", 9, MUTED);
            List<List<String>> paymentRows = new ArrayList<>();
            for (CashierReportSnapshot.PaymentSummaryRow payment : report.payments()) {
                paymentRows.add(row(payment.method(), money(payment.receipts()), money(payment.refunds()), money(payment.net())));
            }
            paymentRows.add(row("TOTAL", money(report.paymentTotal().receipts()), money(report.paymentTotal().refunds()), money(report.paymentTotal().net())));
            table(List.of("Payment", "Receipts", "Refunds", "Net"), paymentRows,
                    new float[]{100f, 137f, 137f, CONTENT_WIDTH - 374f});

            section("D. Cash Reconciliation", 112);
            paragraph("System Cash includes CASH receipts minus CASH refunds processed by " + cashierName
                    + ". Opening cash is excluded. CARD and QR receipts are included in Total Sales but not System Cash.", 9, MUTED);
            filledTable(
                    List.of("Measure", "Final saved value"),
                    List.of(
                            row("System Cash (Transactions)", money(saved.systemCash())),
                            row("Counted Cash (Real-World)", money(saved.realWorldCash())),
                            row("Cash Difference", money(saved.cashDifference())),
                            row("Difference Status", saved.status())
                    ),
                    new float[]{255f, CONTENT_WIDTH - 255f}, CASH_FILL
            );

            section("E. Sales Details", 76);
            paragraph("Refund amounts, quantities, and status reflect events captured in the immutable saved snapshot.", 9, MUTED);
            if (report.sales().isEmpty()) {
                paragraph("No paid sales in this period.", 10, MUTED);
            }
            int saleNumber = 0;
            for (CashierReportSnapshot.SaleReportLine sale : report.sales()) {
                saleNumber++;
                subheading("Sale " + saleNumber + " - " + value(sale.invoice()), 44);
                table(
                        List.of("Field", "Value", "Field", "Value"),
                        List.of(
                                row("Sale time", DATE_TIME.format(sale.timestamp()), "Customer", value(sale.customer())),
                                row("Payment", sale.paymentMethod().name(), "Status at cutoff", sale.status().name()),
                                row("Charged", money(sale.chargedAmount()), "Refunded", money(sale.refundAmount())),
                                row("Retained", money(sale.retainedAmount()), "Original / returned / net units",
                                        sale.originalQuantity() + " / " + sale.returnedQuantity() + " / " + sale.netQuantity())
                        ),
                        new float[]{72f, 148f, 118f, CONTENT_WIDTH - 338f}
                );

                List<List<String>> itemRows = new ArrayList<>();
                for (CashierReportSnapshot.SaleItemReportLine item : sale.items()) {
                    itemRows.add(row(
                            value(item.productName()),
                            value(item.barcode()),
                            value(item.size()) + " / " + value(item.unit()),
                            money(item.unitPrice()),
                            money(item.chargedAmount()),
                            item.originalQuantity() + " / " + item.returnedQuantity() + " / " + item.netQuantity()
                    ));
                }
                table(List.of("Product", "Barcode", "Size / unit", "Unit price", "Charged", "Original / returned / net"), itemRows,
                        new float[]{116f, 72f, 72f, 72f, 72f, CONTENT_WIDTH - 404f});

                for (CashierReportSnapshot.RefundReportLine refund : sale.refunds()) {
                    subheading("Refund event " + refund.auditId() + " - " + DATE_TIME.format(refund.timestamp()) + " - " + money(refund.amount()), 42);
                    List<List<String>> refundItemRows = new ArrayList<>();
                    for (CashierReportSnapshot.RefundItemLine item : refund.items()) {
                        refundItemRows.add(row(
                                value(item.productName()),
                                Integer.toString(item.quantity()),
                                money(item.amount()),
                                split(item.regularUnits()),
                                split(item.discountedUnits())
                        ));
                    }
                    table(List.of("Product", "Returned", "Amount", "Regular units", "Discounted units"), refundItemRows,
                            new float[]{167f, 76f, 92f, 88f, CONTENT_WIDTH - 423f});
                }
            }

            section("F. Refund Activity", 78);
            paragraph("Refund activity for " + cashierName
                    + ". These refunds were processed during the report period and may relate to sales from another period or another cashier. CARD and QR refunds do not reduce System Cash.", 9, MUTED);
            if (report.refundsProcessed().isEmpty()) {
                paragraph("No refunds were processed by " + cashierName + " during this report period.", 10, MUTED);
            } else {
                List<List<String>> refundRows = new ArrayList<>();
                for (CashierReportSnapshot.RefundReportLine refund : report.refundsProcessed()) {
                    refundRows.add(row(
                            refund.auditId().toString(),
                            DATE_TIME.format(refund.timestamp()),
                            value(refund.invoice()),
                            refund.paymentMethod().name(),
                            money(refund.amount())
                    ));
                }
                table(List.of("Event", "Refund time", "Original invoice", "Payment", "Refund amount"), refundRows,
                        new float[]{52f, 128f, 142f, 75f, CONTENT_WIDTH - 397f});
            }

            if (report.legacyUnitSplitsUnavailable()) {
                spacer(8);
                paragraph("Historical note: some refunds do not have a regular/discounted unit split. Their financial totals and total returned quantities remain included.", 9, MUTED);
            }
        }

        private void finish() throws IOException {
            closeStream();
            int pages = document.getNumberOfPages();
            for (int index = 0; index < pages; index++) {
                PDPage page = document.getPage(index);
                try (PDPageContentStream footer = new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    String text = "Report #" + reportId + "    FINAL / SUBMITTED    Page " + (index + 1) + " of " + pages;
                    float width = stringWidth(text, regular, 8);
                    footer.setNonStrokingColor(MUTED);
                    footer.beginText();
                    footer.setFont(regular, 8);
                    footer.newLineAtOffset((PAGE_SIZE.getWidth() - width) / 2, 24);
                    footer.showText(text);
                    footer.endText();
                }
            }
        }

        private void newPage(boolean continuation) throws IOException {
            closeStream();
            PDPage page = new PDPage(PAGE_SIZE);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = TOP;
            if (continuation) {
                drawText("LOCALMART POS", MARGIN, y, bold, 8, ACCENT);
                String right = "Cashier Sale Report #" + reportId;
                drawRight(right, y, regular, 8, MUTED);
                y -= 11;
                horizontalLine(ACCENT, 1f);
                y -= 10;
            }
        }

        private void closeStream() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        private void documentHeader(SavedReportView saved, CashierReportSnapshot report) throws IOException {
            drawText("LOCALMART POS", MARGIN, y, bold, 9, ACCENT);
            drawRight("REPORT #" + saved.id(), y, bold, 9, MUTED);
            y -= 23;
            drawText("CASHIER SALE REPORT", MARGIN, y, bold, 21, INK);
            y -= 18;
            drawText("Final cashier financial report", MARGIN, y, regular, 9, MUTED);
            drawRight("FINAL / SUBMITTED", y, bold, 9, ACCENT);
            y -= 17;
            String period = DATE.format(saved.startDate()) + " - " + DATE.format(saved.endDate());
            drawText("Inclusive period: " + period, MARGIN, y, regular, 8.5f, MUTED);
            drawRight("Cashier: " + value(report.cashierName()), y, regular, 8.5f, MUTED);
            y -= 13;
            horizontalLine(ACCENT, 1.2f);
            y -= 3;
        }

        private void section(String heading, float keepWithNext) throws IOException {
            ensure(31 + keepWithNext);
            spacer(9);
            stream.setNonStrokingColor(ACCENT);
            stream.addRect(MARGIN, y - 11, 3, 13);
            stream.fill();
            drawText(heading, MARGIN + 9, y - 8, bold, 12, INK);
            y -= 20;
        }

        private void subheading(String heading, float keepWithNext) throws IOException {
            ensure(22 + keepWithNext);
            spacer(5);
            line(heading, bold, 9, ACCENT, 14);
        }

        private void paragraph(String text, float size, Color color) throws IOException {
            List<String> lines = wrap(text, regular, size, CONTENT_WIDTH);
            ensure(lines.size() * (size + 3) + 2);
            for (String line : lines) {
                drawText(line, MARGIN, y, regular, size, color);
                y -= size + 3;
            }
            spacer(2);
        }

        private void line(String text, PDFont font, float size, Color color, float advance) throws IOException {
            ensure(advance);
            drawText(text, MARGIN, y, font, size, color);
            y -= advance;
        }

        private void horizontalLine(Color color, float width) throws IOException {
            stream.setStrokingColor(color);
            stream.setLineWidth(width);
            stream.moveTo(MARGIN, y);
            stream.lineTo(PAGE_SIZE.getWidth() - MARGIN, y);
            stream.stroke();
        }

        private void table(List<String> headers, List<List<String>> rows, float[] widths) throws IOException {
            filledTable(headers, rows, widths, HEADER_FILL);
        }

        private void filledTable(List<String> headers, List<List<String>> rows, float[] widths, Color headerFill) throws IOException {
            validateColumns(headers, widths);
            float requiredHeight = rowHeight(headers, widths, bold, 8f);
            if (!rows.isEmpty()) {
                validateColumns(rows.getFirst(), widths);
                requiredHeight += rowHeight(rows.getFirst(), widths, regular, 8.5f);
            }
            ensure(requiredHeight);
            drawTableRow(headers, widths, true, headerFill);
            for (List<String> row : rows) {
                validateColumns(row, widths);
                float height = rowHeight(row, widths, regular, 8.5f);
                if (y - height < BOTTOM) {
                    newPage(true);
                    drawTableRow(headers, widths, true, headerFill);
                }
                drawTableRow(row, widths, false, Color.WHITE);
            }
            spacer(4);
        }

        private void drawTableRow(List<String> cells, float[] widths, boolean header, Color fill) throws IOException {
            PDFont font = header ? bold : regular;
            float size = header ? 8f : 8.5f;
            float height = rowHeight(cells, widths, font, size);
            if (y - height < BOTTOM) {
                newPage(true);
            }
            float x = MARGIN;
            for (int index = 0; index < cells.size(); index++) {
                stream.setNonStrokingColor(fill);
                stream.addRect(x, y - height, widths[index], height);
                stream.fill();
                stream.setStrokingColor(BORDER);
                stream.setLineWidth(0.45f);
                stream.addRect(x, y - height, widths[index], height);
                stream.stroke();
                List<String> lines = wrap(value(cells.get(index)), font, size, widths[index] - 8);
                float textY = y - 10;
                for (String line : lines) {
                    float textX = x + 4;
                    if (!header && lines.size() == 1 && line.startsWith("THB ")) {
                        textX = x + widths[index] - 4 - stringWidth(line, font, size);
                    }
                    drawText(line, textX, textY, font, size, INK);
                    textY -= size + 2;
                }
                x += widths[index];
            }
            y -= height;
        }

        private float rowHeight(List<String> cells, float[] widths, PDFont font, float size) throws IOException {
            int maxLines = 1;
            for (int index = 0; index < cells.size(); index++) {
                maxLines = Math.max(maxLines, wrap(value(cells.get(index)), font, size, widths[index] - 8).size());
            }
            return Math.max(18f, 6f + maxLines * (size + 2));
        }

        private void validateColumns(List<String> cells, float[] widths) {
            if (cells.size() != widths.length) {
                throw new IllegalArgumentException("PDF table column mismatch");
            }
        }

        private void ensure(float requiredHeight) throws IOException {
            if (y - requiredHeight < BOTTOM) {
                newPage(true);
            }
        }

        private void spacer(float amount) {
            y -= amount;
        }

        private void drawText(String text, float x, float baseline, PDFont font, float size, Color color) throws IOException {
            stream.setNonStrokingColor(color);
            float cursor = x;
            for (FontRun run : fontRuns(value(text), font)) {
                stream.beginText();
                stream.setFont(run.font(), size);
                stream.newLineAtOffset(cursor, baseline);
                stream.showText(run.text());
                stream.endText();
                cursor += run.font().getStringWidth(run.text()) / 1000f * size;
            }
        }

        private void drawRight(String text, float baseline, PDFont font, float size, Color color) throws IOException {
            drawText(text, PAGE_SIZE.getWidth() - MARGIN - stringWidth(text, font, size), baseline, font, size, color);
        }

        private List<String> wrap(String text, PDFont font, float size, float maxWidth) throws IOException {
            String normalized = value(text).replace('\n', ' ').replace('\r', ' ');
            if (stringWidth(normalized, font, size) <= maxWidth) {
                return List.of(normalized);
            }
            BreakIterator iterator = BreakIterator.getLineInstance(Locale.forLanguageTag("th-TH"));
            iterator.setText(normalized);
            List<String> pieces = new ArrayList<>();
            int start = iterator.first();
            for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
                pieces.add(normalized.substring(start, end));
            }
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String piece : pieces) {
                String candidate = current + piece;
                if (current.isEmpty() || stringWidth(candidate, font, size) <= maxWidth) {
                    current.append(piece);
                    continue;
                }
                lines.add(current.toString().stripTrailing());
                current.setLength(0);
                if (stringWidth(piece, font, size) <= maxWidth) {
                    current.append(piece.stripLeading());
                } else {
                    splitLongPiece(piece, font, size, maxWidth, lines, current);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString().stripTrailing());
            }
            return lines.isEmpty() ? List.of("") : lines;
        }

        private void splitLongPiece(String piece, PDFont font, float size, float maxWidth,
                                    List<String> lines, StringBuilder current) throws IOException {
            BreakIterator characters = BreakIterator.getCharacterInstance(Locale.forLanguageTag("th-TH"));
            characters.setText(piece);
            int start = characters.first();
            for (int end = characters.next(); end != BreakIterator.DONE; start = end, end = characters.next()) {
                String grapheme = piece.substring(start, end);
                if (!current.isEmpty() && stringWidth(current + grapheme, font, size) > maxWidth) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                current.append(grapheme);
            }
        }

        private float stringWidth(String text, PDFont font, float size) throws IOException {
            float width = 0;
            for (FontRun run : fontRuns(value(text), font)) {
                width += run.font().getStringWidth(run.text()) / 1000f * size;
            }
            return width;
        }

        private List<FontRun> fontRuns(String text, PDFont requestedFont) throws IOException {
            List<FontRun> runs = new ArrayList<>();
            PDFont currentFont = null;
            StringBuilder currentText = new StringBuilder();
            for (int offset = 0; offset < text.length();) {
                int codePoint = text.codePointAt(offset);
                PDFont selected = selectFont(codePoint, requestedFont);
                if (currentFont != null && currentFont != selected) {
                    runs.add(new FontRun(currentFont, currentText.toString()));
                    currentText.setLength(0);
                }
                currentFont = selected;
                currentText.appendCodePoint(codePoint);
                offset += Character.charCount(codePoint);
            }
            if (currentFont != null) {
                runs.add(new FontRun(currentFont, currentText.toString()));
            }
            return runs;
        }

        private PDFont selectFont(int codePoint, PDFont requestedFont) throws IOException {
            PDType0Font latin = requestedFont == bold ? bold : regular;
            PDType0Font thai = requestedFont == bold ? thaiBold : thaiRegular;
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.THAI) {
                return thai;
            }
            if (latin.hasGlyph(codePoint)) {
                return latin;
            }
            if (thai.hasGlyph(codePoint)) {
                return thai;
            }
            throw new IOException("Bundled PDF fonts do not support Unicode code point U+"
                    + Integer.toHexString(codePoint).toUpperCase(Locale.ROOT));
        }

        private record FontRun(PDFont font, String text) {}

        private static List<String> row(String... values) {
            return List.of(values);
        }

        private static String value(Object value) {
            if (value == null) {
                return "N/A";
            }
            String text = value.toString();
            return text.isBlank() ? "N/A" : text;
        }

        private static String money(BigDecimal amount) {
            if (amount == null) {
                return "N/A";
            }
            return "THB " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }

        private static String split(Integer value) {
            return value == null ? "Unavailable" : value.toString();
        }

        private static String displayType(ReportType type) {
            return switch (type) {
                case DAILY -> "Daily";
                case WEEKLY -> "Weekly";
                case CUSTOM -> "Custom";
            };
        }
    }
}
