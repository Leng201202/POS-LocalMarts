package com.localmarts.pos.report.cashier.service;

import com.localmarts.pos.audit.AuditRepository;
import com.localmarts.pos.report.common.ReportDataException;
import com.localmarts.pos.report.common.ReportTime;
import com.localmarts.pos.report.cashier.dto.ReportPeriodRequest;
import com.localmarts.pos.report.cashier.model.CashierReportSnapshot;
import com.localmarts.pos.report.cashier.model.ReportPeriod;
import com.localmarts.pos.audit.entity.Audit;
import com.localmarts.pos.sale.SaleRepository;
import com.localmarts.pos.sale.entity.Sale;
import com.localmarts.pos.sale.entity.SaleItem;
import com.localmarts.pos.sale.entity.SaleStatus;
import com.localmarts.pos.user.UserRepository;
import com.localmarts.pos.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import static com.localmarts.pos.report.cashier.model.CashierReportSnapshot.*;

@Service
public class CashierSaleReportService {
    private final SaleRepository sales;
    private final AuditRepository audits;
    private final UserRepository users;
    private final RefundAuditReader reader;
    private final CashierSaleReportCalculator calculator;
    private final Clock clock;

    @Autowired
    public CashierSaleReportService(SaleRepository sales, AuditRepository audits, UserRepository users,
            RefundAuditReader reader, CashierSaleReportCalculator calculator) {
        this(sales, audits, users, reader, calculator, Clock.system(ReportTime.ZONE));
    }
    CashierSaleReportService(SaleRepository sales, AuditRepository audits, UserRepository users,
            RefundAuditReader reader, CashierSaleReportCalculator calculator, Clock clock) {
        this.sales = sales; this.audits = audits; this.users = users;
        this.reader = reader; this.calculator = calculator; this.clock = clock;
    }
    public LocalDate today() { return LocalDate.now(clock.withZone(ReportTime.ZONE)); }

    @Transactional(readOnly = true)
    public CashierReportSnapshot preview(Long actorId, ReportPeriodRequest request) {
        ReportPeriod period = ReportPeriod.resolve(request, clock);
        User cashier = users.findById(actorId).orElseThrow(() -> new ReportDataException("Cashier account is unavailable."));
        List<Sale> selected = sales.findCashierReportSales(actorId, period.start(), period.cutoff());
        Map<Long, Sale> linkedSales = new HashMap<>();
        selected.forEach(s -> linkedSales.put(s.getId(), s));
        List<Long> ids = selected.stream().map(Sale::getId).toList();
        // A sentinel avoids an empty IN clause on dialects that do not support it.
        List<Audit> candidates = audits.findCashierReportRefundCandidates(actorId,
                ids.isEmpty() ? List.of(-1L) : ids, period.start(), period.cutoff());
        List<RefundReportLine> events = new ArrayList<>();
        Set<Long> validActors = new HashSet<>();
        validActors.add(actorId);
        for (Audit audit : candidates) {
            Sale linked = audit.getRowId() == null ? null : linkedSales.computeIfAbsent(audit.getRowId(),
                    id -> sales.findRefundDetailsById(id).orElse(null));
            RefundReportLine event = reader.read(audit, linked);
            if (!validActors.contains(event.actorId())) {
                if (!users.existsById(event.actorId())) throw new ReportDataException("Cannot generate preview: refund Audit " + event.auditId() + " has an unavailable processing actor.");
                validActors.add(event.actorId());
            }
            events.add(event);
        }
        // All events for selected sales (including later ones) were fetched. Reconcile the
        // stored returned quantities to detect missing audit history; the calculator only
        // applies events before cutoff and never uses today's quantities for historical totals.
        Map<Long, Long> returned = new HashMap<>();
        for (RefundReportLine event : events)
            for (RefundItemLine item : event.items()) returned.merge(item.saleItemId(), (long) item.quantity(), Long::sum);
        for (Sale sale : selected) {
            long originalUnits = 0, returnedUnits = 0;
            for (SaleItem item : sale.getItems()) {
                if (item.getRefundQuantity() == null || returned.getOrDefault(item.getId(), 0L) != item.getRefundQuantity().longValue())
                    throw new ReportDataException("Cannot generate preview: Sale " + sale.getId() + " has missing or inconsistent structured refund history.");
                if (item.getQuantity() == null || item.getRefundQuantity() < 0 || item.getRefundQuantity() > item.getQuantity())
                    throw new ReportDataException("Cannot generate preview: Sale " + sale.getId() + " has invalid returned quantities.");
                originalUnits += item.getQuantity();
                returnedUnits += item.getRefundQuantity();
            }
            SaleStatus expected = returnedUnits == 0 ? SaleStatus.COMPLETED
                    : returnedUnits == originalUnits ? SaleStatus.REFUNDED : SaleStatus.PARTIALLY_REFUNDED;
            if (sale.getSaleStatus() != expected)
                throw new ReportDataException("Cannot generate preview: Sale " + sale.getId() + " status disagrees with its structured refund history.");
        }
        return calculator.calculate(actorId, cashier.getUsername(), cashier.getFullName(), period, selected, events);
    }
}
