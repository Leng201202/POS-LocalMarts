package com.localmarts.pos.report.dtos;

import lombok.Builder;

import java.math.BigDecimal;
@Builder
public record ProfitSummaryDTO(
        BigDecimal totalRevenue,
        BigDecimal costOfGoodsSold,
        BigDecimal grossProfit,
        BigDecimal netProfit
) {
}
