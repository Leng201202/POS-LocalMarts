package com.localmarts.pos.report.services;

import com.localmarts.pos.report.ReportRepository;
import com.localmarts.pos.report.dtos.ProfitSummaryDTO;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ProfitReportService {
    private final ReportRepository repository;

    public ProfitSummaryDTO getProfitSummary(
            LocalDate start,
            LocalDate end
    ){
        Object[] data=repository.findProfitSummary(
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay()
        );

        BigDecimal revenue=(BigDecimal) data[0];
        BigDecimal cost=(BigDecimal) data[1];

        BigDecimal profit=revenue.subtract(cost);

        return ProfitSummaryDTO.builder()
                .totalRevenue(revenue)
                .costOfGoodsSold(cost)
                .grossProfit(profit)
                .netProfit(profit)
                .build();

    }

    public @Nullable Object getProfitTrend(LocalDate start, LocalDate end) {
        return "null";
    }

    public @Nullable Object getProductPerformance(LocalDate start, LocalDate end, Sort sort) {
        return "still dev";
    }
}
