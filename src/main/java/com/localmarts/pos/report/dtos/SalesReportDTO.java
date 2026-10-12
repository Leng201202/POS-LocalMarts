package com.localmarts.pos.report.dtos;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record SalesReportDTO (
        LocalDate startDate,
        LocalDate endDate,
        Long totalSaleCount,
        BigDecimal totalRevenue

){
}
