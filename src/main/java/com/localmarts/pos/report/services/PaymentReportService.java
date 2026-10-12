package com.localmarts.pos.report.services;


import com.localmarts.pos.report.ReportRepository;
import com.localmarts.pos.report.dtos.PaymentAnalysisDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentReportService {
    private final ReportRepository repository;

    public List<PaymentAnalysisDTO> getPaymentAnalysis(
            LocalDate start,
            LocalDate end
    ){
        List<Object[]> rows=repository.findPaymentSummary(start.atStartOfDay(),end.atTime(23,59,59));

        BigDecimal total=rows.stream()
                .map(r->(BigDecimal) r[1])
                .reduce(BigDecimal.ZERO,BigDecimal::add);

        return rows.stream().map(r->{
            BigDecimal amount=(BigDecimal) r[1];
            return PaymentAnalysisDTO.builder()
                    .paymentType((String) r[0])
                    .amount(amount)
                    .percentage(amount.divide(total,4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                    )
                    .build();

        })
                .toList();
    }
}
