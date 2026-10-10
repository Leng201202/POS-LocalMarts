package com.localmarts.pos.report;

import com.localmarts.pos.sale.entity.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static com.localmarts.pos.report.ReportFixtures.*;

class CashierSaleReportCalculatorTest {
    final CashierSaleReportCalculator calculator = new CashierSaleReportCalculator();
    final RefundAuditReader reader = new RefundAuditReader();
    @Test void emptyReportHasZeroCashNoAverageOrBestSeller() {
        var r = calculator.calculate(2L,"cashier","Cashier",all(),List.of(),List.of());
        assertThat(r.summary().transactions()).isZero();assertThat(r.summary().averageSaleValue()).isNull();
        assertThat(r.summary().bestSeller()).isNull();assertThat(r.systemCash()).isEqualByComparingTo("0");
        assertThat(r.payments()).hasSize(3);
    }
    @Test void allPaymentReceiptsAndHistoricalDiscountCountedOnceAndAverageRounded() {
        var a=sale(1,2,PaymentMethod.CASH);var b=sale(2,2,PaymentMethod.CARD);var c=sale(3,2,PaymentMethod.QR);
        c.setTotalAmount(money("95.02"));b.setPointDiscount(money("5"));
        var r=calculator.calculate(2L,"cashier","Cashier",all(),List.of(a,b,c),List.of());
        assertThat(r.summary().transactions()).isEqualTo(3);
        assertThat(r.summary().salesAmount()).isEqualByComparingTo("285.02");
        assertThat(r.summary().averageSaleValue()).isEqualByComparingTo("95.01");
        assertThat(r.summary().totalDiscount()).isEqualByComparingTo("15.00");
        assertThat(r.systemCash()).isEqualByComparingTo("95.00");
        assertThat(r.payments()).extracting(CashierReportSnapshot.PaymentSummaryRow::receipts)
                .containsExactly(money("95.00"),money("95.00"),money("95.02"));
    }
    @Test void partialRefundIncludesOriginalSaleAndLegacyFinancialAmounts() {
        var sale=sale(1,2,PaymentMethod.CASH);sale.setSaleStatus(SaleStatus.PARTIALLY_REFUNDED);sale.getItems().getFirst().setRefundQuantity(3);
        var event=reader.read(audit(49,sale,2,true),sale);
        var r=calculator.calculate(2L,"cashier","Cashier",all(),List.of(sale),List.of(event));
        assertThat(r.summary().salesAmount()).isEqualByComparingTo("95");
        assertThat(r.summary().refundAmount()).isEqualByComparingTo("72.50");
        assertThat(r.summary().refundCount()).isEqualTo(1);
        assertThat(r.systemCash()).isEqualByComparingTo("22.50");
        assertThat(r.sales().getFirst().netQuantity()).isEqualTo(1);
        assertThat(r.sales().getFirst().status()).isEqualTo(SaleStatus.PARTIALLY_REFUNDED);
        assertThat(r.legacyUnitSplitsUnavailable()).isTrue();
    }
    @Test void cardQrRefundsVisibleButDoNotReduceCash() {
        var a=sale(1,2,PaymentMethod.CARD);var b=sale(2,2,PaymentMethod.QR);
        var r=calculator.calculate(2L,"cashier","Cashier",all(),List.of(a,b),List.of(reader.read(audit(49,a,2,false),a),reader.read(audit(50,b,2,false),b)));
        assertThat(r.paymentTotal().refunds()).isEqualByComparingTo("145.00");
        assertThat(r.systemCash()).isEqualByComparingTo("0");
        assertThat(r.refundsProcessed()).hasSize(2);
    }
    @Test void laterRefundDoesNotChangeHistoricalStatusUnitsOrCash() {
        var sale=sale(1,2,PaymentMethod.CASH);sale.setSaleStatus(SaleStatus.PARTIALLY_REFUNDED);sale.getItems().getFirst().setRefundQuantity(3);
        var event=reader.read(audit(49,sale,2,false),sale);
        var p=period(LocalDate.of(2026,9,3),LocalDate.of(2026,9,3));
        var r=calculator.calculate(2L,"cashier","Cashier",p,List.of(sale),List.of(event));
        assertThat(r.systemCash()).isEqualByComparingTo("95");assertThat(r.summary().refundCount()).isZero();
        assertThat(r.sales().getFirst().status()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(r.sales().getFirst().netQuantity()).isEqualTo(4);
    }
    @Test void crossCashierCashRefundBelongsOnlyToProcessorAndCanMakeCashNegative() {
        var sale=sale(1,2,PaymentMethod.CASH);var event=reader.read(audit(49,sale,3,false),sale);
        var seller=calculator.calculate(2L,"cashier","Seller",all(),List.of(sale),List.of(event));
        assertThat(seller.systemCash()).isEqualByComparingTo("95");
        assertThat(seller.summary().refundAmount()).isEqualByComparingTo("72.50");
        assertThat(seller.refundsProcessed()).isEmpty();
        var processor=calculator.calculate(3L,"cashier","Processor",all(),List.of(sale),List.of(event));
        assertThat(processor.summary().transactions()).isZero();assertThat(processor.sales()).isEmpty();
        assertThat(processor.systemCash()).isEqualByComparingTo("-72.50");assertThat(processor.refundsProcessed()).hasSize(1);
    }
    @Test void fullReturnExcludedFromBestSellerAndTieUsesStableProductIdAndSnapshots() {
        var a=sale(1,2,PaymentMethod.CASH);var b=sale(2,2,PaymentMethod.CASH);var c=sale(3,2,PaymentMethod.CASH);
        var audit=audit(49,a,2,false);set(audit,"item.10.refundQuantity","4");set(audit,"item.10.discountedUnitsReturned","2");set(audit,"item.10.lineRefundAmount","95");set(audit,"refundTotal","95");
        var event=reader.read(audit,a);a.setSaleStatus(SaleStatus.REFUNDED);
        var r=calculator.calculate(2L,"cashier","Cashier",all(),List.of(c,a,b),List.of(event));
        assertThat(r.summary().bestSeller().productId()).isEqualTo(2L);
        assertThat(r.summary().bestSeller().name()).isEqualTo("Snapshot 2");
        assertThat(r.sales().getFirst().status()).isEqualTo(SaleStatus.REFUNDED);
        assertThat(calculator.calculate(2L,"cashier","Cashier",all(),List.of(a),List.of(event)).summary().bestSeller()).isNull();
    }
    @Test void bestSellerUsesNetUnitsAndGroupsByProductId() {
        var a=sale(1,2,PaymentMethod.CASH);var b=sale(2,2,PaymentMethod.CASH);
        var event=reader.read(audit(49,a,2,false),a);
        assertThat(calculator.calculate(2L,"cashier","Cashier",all(),List.of(a,b),List.of(event)).summary().bestSeller().productId()).isEqualTo(2L);
        b.getItems().getFirst().setProduct(a.getItems().getFirst().getProduct());
        assertThat(calculator.calculate(2L,"cashier","Cashier",all(),List.of(a,b),List.of(event)).summary().bestSeller().netUnits()).isEqualTo(5);
    }
    @Test void excludesUnpaidVoidedOtherCashierAndAtCutoffSales() {
        var a=sale(1,2,PaymentMethod.CASH);a.setPaymentStatus(PaymentStatus.PENDING);
        var b=sale(2,2,PaymentMethod.CASH);b.setSaleStatus(SaleStatus.VOIDED);
        var c=sale(3,3,PaymentMethod.CASH);var d=sale(4,2,PaymentMethod.CASH);d.setTimestamp(all().cutoff());
        assertThat(calculator.calculate(2L,"cashier","Cashier",all(),List.of(a,b,c,d),List.of()).summary().transactions()).isZero();
    }
    @Test void eventExactlyAtCutoffIsExcludedAndDuplicateIdentityFails() {
        var sale=sale(1,2,PaymentMethod.CASH);var audit=audit(49,sale,2,false);audit.setTimestamp(all().cutoff());
        var event=reader.read(audit,sale);
        assertThat(calculator.calculate(2L,"cashier","Cashier",all(),List.of(sale),List.of(event)).summary().refundCount()).isZero();
        assertThatThrownBy(() -> calculator.calculate(2L,"cashier","Cashier",all(),List.of(sale),List.of(event,event))).hasMessageContaining("duplicate refund");
    }
}
