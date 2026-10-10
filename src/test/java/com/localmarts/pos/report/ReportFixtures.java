package com.localmarts.pos.report;

import com.localmarts.pos.audit.entity.*;
import com.localmarts.pos.audit.utils.*;
import com.localmarts.pos.product.entity.Product;
import com.localmarts.pos.sale.entity.*;
import com.localmarts.pos.user.entity.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

class ReportFixtures {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T08:00:00Z"), ZoneOffset.UTC);
    static final LocalDateTime SALE_TIME = LocalDateTime.of(2026, 9, 3, 10, 0);
    static final LocalDateTime REFUND_TIME = LocalDateTime.of(2026, 9, 4, 11, 0);
    static BigDecimal money(String amount) { return new BigDecimal(amount); }
    static User user(long id) { return User.builder().id(id).fullName("Cashier " + id).username("cashier" + id).password("test").role(Role.CASHIER).build(); }
    static Sale sale(long id, long actor, PaymentMethod method) {
        Sale sale = Sale.builder().id(id).cashier(user(actor)).invoiceNo("INV-" + id).timestamp(SALE_TIME)
                .paymentMethod(method).paymentStatus(PaymentStatus.PAID).saleStatus(SaleStatus.COMPLETED)
                .subtotal(money("100.00")).totalAmount(money("95.00")).paidAmount(money("100.00"))
                .changeAmount(money("5.00")).totalDiscount(money("5.00")).pointDiscount(BigDecimal.ZERO).build();
        sale.addItem(SaleItem.builder().id(id * 10).product(Product.builder().id(id).name("Current name")
                .barcode("P"+id).sellingPrice(money("25")).costPrice(money("10")).stockQuantity(10)
                .minimumStock(0).discountPercent(BigDecimal.ZERO).build())
                .productNameSnapshot("Snapshot " + id).barcodeSnapshot("P"+id).quantity(4).refundQuantity(0)
                .unitPrice(money("25")).grossSubtotal(money("100")).netTotal(money("95"))
                .discountAmount(money("5")).discountPercentSnapshot(money("10"))
                .costPriceSnapshot(money("10")).saleStatus(SaleStatus.COMPLETED).build());
        return sale;
    }
    static Audit audit(long id, Sale sale, long actor, boolean legacy) {
        Audit audit = Audit.builder().id(id).actor(actor).rowId(sale.getId()).entityName("Sale")
                .action(AuditAction.UPDATE).status(AuditStatus.SUCCESS).timestamp(REFUND_TIME).build();
        add(audit, "refundTotal", "72.50");
        String prefix = "item." + sale.getItems().getFirst().getId() + ".";
        add(audit, prefix + "saleItemId", sale.getItems().getFirst().getId().toString());
        add(audit, prefix + "refundQuantity", "3");
        add(audit, prefix + "lineRefundAmount", "72.50");
        add(audit, "saleStatus", "PARTIALLY_REFUNDED");
        add(audit, prefix + "cashRefundAmount", "72.50");
        if (!legacy) {
            add(audit, prefix + "regularUnitsReturned", "2");
            add(audit, prefix + "discountedUnitsReturned", "1");
        }
        return audit;
    }
    static void add(Audit audit, String field, String value) {
        audit.getDetails().add(AuditDetails.builder().audit(audit).fieldName(field).newData(value).build());
    }
    static void set(Audit audit, String field, String value) {
        audit.getDetails().stream().filter(d -> d.getFieldName().equals(field)).findFirst().orElseThrow().setNewData(value);
    }
    static ReportPeriod period(LocalDate start, LocalDate end) {
        ReportPeriodRequest request = new ReportPeriodRequest(); request.setType(ReportType.CUSTOM);
        request.setStartDate(start); request.setEndDate(end);
        return ReportPeriod.resolve(request, CLOCK);
    }
    static ReportPeriod all() { return period(LocalDate.of(2026,9,3), LocalDate.of(2026,9,4)); }
}
