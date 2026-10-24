package com.localmarts.pos.report.cashier.model;

import com.localmarts.pos.report.cashier.entity.CashierSaleReport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Presentation uses only immutable report scalars and decoded snapshot, never the cashier relationship. */
public record SavedReportView(Long id, ReportType reportType, LocalDate startDate, LocalDate endDate,
        LocalDateTime generatedAt, LocalDateTime submittedAt, BigDecimal systemCash,
        BigDecimal realWorldCash, BigDecimal cashDifference, CashierReportSnapshot snapshot) {
    public String status() { return cashDifference.signum() > 0 ? "OVER" : cashDifference.signum() < 0 ? "SHORT" : "BALANCED"; }
    public static SavedReportView from(CashierSaleReport row, CashierReportSnapshot snapshot) {
        return new SavedReportView(row.getId(), row.getReportType(), row.getStartDate(), row.getEndDate(),
                row.getGeneratedAt(), row.getSubmittedAt(), row.getSystemCash(), row.getRealWorldCash(), row.getCashDifference(), snapshot);
    }
}
