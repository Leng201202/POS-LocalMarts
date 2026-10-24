package com.localmarts.pos.report.cashier.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import com.localmarts.pos.sale.entity.PaymentMethod;
import java.util.List;
import static com.localmarts.pos.report.cashier.fixture.ReportFixtures.*;
import static org.assertj.core.api.Assertions.*;

class ReportSnapshotCodecTest {
    final ReportSnapshotCodec codec = new ReportSnapshotCodec(JsonMapper.builder().build());
    @Test void roundTripPreservesExactDecimalsIdentityItemsAndLegacyUnavailableSplits() {
        var sale = sale(1,2,PaymentMethod.CASH);
        sale.setCustomer(com.localmarts.pos.customer.entity.Customer.builder().name("Member Before").build());
        var audit = audit(49,sale,2,true);
        var preview = new CashierSaleReportCalculator().calculate(2L,"username-before","Name Before",all(),List.of(sale),
                List.of(new RefundAuditReader().read(audit,sale)));
        String json = codec.encode(preview);
        assertThat(json).contains("\"snapshotVersion\":1", "Asia/Bangkok", "THB", "username-before");
        var decoded = codec.decode(1,json);
        assertThat(decoded).isEqualTo(preview);
        assertThat(decoded.systemCash()).isEqualTo(money("22.50"));
        assertThat(decoded.sales().getFirst().items().getFirst().productName()).isEqualTo("Snapshot 1");
        assertThat(decoded.sales().getFirst().refunds().getFirst().items().getFirst().regularUnits()).isNull();
        assertThat(decoded.legacyUnitSplitsUnavailable()).isTrue();
        sale.setTotalAmount(money("999"));sale.setSaleStatus(com.localmarts.pos.sale.entity.SaleStatus.REFUNDED);
        sale.getItems().getFirst().setProductNameSnapshot("Changed");sale.getItems().getFirst().getProduct().setName("Changed product");
        sale.getCashier().setFullName("Changed cashier");
        sale.getCustomer().setName("Changed customer");
        sale.getItems().getFirst().setRefundQuantity(4);
        set(audit,"refundTotal","95.00");
        assertThat(codec.decode(1,json)).isEqualTo(preview);
    }
    @Test void unsupportedVersionsAndMalformedSnapshotFailClearly() {
        assertThatThrownBy(() -> codec.decode(2,"{}")).hasMessageContaining("Unsupported");
        assertThatThrownBy(() -> codec.decode(1,"{\"snapshotVersion\":2}")).hasMessageContaining("Unsupported");
        assertThatThrownBy(() -> codec.decode(1,"not json")).hasMessageContaining("cannot be read");
    }
}
