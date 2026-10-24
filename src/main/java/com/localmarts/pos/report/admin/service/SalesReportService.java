package com.localmarts.pos.report.admin.service;

import com.localmarts.pos.report.admin.model.AdminReportPeriod;
import com.localmarts.pos.report.common.ReportTime;
import com.localmarts.pos.report.admin.repository.AdminReportRepository;
import com.localmarts.pos.report.admin.dto.CashierSaleDTO;
import com.localmarts.pos.report.admin.dto.SalesReportDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class SalesReportService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final AdminReportRepository repository;
    private final Clock clock;

    @Autowired
    public SalesReportService(AdminReportRepository repository) {
        this(repository, Clock.system(ReportTime.ZONE));
    }

    SalesReportService(AdminReportRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(ReportTime.ZONE));
    }

    public AdminReportPeriod resolvePeriod(LocalDate start, LocalDate end) {
        return AdminReportPeriod.resolve(start, end, clock);
    }

    @Transactional(readOnly = true)
    public SalesReportDTO getReport(LocalDate start, LocalDate end) {
        AdminReportPeriod period = resolvePeriod(start, end);
        Object[] result = repository.findSalesBetween(period.start(), period.cutoff());
        long saleCount = number(result[0]).longValue();
        BigDecimal revenue = money(result[1]);
        BigDecimal average = saleCount == 0
                ? ZERO
                : revenue.divide(BigDecimal.valueOf(saleCount), 2, RoundingMode.HALF_UP);

        List<CashierSaleDTO> cashiers = repository.findCashierSales(period.start(), period.cutoff()).stream()
                .map(row -> CashierSaleDTO.builder()
                        .cashierId(number(row[0]).longValue())
                        .cashierName(String.valueOf(row[1]))
                        .saleCount(number(row[2]).longValue())
                        .revenue(money(row[3]))
                        .build())
                .toList();

        return SalesReportDTO.builder()
                .startDate(period.startDate())
                .endDate(period.endDate())
                .totalSaleCount(saleCount)
                .totalRevenue(revenue)
                .averageSale(average)
                .cashierSales(cashiers)
                .build();
    }

    private static Number number(Object value) {
        return value instanceof Number number ? number : 0;
    }

    private static BigDecimal money(Object value) {
        if (value == null) return ZERO;
        if (value instanceof BigDecimal decimal) return decimal.setScale(2, RoundingMode.HALF_UP);
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
