package com.localmarts.pos.report.dtos;

import java.math.BigDecimal;

public record ProductProfitDTO(
        String productName,
        String categoryName,
        Long quantitySold,
        BigDecimal cost,
        BigDecimal profit
) {
}
