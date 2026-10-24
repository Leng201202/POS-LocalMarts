package com.localmarts.pos.report.cashier.service;

import com.localmarts.pos.report.cashier.model.CashierReportSnapshot;
import com.localmarts.pos.report.cashier.model.ReportPeriod;
import com.localmarts.pos.report.cashier.model.ReportType;
import com.localmarts.pos.report.cashier.model.SavedReportView;
import com.localmarts.pos.report.common.ReportDataException;

import com.localmarts.pos.sale.entity.PaymentMethod;
import com.localmarts.pos.sale.entity.SaleStatus;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CashierReportPdfServiceTest {
    private final CashierReportSubmissionService submissions = mock(CashierReportSubmissionService.class);
    private final CashierReportPdfService service = new CashierReportPdfService(submissions);

    @Test
    void generatesA4MultiPagePdfFromFrozenSavedValuesWithThaiAndEnglishText() throws Exception {
        SavedReportView saved = savedReport(38);
        when(submissions.view(2L, 8L)).thenReturn(Optional.of(saved));

        CashierReportPdfService.GeneratedPdf generated = service.generate(2L, 8L).orElseThrow();

        assertThat(generated.filename()).isEqualTo("cashier-sale-report-8.pdf");
        assertThat(generated.content()).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        try (PDDocument document = Loader.loadPDF(generated.content())) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            assertThat(document.getPage(0).getMediaBox().getWidth())
                    .isEqualTo(org.apache.pdfbox.pdmodel.common.PDRectangle.A4.getWidth());
            assertThat(document.getPage(0).getMediaBox().getHeight())
                    .isEqualTo(org.apache.pdfbox.pdmodel.common.PDRectangle.A4.getHeight());
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                    .contains("CASHIER SALE REPORT", "Final cashier financial report", "FINAL / SUBMITTED", "REPORT #8")
                    .contains("Cashier Thai English", "สมชาย ใจดี")
                    .contains("ลูกค้าทดสอบ", "น้ำดื่ม", "English Product's")
                    .contains("THB 1234.56", "THB 1100.10", "THB 1125.25", "THB 25.15")
                    .contains("03 Sep 2026 15:00:00", "04 Sep 2026 15:00:00", "05 Sep 2026 12:30:00")
                    .contains("A. Report Information", "B. Sales Summary", "C. Payment Summary")
                    .contains("D. Cash Reconciliation", "E. Sales Details", "F. Refund Activity")
                    .contains("Refund activity for สมชาย ใจดี - Cashier Thai English")
                    .contains("Page 1 of ")
                    .doesNotContain("Refunds Processed by You", "processed by you");

            assertSectionStartsWithTable(document, "D. Cash Reconciliation", "System Cash (Transactions)");
        }
        verify(submissions).view(2L, 8L);
        verifyNoMoreInteractions(submissions);
    }

    @Test
    void ownerScopedLookupMakesMissingAndOtherCashierReportsIndistinguishable() {
        when(submissions.view(2L, 999L)).thenReturn(Optional.empty());
        when(submissions.view(3L, 8L)).thenReturn(Optional.empty());

        assertThat(service.generate(2L, 999L)).isEmpty();
        assertThat(service.generate(3L, 8L)).isEmpty();

        verify(submissions).view(2L, 999L);
        verify(submissions).view(3L, 8L);
        verifyNoMoreInteractions(submissions);
    }

    @Test
    void unsupportedPersistedSnapshotVersionFailsClearly() {
        when(submissions.view(2L, 8L))
                .thenThrow(new ReportDataException("Unsupported saved report snapshot version: 2."));

        assertThatThrownBy(() -> service.generate(2L, 8L))
                .isInstanceOf(ReportDataException.class)
                .hasMessageContaining("Unsupported saved report snapshot version: 2");
    }

    @Test
    void usesSavedCashierNameInProfessionalRefundEmptyState() throws Exception {
        SavedReportView saved = savedReport(0);
        when(submissions.view(2L, 8L)).thenReturn(Optional.of(saved));

        CashierReportPdfService.GeneratedPdf generated = service.generate(2L, 8L).orElseThrow();

        try (PDDocument document = Loader.loadPDF(generated.content())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                    .contains("F. Refund Activity")
                    .contains("No refunds were processed by")
                    .contains("Cashier Thai English during this report period.")
                    .doesNotContain("Refunds Processed by You", "processed by you");
        }
    }

    private SavedReportView savedReport(int saleCount) {
        LocalDateTime generated = LocalDateTime.of(2026, 9, 3, 15, 0);
        ReportPeriod period = new ReportPeriod(
                ReportType.CUSTOM,
                LocalDate.of(2026, 9, 3),
                LocalDate.of(2026, 9, 4),
                generated,
                LocalDateTime.of(2026, 9, 4, 15, 0)
        );
        List<CashierReportSnapshot.SaleReportLine> sales = new ArrayList<>();
        for (int index = 1; index <= saleCount; index++) {
            String product = index == 1 ? "น้ำดื่ม English Product’s" : "English Product " + index;
            CashierReportSnapshot.RefundItemLine refundItem = new CashierReportSnapshot.RefundItemLine(
                    (long) index * 10,
                    product,
                    1,
                    money("5.00"),
                    1,
                    0
            );
            CashierReportSnapshot.RefundReportLine refund = new CashierReportSnapshot.RefundReportLine(
                    (long) 1000 + index,
                    (long) index,
                    2L,
                    LocalDateTime.of(2026, 9, 4, 10, 0).plusMinutes(index),
                    "INV-FROZEN-" + index,
                    PaymentMethod.CASH,
                    money("5.00"),
                    List.of(refundItem)
            );
            CashierReportSnapshot.SaleItemReportLine item = new CashierReportSnapshot.SaleItemReportLine(
                    (long) index * 10,
                    (long) index,
                    product,
                    "BAR-" + index,
                    "500ml",
                    "Bottle",
                    3,
                    1,
                    2,
                    money("25.00"),
                    money("75.00")
            );
            sales.add(new CashierReportSnapshot.SaleReportLine(
                    (long) index,
                    "INV-FROZEN-" + index,
                    LocalDateTime.of(2026, 9, 3, 9, 0).plusMinutes(index),
                    index == 1 ? "ลูกค้าทดสอบ" : "English Customer " + index,
                    PaymentMethod.CASH,
                    money("75.00"),
                    money("5.00"),
                    money("70.00"),
                    SaleStatus.PARTIALLY_REFUNDED,
                    3,
                    1,
                    2,
                    List.of(refund),
                    List.of(item)
            ));
        }
        List<CashierReportSnapshot.RefundReportLine> processed = sales.isEmpty()
                ? List.of()
                : List.of(sales.getFirst().refunds().getFirst());
        CashierReportSnapshot snapshot = new CashierReportSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000008"),
                2L,
                "cashier.frozen",
                "สมชาย ใจดี - Cashier Thai English",
                period,
                new CashierReportSnapshot.SalesSummary(
                        saleCount,
                        money("1234.56"),
                        money("32.49"),
                        money("45.67"),
                        1,
                        money("5.00"),
                        money("1229.56"),
                        new CashierReportSnapshot.ProductReportLine(1L, "น้ำดื่ม English Product", 12)
                ),
                List.of(
                        new CashierReportSnapshot.PaymentSummaryRow("CASH", money("900.00"), money("5.00"), money("895.00")),
                        new CashierReportSnapshot.PaymentSummaryRow("CARD", money("334.56"), money("0.00"), money("334.56"))
                ),
                new CashierReportSnapshot.PaymentSummaryRow("TOTAL", money("1234.56"), money("5.00"), money("1229.56")),
                money("999.99"),
                sales,
                processed,
                false
        );
        return new SavedReportView(
                8L,
                ReportType.CUSTOM,
                period.startDate(),
                period.endDate(),
                generated,
                LocalDateTime.of(2026, 9, 5, 12, 30),
                money("1100.10"),
                money("1125.25"),
                money("25.15"),
                snapshot
        );
    }

    private BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    private void assertSectionStartsWithTable(PDDocument document, String section, String firstRow) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        boolean found = false;
        for (int page = 1; page <= document.getNumberOfPages(); page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            String pageText = stripper.getText(document);
            if (pageText.contains(section)) {
                assertThat(pageText).contains(firstRow);
                found = true;
                break;
            }
        }
        assertThat(found).as("section should be present in the PDF").isTrue();
    }
}
