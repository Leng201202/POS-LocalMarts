package com.localmarts.pos.report.admin.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Builder
public record SalesReportDTO (
        LocalDate startDate,
        LocalDate endDate,
        Long totalSaleCount,
        BigDecimal totalRevenue,
        BigDecimal averageSale,
        List<CashierSaleDTO> cashierSales

){
}
