package com.localmarts.pos.report.dtos;

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
