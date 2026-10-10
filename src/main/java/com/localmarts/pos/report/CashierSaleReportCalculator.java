package com.localmarts.pos.report;

import com.localmarts.pos.sale.entity.*;
import org.springframework.stereotype.Component;
import java.math.*;
import java.util.*;
import static com.localmarts.pos.report.CashierReportSnapshot.*;

@Component
public class CashierSaleReportCalculator {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    public CashierReportSnapshot calculate(Long actorId, String cashierUsername, String cashierName, ReportPeriod period,
            List<Sale> candidates, List<RefundReportLine> events) {
        List<Sale> selected = candidates.stream()
                .filter(s -> s.getCashier() != null && Objects.equals(s.getCashier().getId(), actorId))
                .filter(s -> s.getPaymentStatus() == PaymentStatus.PAID)
                .filter(s -> EnumSet.of(SaleStatus.COMPLETED, SaleStatus.PARTIALLY_REFUNDED, SaleStatus.REFUNDED).contains(s.getSaleStatus()))
                .filter(s -> period.contains(s.getTimestamp()))
                .sorted(Comparator.comparing(Sale::getTimestamp).thenComparing(Sale::getId)).toList();
        Map<Long, RefundReportLine> unique = new LinkedHashMap<>();
        for (RefundReportLine event : events) {
            if (unique.putIfAbsent(event.auditId(), event) != null)
                throw new ReportDataException("Cannot generate preview: duplicate refund event " + event.auditId() + ".");
        }
        List<RefundReportLine> beforeCutoff = unique.values().stream()
                .filter(r -> r.timestamp().isBefore(period.cutoff()))
                .sorted(Comparator.comparing(RefundReportLine::timestamp).thenComparing(RefundReportLine::auditId)).toList();
        List<RefundReportLine> processed = beforeCutoff.stream()
                .filter(r -> Objects.equals(r.actorId(), actorId) && period.contains(r.timestamp())).toList();
        Map<PaymentMethod, BigDecimal> receipts = new EnumMap<>(PaymentMethod.class);
        Map<PaymentMethod, BigDecimal> refunds = new EnumMap<>(PaymentMethod.class);
        Map<Long, ProductReportLine> products = new TreeMap<>();
        List<SaleReportLine> lines = new ArrayList<>();
        BigDecimal amount = ZERO, discount = ZERO, refunded = ZERO;
        long refundCount = 0;
        for (Sale sale : selected) {
            BigDecimal charged = requiredMoney(sale.getTotalAmount(), sale.getId());
            if (sale.getPaymentMethod() == null) throw invalidSale(sale, "missing payment method");
            // Retained CASH receipt excludes change. The checkout invariant requires this to equal totalAmount.
            BigDecimal receipt = charged;
            if (sale.getPaymentMethod() == PaymentMethod.CASH) {
                receipt = requiredMoney(sale.getPaidAmount(), sale.getId()).subtract(requiredMoney(sale.getChangeAmount(), sale.getId()));
                if (receipt.compareTo(charged) != 0) throw invalidSale(sale, "paid amount minus change differs from charged amount");
            }
            receipts.merge(sale.getPaymentMethod(), receipt, BigDecimal::add);
            amount = amount.add(charged);
            discount = discount.add(requiredMoney(sale.getTotalDiscount(), sale.getId()));
            List<RefundReportLine> applicable = beforeCutoff.stream().filter(r -> r.saleId().equals(sale.getId())).toList();
            BigDecimal refund = applicable.stream().map(RefundReportLine::amount).reduce(ZERO, BigDecimal::add);
            if (refund.compareTo(charged) > 0) throw invalidSale(sale, "refund exceeds charged amount");
            Map<Long, Long> returned = new HashMap<>();
            for (RefundReportLine event : applicable)
                for (RefundItemLine item : event.items()) returned.merge(item.saleItemId(), (long) item.quantity(), Long::sum);
            long originalUnits = 0, returnedUnits = 0;
            List<SaleItemReportLine> itemLines = new ArrayList<>();
            if (sale.getItems().isEmpty()) throw invalidSale(sale, "missing SaleItems");
            for (SaleItem item : sale.getItems()) {
                if (item.getProduct() == null || item.getProduct().getId() == null || item.getQuantity() == null || item.getQuantity() < 1)
                    throw invalidSale(sale, "missing product or invalid quantity");
                long count = returned.getOrDefault(item.getId(), 0L);
                if (count > item.getQuantity()) throw invalidSale(sale, "returned quantity exceeds original quantity");
                originalUnits += item.getQuantity();
                returnedUnits += count;
                long net = item.getQuantity() - count;
                Long productId = item.getProduct().getId();
                itemLines.add(new SaleItemReportLine(item.getId(), productId, item.getProductNameSnapshot(),
                        item.getBarcodeSnapshot(), item.getSizeSnapshot(), item.getUnitSnapshot(),
                        item.getQuantity(), count, net, item.getUnitPrice(), item.getNetTotal()));
                ProductReportLine prior = products.get(productId);
                products.put(productId, new ProductReportLine(productId,
                        prior == null ? item.getProductNameSnapshot() : prior.name(), net + (prior == null ? 0 : prior.netUnits())));
            }
            SaleStatus status = returnedUnits == 0 ? SaleStatus.COMPLETED
                    : returnedUnits == originalUnits ? SaleStatus.REFUNDED : SaleStatus.PARTIALLY_REFUNDED;
            lines.add(new SaleReportLine(sale.getId(), sale.getInvoiceNo(), sale.getTimestamp(),
                    sale.getCustomer() == null ? "Walk-in" : sale.getCustomer().getName(), sale.getPaymentMethod(),
                    charged, refund, charged.subtract(refund), status, originalUnits, returnedUnits,
                    originalUnits - returnedUnits, applicable, itemLines));
            refundCount += applicable.size();
            refunded = refunded.add(refund);
        }
        for (RefundReportLine event : processed) refunds.merge(event.paymentMethod(), event.amount(), BigDecimal::add);
        List<PaymentSummaryRow> payments = new ArrayList<>();
        for (PaymentMethod method : PaymentMethod.values()) {
            BigDecimal received = receipts.getOrDefault(method, ZERO), returned = refunds.getOrDefault(method, ZERO);
            payments.add(new PaymentSummaryRow(method.name(), received, returned, received.subtract(returned)));
        }
        BigDecimal allReceipts = payments.stream().map(PaymentSummaryRow::receipts).reduce(ZERO, BigDecimal::add);
        BigDecimal allRefunds = payments.stream().map(PaymentSummaryRow::refunds).reduce(ZERO, BigDecimal::add);
        ProductReportLine best = products.values().stream().filter(p -> p.netUnits() > 0)
                .sorted(Comparator.comparingLong(ProductReportLine::netUnits).reversed().thenComparing(ProductReportLine::productId))
                .findFirst().orElse(null);
        Set<Long> selectedIds = new HashSet<>();
        selected.forEach(s -> selectedIds.add(s.getId()));
        boolean legacy = beforeCutoff.stream().filter(r -> selectedIds.contains(r.saleId()) || processed.contains(r))
                .flatMap(r -> r.items().stream()).anyMatch(i -> i.regularUnits() == null || i.discountedUnits() == null);
        return new CashierReportSnapshot(UUID.randomUUID(), actorId, cashierUsername, cashierName, period,
                new SalesSummary(selected.size(), amount, selected.isEmpty() ? null : amount.divide(BigDecimal.valueOf(selected.size()), 2, RoundingMode.HALF_UP),
                        discount, refundCount, refunded, amount.subtract(refunded), best), payments,
                new PaymentSummaryRow("Total", allReceipts, allRefunds, allReceipts.subtract(allRefunds)),
                receipts.getOrDefault(PaymentMethod.CASH, ZERO).subtract(refunds.getOrDefault(PaymentMethod.CASH, ZERO)), lines, processed, legacy);
    }
    private static BigDecimal requiredMoney(BigDecimal value, Long saleId) {
        if (value == null || value.signum() < 0) throw new ReportDataException("Cannot generate preview: invalid amount on Sale " + saleId + ".");
        try { return value.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw new ReportDataException("Cannot generate preview: invalid money precision on Sale " + saleId + "."); }
    }
    private static ReportDataException invalidSale(Sale sale, String reason) {
        return new ReportDataException("Cannot generate preview: Sale " + sale.getId() + ": " + reason + ".");
    }
}
