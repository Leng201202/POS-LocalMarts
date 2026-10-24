package com.localmarts.pos.report.admin.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record CashierSaleDTO(
        Long cashierId,
        String cashierName,
        Long saleCount,
        BigDecimal revenue
) {
}
