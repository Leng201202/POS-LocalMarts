package com.localmarts.pos.report;

import com.localmarts.pos.sale.entity.PaymentMethod;
import com.localmarts.pos.sale.entity.SaleStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Detached, immutable session preview; never holds JPA entities. */
public record CashierReportSnapshot(UUID token, Long ownerActorId, String cashierUsername, String cashierName,
        ReportPeriod period, SalesSummary summary, List<PaymentSummaryRow> payments,
        PaymentSummaryRow paymentTotal, BigDecimal systemCash, List<SaleReportLine> sales,
        List<RefundReportLine> refundsProcessed, boolean legacyUnitSplitsUnavailable) {
    public CashierReportSnapshot {
        payments = List.copyOf(payments);
        sales = List.copyOf(sales);
        refundsProcessed = List.copyOf(refundsProcessed);
    }
    public record SalesSummary(long transactions, BigDecimal salesAmount, BigDecimal averageSaleValue,
            BigDecimal totalDiscount, long refundCount, BigDecimal refundAmount,
            BigDecimal retainedAmount, ProductReportLine bestSeller) {}
    public record PaymentSummaryRow(String method, BigDecimal receipts, BigDecimal refunds, BigDecimal net) {}
    public record ProductReportLine(Long productId, String name, long netUnits) {}
    public record SaleReportLine(Long saleId, String invoice, LocalDateTime timestamp, String customer,
            PaymentMethod paymentMethod, BigDecimal chargedAmount, BigDecimal refundAmount,
            BigDecimal retainedAmount, SaleStatus status, long originalQuantity, long returnedQuantity,
            long netQuantity, List<RefundReportLine> refunds, List<SaleItemReportLine> items) {
        public SaleReportLine { refunds = List.copyOf(refunds); items = List.copyOf(items); }
    }
    public record SaleItemReportLine(Long saleItemId, Long productId, String productName, String barcode,
            String size, String unit, long originalQuantity, long returnedQuantity, long netQuantity,
            BigDecimal unitPrice, BigDecimal chargedAmount) {}
    public record RefundReportLine(Long auditId, Long saleId, Long actorId, LocalDateTime timestamp,
            String invoice, PaymentMethod paymentMethod, BigDecimal amount, List<RefundItemLine> items) {
        public RefundReportLine { items = List.copyOf(items); }
    }
    public record RefundItemLine(Long saleItemId, String productName, int quantity, BigDecimal amount,
            Integer regularUnits, Integer discountedUnits) {}
}
