package com.localmarts.pos.report;

import com.localmarts.pos.sale.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.localmarts.pos.report.ReportFixtures.*;

class RefundAuditReaderTest {
    private final RefundAuditReader reader = new RefundAuditReader();
    @Test void reconstructsStructuredEventOnceAndUsesLinkedPaymentNotCashRefundKey() {
        var sale = sale(1,2,PaymentMethod.QR); var audit = audit(49,sale,3,false);
        add(audit,"refundNote","arbitrary note"); add(audit,"item.10.breakdown.0.quantity","3");
        var event = reader.read(audit,sale);
        assertThat(event.amount()).isEqualByComparingTo("72.50");
        assertThat(event.paymentMethod()).isEqualTo(PaymentMethod.QR);
        assertThat(event.actorId()).isEqualTo(3L);
        assertThat(event.items()).hasSize(1);
        assertThat(event.items().getFirst().regularUnits()).isEqualTo(2);
        assertThat(event.items().getFirst().discountedUnits()).isEqualTo(1);
    }
    @Test void legacyMissingSplitsPreservesMoneyAndQuantityWithoutInference() {
        var sale = sale(1,2,PaymentMethod.CASH); var event = reader.read(audit(49,sale,2,true),sale);
        assertThat(event.amount()).isEqualByComparingTo("72.50");
        assertThat(event.items().getFirst().quantity()).isEqualTo(3);
        assertThat(event.items().getFirst().regularUnits()).isNull();
        assertThat(event.items().getFirst().discountedUnits()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"refundTotal","item.10.saleItemId","item.10.refundQuantity","item.10.lineRefundAmount"})
    void missingFinancialFieldFailsClearly(String field) {
        var sale = sale(1,2,PaymentMethod.CASH);var audit = audit(49,sale,2,true);
        audit.getDetails().removeIf(d -> d.getFieldName().equals(field));
        assertThatThrownBy(() -> reader.read(audit,sale)).isInstanceOf(ReportDataException.class).hasMessageContaining("Audit 49").hasMessageContaining(field);
    }
    @Test void missingLinksActorTimestampPaymentFail() {
        var sale = sale(1,2,PaymentMethod.CASH);var audit = audit(49,sale,2,true);
        assertThatThrownBy(() -> reader.read(audit,null)).hasMessageContaining("linked Sale");
        audit.setActor(null);assertThatThrownBy(() -> reader.read(audit,sale)).hasMessageContaining("actor");
        audit.setActor(2L);audit.setTimestamp(null);assertThatThrownBy(() -> reader.read(audit,sale)).hasMessageContaining("timestamp");
        audit.setTimestamp(REFUND_TIME);sale.setPaymentMethod(null);assertThatThrownBy(() -> reader.read(audit,sale)).hasMessageContaining("payment method");
    }
    @Test void mismatchedAmountsDuplicateFieldsAndWrongItemFail() {
        var sale = sale(1,2,PaymentMethod.CASH);var audit = audit(49,sale,2,false);
        set(audit,"refundTotal","70");assertThatThrownBy(() -> reader.read(audit,sale)).hasMessageContaining("reconcile");
        set(audit,"refundTotal","72.50");set(audit,"item.10.saleItemId","999");
        assertThatThrownBy(() -> reader.read(audit,sale)).hasMessageContaining("SaleItem");
        set(audit,"item.10.saleItemId","10");add(audit,"refundTotal","72.50");
        assertThatThrownBy(() -> reader.read(audit,sale)).hasMessageContaining("duplicate");
    }
    @Test void invalidQuantityMoneyPrecisionAndSplitFail() {
        var sale = sale(1,2,PaymentMethod.CASH);var audit = audit(49,sale,2,false);
        set(audit,"item.10.refundQuantity","0");assertThatThrownBy(() -> reader.read(audit,sale)).hasMessageContaining("quantity");
        set(audit,"item.10.refundQuantity","3");set(audit,"refundTotal","72.501");
        assertThatThrownBy(() -> reader.read(audit,sale)).isInstanceOf(ReportDataException.class);
        set(audit,"refundTotal","72.50");set(audit,"item.10.regularUnitsReturned","3");
        assertThatThrownBy(() -> reader.read(audit,sale)).hasMessageContaining("split");
    }
}
