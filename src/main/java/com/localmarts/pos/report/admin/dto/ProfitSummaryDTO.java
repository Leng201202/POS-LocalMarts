package com.localmarts.pos.report.admin.dto;

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
