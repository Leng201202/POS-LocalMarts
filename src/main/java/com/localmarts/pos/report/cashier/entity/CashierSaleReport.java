package com.localmarts.pos.report.cashier.entity;

import com.localmarts.pos.report.cashier.model.CashierReportSnapshot;
import com.localmarts.pos.report.cashier.model.ReportType;
import com.localmarts.pos.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Immutable
@Table(name = "cashier_sale_reports", uniqueConstraints = @UniqueConstraint(
        name = "uk_cashier_report_period", columnNames = {"cashier_id", "start_date", "end_date"}))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CashierSaleReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "integer")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cashier_id", nullable = false, updatable = false, columnDefinition = "bigint references users(id)")
    private User cashier;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false)
    private ReportType reportType;
    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;
    @Column(nullable = false, updatable = false)
    private LocalDateTime generatedAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime submittedAt;
    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal systemCash;
    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal realWorldCash;
    @Column(nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal cashDifference;
    // SMALLINT is sufficient for snapshot versions. Hibernate 7.4's SQLite DDL
    // builder otherwise mistakes an earlier "integer" version column for the ID's
    // identity type and emits an untyped ID instead of INTEGER PRIMARY KEY.
    @Column(nullable = false, updatable = false, columnDefinition = "smallint")
    private Integer snapshotVersion;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    public CashierSaleReport(User cashier, CashierReportSnapshot preview, LocalDateTime submittedAt,
            BigDecimal realWorldCash, BigDecimal difference, String json) {
        this.cashier = cashier;
        this.reportType = preview.period().type();
        this.startDate = preview.period().startDate(); this.endDate = preview.period().endDate();
        this.generatedAt = preview.period().generatedAt(); this.submittedAt = submittedAt;
        this.systemCash = preview.systemCash(); this.realWorldCash = realWorldCash;
        this.cashDifference = difference; this.snapshotVersion = 1; this.snapshotJson = json;
    }
}
