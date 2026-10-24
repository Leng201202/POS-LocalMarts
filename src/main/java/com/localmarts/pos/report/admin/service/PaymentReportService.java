package com.localmarts.pos.report.admin.service;

import com.localmarts.pos.report.admin.model.AdminReportPeriod;
import com.localmarts.pos.report.common.ReportTime;
import com.localmarts.pos.report.admin.repository.AdminReportRepository;
import com.localmarts.pos.report.admin.dto.PaymentAnalysisDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class PaymentReportService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final AdminReportRepository repository;
    private final Clock clock;

    @Autowired
    public PaymentReportService(AdminReportRepository repository) {
        this(repository, Clock.system(ReportTime.ZONE));
    }

    PaymentReportService(AdminReportRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PaymentAnalysisDTO> getPaymentAnalysis(LocalDate start, LocalDate end) {
        AdminReportPeriod period = AdminReportPeriod.resolve(start, end, clock);
        List<Object[]> rows = repository.findPaymentSummary(period.start(), period.cutoff());
        BigDecimal total = rows.stream().map(row -> money(row[1])).reduce(ZERO, BigDecimal::add);
        return rows.stream().map(row -> {
            BigDecimal amount = money(row[1]);
            BigDecimal percentage = total.signum() == 0
                    ? ZERO
                    : amount.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
            return PaymentAnalysisDTO.builder()
                    .paymentType(String.valueOf(row[0]))
                    .amount(amount)
                    .percentage(percentage)
                    .build();
        }).toList();
    }

    private static BigDecimal money(Object value) {
        if (value == null) return ZERO;
        if (value instanceof BigDecimal decimal) return decimal.setScale(2, RoundingMode.HALF_UP);
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
