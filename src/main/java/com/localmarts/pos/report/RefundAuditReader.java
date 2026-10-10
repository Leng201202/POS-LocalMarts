package com.localmarts.pos.report;

import com.localmarts.pos.audit.entity.Audit;
import com.localmarts.pos.audit.entity.AuditDetails;
import com.localmarts.pos.sale.entity.Sale;
import com.localmarts.pos.sale.entity.SaleItem;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import static com.localmarts.pos.report.CashierReportSnapshot.*;

/** Reads the structured Sale UPDATE refund convention, never descriptions or inventory reasons. */
@Component
public class RefundAuditReader {
    public RefundReportLine read(Audit audit, Sale sale) {
        try {
            require(audit.getId() != null, "missing event ID");
            require(sale != null && Objects.equals(audit.getRowId(), sale.getId()), "missing linked Sale");
            require(audit.getActor() != null, "missing processing actor");
            require(audit.getTimestamp() != null, "missing timestamp");
            require(sale.getPaymentMethod() != null, "missing payment method");
            require(sale.getTimestamp() != null && !audit.getTimestamp().isBefore(sale.getTimestamp()),
                    "refund timestamp precedes Sale");
            Map<String, AuditDetails> fields = new HashMap<>();
            for (AuditDetails detail : audit.getDetails()) {
                require(detail.getFieldName() != null && fields.putIfAbsent(detail.getFieldName(), detail) == null,
                        "duplicate or unnamed structured field");
            }
            BigDecimal total = money(value(fields, "refundTotal"));
            require(sale.getTotalAmount() != null && total.compareTo(sale.getTotalAmount()) <= 0,
                    "refund exceeds or lacks original charged amount");
            Map<Long, SaleItem> saleItems = new HashMap<>();
            for (SaleItem item : sale.getItems()) saleItems.put(item.getId(), item);
            Set<String> prefixes = new TreeSet<>();
            for (String key : fields.keySet()) {
                if (key.startsWith("item.")) {
                    int end = key.indexOf('.', 5);
                    require(end > 5, "invalid item field");
                    prefixes.add(key.substring(0, end + 1));
                }
            }
            require(!prefixes.isEmpty(), "missing refunded items");
            List<RefundItemLine> lines = new ArrayList<>();
            BigDecimal sum = BigDecimal.ZERO;
            Set<Long> seen = new HashSet<>();
            for (String prefix : prefixes) {
                Long id = Long.valueOf(value(fields, prefix + "saleItemId"));
                require(prefix.equals("item." + id + ".") && seen.add(id), "inconsistent SaleItem ID");
                SaleItem item = saleItems.get(id);
                require(item != null, "SaleItem does not belong to linked Sale");
                int quantity = Integer.parseInt(value(fields, prefix + "refundQuantity"));
                require(quantity > 0 && item.getQuantity() != null && quantity <= item.getQuantity(), "invalid returned quantity");
                // The existing workflow permits one refund per sale and writes old=0, new=event quantity.
                // Fail clearly if that convention changes; do not silently interpret cumulative quantities.
                String old = fields.get(prefix + "refundQuantity").getOldData();
                require(old == null || Integer.parseInt(old) == 0, "unsupported cumulative refund quantity");
                BigDecimal amount = money(value(fields, prefix + "lineRefundAmount"));
                require(item.getNetTotal() != null && amount.compareTo(item.getNetTotal()) <= 0,
                        "line refund exceeds or lacks original line amount");
                Integer regular = optionalInt(fields, prefix + "regularUnitsReturned");
                Integer discounted = optionalInt(fields, prefix + "discountedUnitsReturned");
                if (regular != null && discounted != null) {
                    require((long) regular + discounted == quantity, "returned unit split does not match quantity");
                } else {
                    // Legacy events retain reliable financial values, but no split is inferred.
                    regular = null;
                    discounted = null;
                }
                lines.add(new RefundItemLine(id, item.getProductNameSnapshot(), quantity, amount, regular, discounted));
                sum = sum.add(amount);
            }
            require(sum.compareTo(total) == 0, "line refund amounts do not reconcile with refundTotal");
            return new RefundReportLine(audit.getId(), sale.getId(), audit.getActor(), audit.getTimestamp(),
                    sale.getInvoiceNo(), sale.getPaymentMethod(), total, lines);
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw new ReportDataException("Cannot generate preview: refund Audit " + audit.getId() + ": " + ex.getMessage() + ".");
        }
    }

    private static String value(Map<String, AuditDetails> fields, String key) {
        AuditDetails detail = fields.get(key);
        require(detail != null && detail.getNewData() != null && !detail.getNewData().isBlank(), "missing " + key);
        return detail.getNewData();
    }
    private static Integer optionalInt(Map<String, AuditDetails> fields, String key) {
        if (!fields.containsKey(key)) return null;
        int result = Integer.parseInt(value(fields, key));
        require(result >= 0, "negative " + key);
        return result;
    }
    private static BigDecimal money(String value) {
        BigDecimal result = new BigDecimal(value).setScale(2, java.math.RoundingMode.UNNECESSARY);
        require(result.signum() >= 0, "negative refund amount");
        return result;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
